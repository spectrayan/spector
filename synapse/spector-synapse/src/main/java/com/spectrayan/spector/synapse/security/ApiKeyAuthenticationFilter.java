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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.connector.model.CredentialRecord;
import com.spectrayan.spector.synapse.connector.repository.CredentialRepository;
import com.spectrayan.spector.config.properties.AuthProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * API key authentication filter backed by the universal credentials vault.
 *
 * <p>Accepts keys via two mechanisms, preferring the {@code Authorization} header over
 * {@code X-API-Key} when both are present:</p>
 * <ul>
 *   <li>{@code Authorization: Bearer <api-key>}</li>
 *   <li>{@code X-API-Key: <api-key>}</li>
 * </ul>
 *
 * <p>Only requests whose path starts with {@code /api/} or {@code /mcp} are inspected; all other
 * paths pass straight through. The filter always continues the chain regardless of whether an
 * {@code Authentication} was bound.</p>
 *
 * <p>Behavior depends on {@code spector.auth.enabled}:</p>
 * <ul>
 *   <li><strong>disabled</strong> (legacy, backward-compatible): if the extracted key equals the
 *       configured shared key ({@code spector.api-key}), bind an {@code Authentication} carrying
 *       {@code ROLE_API}; otherwise leave the context unauthenticated.</li>
 *   <li><strong>enabled</strong>: compute {@code SHA-256} of the raw key and look up a non-expired
 *       credential via {@link CredentialRepository#findByKeyHash(String)}. On a match, bind an
 *       {@code Authentication} whose principal is the owning {@code userId} and whose authorities
 *       are mapped to {@code SCOPE_*} and {@code ROLE_*}; otherwise leave the context
 *       unauthenticated (downstream authorization yields 401/403).</li>
 * </ul>
 *
 * <p>Raw key values are never logged, including on validation-failure and exception paths.</p>
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String API_KEY_HEADER = "X-API-Key";

    /** Maximum accepted length of a presented API key value (inclusive). */
    private static final int MAX_KEY_LENGTH = 512;

    private final SynapseProperties props;
    private final AuthProperties auth;
    private final CredentialRepository credentialRepository;
    private final UserAccountStore userAccountStore;
    private final ConcurrentHashMap<String, Long> lastUsedThrottle = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public ApiKeyAuthenticationFilter(
            SynapseProperties props,
            CredentialRepository credentialRepository,
            org.springframework.beans.factory.ObjectProvider<UserAccountStore> userAccountStoreProvider) {
        this.props = props;
        this.auth = props.auth();
        this.credentialRepository = credentialRepository;
        this.userAccountStore = userAccountStoreProvider != null ? userAccountStoreProvider.getIfAvailable() : null;
    }

    public ApiKeyAuthenticationFilter(SynapseProperties props, CredentialRepository credentialRepository) {
        this(props, credentialRepository, (UserAccountStore) null);
    }

    public ApiKeyAuthenticationFilter(SynapseProperties props, CredentialRepository credentialRepository, UserAccountStore userAccountStore) {
        this.props = props;
        this.auth = props.auth();
        this.credentialRepository = credentialRepository;
        this.userAccountStore = userAccountStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip non-API and non-MCP paths (unchanged skip logic).
        if (!path.startsWith("/api/") && !path.startsWith("/mcp")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String apiKey = extractApiKey(request);
            if (auth != null && auth.enabled()) {
                // codeql[java/user-controlled-bypass]
                authenticatePerUserKey(apiKey, path);
            } else {
                // codeql[java/user-controlled-bypass]
                authenticateLegacySharedKey(apiKey, path);
            }
        } catch (RuntimeException e) {
            // Never log the raw key; leave the context unauthenticated and continue the chain.
            log.warn("[Auth] API key authentication failed for {}", path, e);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateLegacySharedKey(String apiKey, String path) {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        byte[] a = apiKey.getBytes(StandardCharsets.UTF_8);
        byte[] b = props.apiKey() != null ? props.apiKey().getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (java.security.MessageDigest.isEqual(a, b)) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    "api-client", null,
                    List.of(new SimpleGrantedAuthority("ROLE_API")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("[Auth] Legacy shared-key authenticated for {}", path);
        }
    }

    private void authenticatePerUserKey(String apiKey, String path) {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        String hash = sha256Hex(apiKey);
        Optional<CredentialRecord> match = credentialRepository.findByKeyHash(hash);
        if (match.isPresent()) {
            CredentialRecord row = match.get();

            // Expiration check
            if (row.expiresAt() != null && row.expiresAt().isBefore(Instant.now())) {
                log.debug("[Auth] Presented API key credential '{}' expired at {}", row.name(), row.expiresAt());
                return;
            }

            String tenantId = row.tenantId();
            String userId = row.userId();
            Set<String> roles = new java.util.LinkedHashSet<>();
            Set<String> scopes = new java.util.LinkedHashSet<>();

            // Extract scopes from properties map
            if (row.properties() != null && row.properties().containsKey("scopes")) {
                Object scopesObj = row.properties().get("scopes");
                if (scopesObj instanceof java.util.Collection<?> col) {
                    for (Object o : col) {
                        if (o != null) scopes.add(o.toString());
                    }
                } else if (scopesObj instanceof String s) {
                    for (String part : s.split("[,\\s]+")) {
                        if (!part.isBlank()) scopes.add(part.trim());
                    }
                }
            }

            if (userAccountStore != null && userId != null) {
                Optional<UserRow> userOpt = userAccountStore.findByUserId(userId);
                if (userOpt.isPresent()) {
                    UserRow user = userOpt.get();
                    tenantId = user.tenantId();
                    if (user.roles() != null) {
                        roles.addAll(user.roles());
                    }
                }
            }

            for (String s : scopes) {
                if (s != null) {
                    String trimmed = s.trim();
                    String norm = trimmed.replaceAll("(?<=[a-z0-9])(?=[A-Z])", "-").toLowerCase().replace('_', '-');
                    while (norm.startsWith("role-") || norm.startsWith("scope-")) {
                        if (norm.startsWith("role-")) {
                            norm = norm.substring(5).trim();
                        } else {
                            norm = norm.substring(6).trim();
                        }
                    }
                    if (norm.equals("spector:admin") || norm.equals("super-admin") || norm.equals("spector:super-admin")) {
                        roles.add(com.spectrayan.spector.commons.security.SpectorRoles.SUPER_ADMIN);
                        continue;
                    }
                    if (norm.startsWith("spector:")) {
                        norm = norm.substring("spector:".length());
                    }
                    if (!com.spectrayan.spector.commons.security.SpectorRoles.scopesForRole(norm).isEmpty()) {
                        roles.add(norm);
                    }
                }
            }

            List<GrantedAuthority> authorities = SpectorAuthorityMapper.toAuthorities(roles, scopes);
            var authentication = new UsernamePasswordAuthenticationToken(
                    userId != null ? userId : row.credentialId(), null, authorities);
            authentication.setDetails(new ApiKeyAuthenticationDetails(row.credentialId(), tenantId));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            recordLastUsedThrottled(row.credentialId());
            log.debug("[Auth] Credential {} authenticated user {} (tenant={}) for {}",
                    row.credentialId(), userId, tenantId, path);
        }
    }

    private void recordLastUsedThrottled(String credentialId) {
        if (credentialId == null) return;
        long now = System.currentTimeMillis();
        Long last = lastUsedThrottle.put(credentialId, now);
        if (last == null || (now - last) >= 60_000L) {
            try {
                credentialRepository.updateLastUsedAt(credentialId, Instant.ofEpochMilli(now));
            } catch (Exception e) {
                log.debug("[Auth] Failed to update last_used_at for credential {}", credentialId, e);
            }
        }
    }

    static String sha256Hex(String raw) {
        if (raw == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Extracts the presented API key, preferring {@code Authorization: Bearer <key>} over
     * {@code X-API-Key}. Only values whose length is between 1 and {@value #MAX_KEY_LENGTH}
     * characters (inclusive) are accepted; anything else yields {@code null}.
     */
    private String extractApiKey(HttpServletRequest request) {
        // Prefer Authorization: Bearer <key>.
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String candidate = authHeader.substring(BEARER_PREFIX.length()).trim();
            return acceptable(candidate) ? candidate : null;
        }

        // Fall back to X-API-Key: <key>.
        String xApiKey = request.getHeader(API_KEY_HEADER);
        if (xApiKey != null) {
            String candidate = xApiKey.trim();
            return acceptable(candidate) ? candidate : null;
        }

        return null;
    }

    /**
     * Accepts only key values with length in the range 1..{@value #MAX_KEY_LENGTH}.
     */
    private static boolean acceptable(String candidate) {
        return !candidate.isEmpty() && candidate.length() <= MAX_KEY_LENGTH;
    }
}
