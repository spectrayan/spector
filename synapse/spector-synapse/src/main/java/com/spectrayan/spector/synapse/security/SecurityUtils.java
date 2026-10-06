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
import java.util.List;
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

        String extracted = extractTenantFromAuthentication(auth);
        return (extracted != null && !extracted.isBlank()) ? extracted : DEFAULT_USER_ID;
    }

    /**
     * Extracts tenant identifier from any {@link Authentication} object.
     *
     * @param auth the authentication object
     * @return the resolved tenant ID, or {@code null} if absent or blank
     */
    public static String extractTenantFromAuthentication(Authentication auth) {
        if (auth == null) {
            return null;
        }

        if (auth.getDetails() instanceof ApiKeyAuthenticationDetails apiKeyDetails) {
            if (apiKeyDetails.tenantId() != null && !apiKeyDetails.tenantId().isBlank()) {
                return apiKeyDetails.tenantId().trim();
            }
        }

        if (auth.getDetails() instanceof Map<?, ?> detailsMap) {
            String configuredClaim = getOidcTenantClaim();
            if (configuredClaim != null && !configuredClaim.isBlank()) {
                Object val = detailsMap.get(configuredClaim.trim());
                String str = extractStringFromValue(val);
                if (str != null) {
                    return str;
                }
            }
            for (String key : new String[] {"tenant_id", "tenantId", "tid", "realm_access.tenant_id"}) {
                Object val = detailsMap.get(key);
                String str = extractStringFromValue(val);
                if (str != null) {
                    return str;
                }
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
            for (String methodName : new String[] {"tenantId", "getTenantId", "tenant_id"}) {
                try {
                    var method = principal.getClass().getMethod(methodName);
                    Object res = method.invoke(principal);
                    String str = extractStringFromValue(res);
                    if (str != null) {
                        return str;
                    }
                } catch (ReflectiveOperationException ignored) {}
            }
        }

        return null;
    }

    /**
     * Extracts the tenant identifier from a {@link Jwt} token.
     *
     * <p>Inspects the configured OIDC claim (from {@link #getOidcTenantClaim()}) if present,
     * followed by default claims ({@code tenant_id}, {@code tid}, {@code tenantId},
     * {@code realm_access.tenant_id}). Supports numeric claim values, collections/arrays,
     * and dot-delimited nested JSON path traversal (e.g. {@code realm_access.tenant_id}).</p>
     *
     * @param jwt the JWT token to inspect
     * @return the resolved tenant ID, or {@code null} if no tenant claim is found
     */
    public static String extractTenantFromJwt(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        String configuredClaim = getOidcTenantClaim();
        if (configuredClaim != null && !configuredClaim.isBlank()) {
            String val = getClaimValueAsString(jwt, configuredClaim.trim());
            if (val != null && !val.isBlank()) {
                return val.trim();
            }
        }
        for (String candidate : new String[] {"tenant_id", "tid", "tenantId", "realm_access.tenant_id"}) {
            String val = getClaimValueAsString(jwt, candidate);
            if (val != null && !val.isBlank()) {
                return val.trim();
            }
        }
        return null;
    }

    /**
     * Resolves a claim value from a {@link Jwt}, supporting direct keys and dot-separated
     * nested paths (e.g. {@code "realm_access.tenant_id"}). Supports numeric values,
     * collections/arrays (returns the first non-blank entry), and nested path navigation.
     *
     * @param jwt the JWT token
     * @param claimName the claim name or nested path
     * @return string representation of the claim, or {@code null} if absent or blank
     */
    public static String getClaimValueAsString(Jwt jwt, String claimName) {
        if (jwt == null || claimName == null || claimName.isBlank()) {
            return null;
        }
        claimName = claimName.trim();
        Map<String, Object> claims = jwt.getClaims();
        if (claims == null || claims.isEmpty()) {
            return null;
        }

        // 1. Direct match in claims map
        Object direct = claims.get(claimName);
        if (direct != null) {
            String str = extractStringFromValue(direct);
            if (str != null) {
                return str;
            }
        }

        // 2. Dot-separated path traversal for nested JSON objects and arrays
        if (claimName.contains(".")) {
            String[] parts = claimName.split("\\.");
            Object current = claims;
            for (String part : parts) {
                if (current instanceof Map<?, ?> map) {
                    current = map.get(part);
                } else if (current instanceof List<?> list) {
                    try {
                        int idx = Integer.parseInt(part);
                        if (idx >= 0 && idx < list.size()) {
                            current = list.get(idx);
                        } else {
                            current = null;
                            break;
                        }
                    } catch (NumberFormatException e) {
                        current = null;
                        break;
                    }
                } else {
                    current = null;
                    break;
                }
            }
            if (current != null) {
                return extractStringFromValue(current);
            }
        }

        return null;
    }

    /**
     * Recursively extracts the first non-blank string representation from a claim value,
     * properly unpacking collections, arrays, and id-holding maps.
     */
    static String extractStringFromValue(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof String s) {
            String trimmed = s.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        if (val instanceof Number) {
            return val.toString().trim();
        }
        if (val instanceof java.util.Collection<?> coll) {
            for (Object item : coll) {
                String extracted = extractStringFromValue(item);
                if (extracted != null && !extracted.isBlank()) {
                    return extracted;
                }
            }
            return null;
        }
        if (val.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(val);
            for (int i = 0; i < len; i++) {
                Object item = java.lang.reflect.Array.get(val, i);
                String extracted = extractStringFromValue(item);
                if (extracted != null && !extracted.isBlank()) {
                    return extracted;
                }
            }
            return null;
        }
        if (val instanceof Map<?, ?> map) {
            for (String key : new String[] {"id", "tenant_id", "tenantId", "value", "key"}) {
                Object sub = map.get(key);
                if (sub != null) {
                    String extracted = extractStringFromValue(sub);
                    if (extracted != null && !extracted.isBlank()) {
                        return extracted;
                    }
                }
            }
            return null;
        }
        String str = val.toString().trim();
        return str.isBlank() ? null : str;
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
