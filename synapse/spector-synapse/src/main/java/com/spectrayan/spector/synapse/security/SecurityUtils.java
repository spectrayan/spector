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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.config.SpectorPropertyConstants;

/**
 * Resolves the current principal and its authorities from Spring Security's
 * {@link SecurityContextHolder}.
 *
 * <p>Spector OSS is single-tenant, multi-user: the isolation boundary is the
 * individual authenticated user, identified by a 13-character TSID that is the
 * Spring Security principal name (never the login username). When no
 * non-anonymous {@link Authentication} is bound (auth disabled, or credentials
 * absent on a public path), the current user resolves to the literal
 * {@code "default"}, which maps to the single shared memory.
 *
 * <p>All methods are pure reads of the security context: they never mutate the
 * bound {@link Authentication}, initiate an authentication exchange, or alter
 * the security context state, and are therefore idempotent within an unchanged
 * context.
 */
public final class SecurityUtils {

    /** Literal user id used when the request is anonymous or auth is disabled. */
    private static final String DEFAULT_USER_ID = "default";

    /** Prefix Spring Security applies to scope authorities. */
    private static final String SCOPE_PREFIX = "SCOPE_";

    private SecurityUtils() {}

    /**
     * Resolves the current user id (TSID) from the bound {@link Authentication}.
     *
     * @return the non-anonymous principal name, or {@code "default"} when no
     *         non-anonymous {@link Authentication} is bound or the principal
     *         name is null/empty. Never {@code null}.
     */
    public static String getUserId() {
        Authentication auth = currentAuthentication();
        if (auth == null) {
            return DEFAULT_USER_ID;
        }
        String name = auth.getName();
        if (name == null || name.isEmpty()) {
            return DEFAULT_USER_ID;
        }
        return name;
    }

    /**
     * Returns the scope authorities granted to the current principal, with the
     * {@code SCOPE_} prefix stripped (e.g. {@code memory:read}).
     *
     * @return an immutable, insertion-ordered set of scopes; empty when the
     *         request is anonymous. Never {@code null}.
     */
    public static Set<String> getScopes() {
        Authentication auth = currentAuthentication();
        if (auth == null) {
            return Collections.emptySet();
        }
        Set<String> scopes = new LinkedHashSet<>();
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (authority == null) {
                continue;
            }
            String value = authority.getAuthority();
            if (value != null && value.startsWith(SCOPE_PREFIX)) {
                scopes.add(value.substring(SCOPE_PREFIX.length()));
            }
        }
        return Collections.unmodifiableSet(scopes);
    }

    /**
     * Tests whether the current principal holds the given scope.
     *
     * @param scope the scope name without the {@code SCOPE_} prefix
     *              (e.g. {@code memory:read})
     * @return {@code true} when the scope is present; {@code false} when the
     *         scope is {@code null} or the request is anonymous
     */
    public static boolean hasScope(String scope) {
        if (scope == null) {
            return false;
        }
        return getScopes().contains(scope);
    }

    /**
     * Indicates whether a non-anonymous {@link Authentication} is bound to the
     * current security context.
     *
     * @return {@code true} when a non-null, non-anonymous, authenticated
     *         {@link Authentication} is present; {@code false} otherwise
     */
    public static boolean isAuthenticated() {
        return currentAuthentication() != null;
    }

    private static volatile String oidcTenantClaim;

    /**
     * Configures the OIDC tenant claim name (e.g. from {@code spector.auth.oidc.tenant-claim}).
     *
     * @param claim the custom JWT claim name to inspect for tenant resolution
     */
    public static void setOidcTenantClaim(String claim) {
        oidcTenantClaim = claim;
    }

    /**
     * Returns the configured OIDC tenant claim name, checking property cache or system properties.
     *
     * @return the configured claim name or {@code null}
     */
    public static String getOidcTenantClaim() {
        if (oidcTenantClaim != null && !oidcTenantClaim.isBlank()) {
            return oidcTenantClaim;
        }
        return System.getProperty(SpectorPropertyConstants.AUTH_OIDC_TENANT_CLAIM);
    }

    /**
     * Resolves the current tenant identifier.
     *
     * <p>Checks the following sources in order:
     * <ol>
     *   <li>{@link MemoryScope#tenantId()} when bound to the current thread</li>
     *   <li>{@link Authentication#getDetails()} if holding {@link ApiKeyAuthenticationDetails} or map</li>
     *   <li>JWT claims if the authentication is token-based (configured OIDC claim, {@code tenant_id}, {@code tid}, {@code tenantId})</li>
     *   <li>Principal reflection if the principal object exposes tenant information</li>
     *   <li>Literal {@code "default"} when unauthenticated, auth is disabled, or no tenant is present</li>
     * </ol>
     *
     * @return the active tenant id, never {@code null}
     */
    public static String getTenantId() {
        if (MemoryScope.isTenantActive()) {
            String scoped = MemoryScope.tenantId();
            if (scoped != null && !scoped.isBlank()) {
                return scoped;
            }
        }

        Authentication auth = currentAuthentication();
        if (auth == null) {
            return DEFAULT_USER_ID;
        }

        if (auth.getDetails() instanceof ApiKeyAuthenticationDetails apiKeyDetails) {
            if (apiKeyDetails.tenantId() != null && !apiKeyDetails.tenantId().isBlank()) {
                return apiKeyDetails.tenantId();
            }
        }

        if (auth.getDetails() instanceof Map<?, ?> detailsMap) {
            Object tid = detailsMap.get("tenant_id");
            if (tid == null) tid = detailsMap.get("tenantId");
            if (tid == null) tid = detailsMap.get("tid");
            if (tid != null && !tid.toString().isBlank()) {
                return tid.toString().trim();
            }
        }

        Jwt jwt = null;
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            jwt = jwtAuth.getToken();
        } else if (auth.getPrincipal() instanceof Jwt principalJwt) {
            jwt = principalJwt;
        } else if (auth.getCredentials() instanceof Jwt credJwt) {
            jwt = credJwt;
        }

        if (jwt != null) {
            String tenant = extractTenantFromJwt(jwt);
            if (tenant != null && !tenant.isBlank()) {
                return tenant;
            }
        }

        Object principal = auth.getPrincipal();
        if (principal != null && !(principal instanceof String)) {
            try {
                var method = principal.getClass().getMethod("tenantId");
                Object res = method.invoke(principal);
                if (res != null && !res.toString().isBlank()) {
                    return res.toString().trim();
                }
            } catch (ReflectiveOperationException ignored) {}
            try {
                var method = principal.getClass().getMethod("getTenantId");
                Object res = method.invoke(principal);
                if (res != null && !res.toString().isBlank()) {
                    return res.toString().trim();
                }
            } catch (ReflectiveOperationException ignored) {}
        }

        return DEFAULT_USER_ID;
    }

    private static String extractTenantFromJwt(Jwt jwt) {
        String configuredClaim = getOidcTenantClaim();
        if (configuredClaim != null && !configuredClaim.isBlank()) {
            String val = jwt.getClaimAsString(configuredClaim.trim());
            if (val != null && !val.isBlank()) {
                return val.trim();
            }
        }
        String tid = jwt.getClaimAsString("tenant_id");
        if (tid != null && !tid.isBlank()) {
            return tid.trim();
        }
        tid = jwt.getClaimAsString("tid");
        if (tid != null && !tid.isBlank()) {
            return tid.trim();
        }
        tid = jwt.getClaimAsString("tenantId");
        if (tid != null && !tid.isBlank()) {
            return tid.trim();
        }
        return null;
    }

    /**
     * Reads the current {@link Authentication} and normalizes anonymous states
     * to {@code null}.
     *
     * @return the bound {@link Authentication} when it is non-null, authenticated,
     *         and not an {@link AnonymousAuthenticationToken}; otherwise {@code null}
     */
    private static Authentication currentAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth;
    }
}
