/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.synapse.security;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.synapse.admin.tenant.TenantService;
import com.spectrayan.spector.synapse.catalog.exception.TenantSuspendedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Filter that enforces tenant lifecycle status ({@code ACTIVE} vs {@code SUSPENDED}/{@code DELETED})
 * for authenticated requests.
 *
 * <p>If an authenticated caller belongs to a suspended or soft-deleted tenant, the request is
 * rejected with HTTP 403 Forbidden and unified error code {@code SPE-820-003} ({@code SPE-SEC-003}),
 * unless the caller is a platform operator ({@code super-admin}) accessing the tenant management API.</p>
 */
@Component
public class TenantStatusFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantStatusFilter.class);

    private final TenantService tenantService;
    private final UserAccountStore userAccountStore;

    @org.springframework.beans.factory.annotation.Autowired
    public TenantStatusFilter(
            ObjectProvider<TenantService> tenantServiceProvider,
            ObjectProvider<UserAccountStore> userAccountStoreProvider) {
        this.tenantService = tenantServiceProvider != null ? tenantServiceProvider.getIfAvailable() : null;
        this.userAccountStore = userAccountStoreProvider != null ? userAccountStoreProvider.getIfAvailable() : null;
    }

    public TenantStatusFilter(TenantService tenantService, UserAccountStore userAccountStore) {
        this.tenantService = tenantService;
        this.userAccountStore = userAccountStore;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }
        return !path.startsWith("/api/") && !path.startsWith("/mcp");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (tenantService == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (SecurityUtils.isSuperAdmin() && path != null && path.startsWith("/api/v1/admin/tenants")) {
            filterChain.doFilter(request, response);
            return;
        }

        String effectiveTenantId = resolveEffectiveTenantId(auth);
        if (effectiveTenantId != null && tenantService.isTenantSuspended(effectiveTenantId)) {
            writeTenantSuspended(response, effectiveTenantId);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveEffectiveTenantId(Authentication auth) {
        String tenantId = SecurityUtils.getTenantId();
        if ((tenantId == null || "default".equals(tenantId)) && userAccountStore != null && auth.getName() != null) {
            String accountTenant = userAccountStore.findByUserId(auth.getName())
                    .map(UserRow::tenantId)
                    .orElse(null);
            if (accountTenant != null && !accountTenant.isBlank()) {
                return accountTenant;
            }
        }
        return tenantId;
    }

    private void writeTenantSuspended(HttpServletResponse response, String tenantId) throws IOException {
        log.warn("[TenantStatusFilter] Rejecting request for suspended/deleted tenant '{}'", tenantId);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String errorCodeId = ErrorCode.TENANT_SUSPENDED.id();
        String alias = TenantSuspendedException.ERROR_CODE_ALIAS;
        String msg = "Tenant is suspended or deactivated";
        String isoTimestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        String json = String.format(
                "{\"status\":403,\"error\":\"%s\",\"code\":\"%s\",\"alias\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\",\"details\":{\"alias\":\"%s\",\"code\":\"%s\",\"tenantId\":\"%s\"}}",
                errorCodeId, errorCodeId, alias, msg, isoTimestamp, alias, errorCodeId, tenantId
        );
        response.getWriter().write(json);
    }
}
