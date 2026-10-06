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
            return oidcTenantClaim.trim();
        }
        String sysProp = System.getProperty(SpectorPropertyConstants.AUTH_OIDC_TENANT_CLAIM);
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp.trim();
        }
        return null;
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
            String tenant = extractTenantFromMap(detailsMap);
            if (tenant != null) {
                return tenant;
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
        if (principal instanceof Map<?, ?> principalMap) {
            String tenant = extractTenantFromMap(principalMap);
            if (tenant != null) {
                return tenant;
            }
        }

        if (principal != null && !(principal instanceof String)) {
            for (String mapMethod : new String[] {"getAttributes", "getClaims", "attributes", "claims"}) {
                try {
                    var method = principal.getClass().getMethod(mapMethod);
                    Object res = method.invoke(principal);
                    if (res instanceof Map<?, ?> map) {
                        String tenant = extractTenantFromMap(map);
                        if (tenant != null) {
                            return tenant;
                        }
                    }
                } catch (ReflectiveOperationException ignored) {}
            }

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

        for (String methodName : new String[] {"tenantId", "getTenantId", "tenant_id"}) {
            try {
                var method = auth.getClass().getMethod(methodName);
                Object res = method.invoke(auth);
                String str = extractStringFromValue(res);
                if (str != null) {
                    return str;
                }
            } catch (ReflectiveOperationException ignored) {}
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
        return extractTenantFromMap(jwt.getClaims());
    }

    /**
     * Extracts tenant identifier from any claims or attributes map.
     *
     * @param map the claims or attributes map
     * @return resolved tenant ID, or {@code null} if absent or blank
     */
    public static String extractTenantFromMap(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        String configuredClaim = getOidcTenantClaim();
        if (configuredClaim != null && !configuredClaim.isBlank()) {
            String val = getPathValueAsString(map, configuredClaim.trim());
            if (val != null && !val.isBlank()) {
                return val.trim();
            }
        }
        for (String candidate : new String[] {"tenant_id", "tid", "tenantId", "realm_access.tenant_id"}) {
            String val = getPathValueAsString(map, candidate);
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
        return getPathValueAsString(jwt.getClaims(), claimName.trim());
    }

    /**
     * Resolves a value from a map, supporting direct keys and dot-separated
     * nested paths (e.g. {@code "realm_access.tenant_id"}). Supports numeric values,
     * collections/arrays (returns the first non-blank entry), and nested path navigation.
     *
     * @param map the map to traverse
     * @param path the key or dot-separated nested path
     * @return string representation of the value, or {@code null} if absent or blank
     */
    public static String getPathValueAsString(Map<?, ?> map, String path) {
        if (map == null || map.isEmpty() || path == null || path.isBlank()) {
            return null;
        }
        path = path.trim();

        // 1. Direct match in map
        Object direct = map.get(path);
        if (direct != null) {
            String str = extractStringFromValue(direct);
            if (str != null) {
                return str;
            }
        }

        // 2. Dot-separated path traversal for nested JSON objects and arrays
        if (path.contains(".")) {
            String[] parts = path.split("\\.");
            Object current = map;
            for (String part : parts) {
                if (current instanceof Map<?, ?> m) {
                    current = m.get(part);
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
        if (val instanceof Boolean) {
            return null;
        }
        if (val instanceof java.util.Optional<?> opt) {
            return opt.map(SecurityUtils::extractStringFromValue).orElse(null);
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
            for (String key : new String[] {"id", "tenant_id", "tenantId", "slug", "value", "key"}) {
                Object sub = map.get(key);
                if (sub != null) {
                    String extracted = extractStringFromValue(sub);
                    if (extracted != null && !extracted.isBlank()) {
                        return extracted;
                    }
                }
            }
            if (map.size() == 1) {
                Object singleVal = map.values().iterator().next();
                String extracted = extractStringFromValue(singleVal);
                if (extracted != null && !extracted.isBlank()) {
                    return extracted;
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
