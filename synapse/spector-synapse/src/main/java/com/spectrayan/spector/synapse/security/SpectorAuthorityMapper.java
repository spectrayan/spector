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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.spectrayan.spector.commons.security.SpectorRoles;
import com.spectrayan.spector.commons.security.SpectorScopes;

/**
 * Maps Spector RBAC roles ({@link SpectorRoles}) and OAuth 2.1 scopes ({@link SpectorScopes})
 * to Spring Security {@link GrantedAuthority} collections.
 *
 * <p>Enforces bidirectional case and prefix normalization:
 * <ul>
 *   <li>Roles are granted with {@code ROLE_} prefix in both lowercase, uppercase, hyphenated,
 *       and underscore forms (e.g. {@code ROLE_super-admin}, {@code ROLE_SUPER_ADMIN}).</li>
 *   <li>Each role automatically implies and grants its constituent {@link SpectorScopes}
 *       per {@link SpectorRoles#scopesForRole(String)}.</li>
 *   <li>Scopes are granted with both {@code SCOPE_} prefix and canonical/prefixed variants
 *       (e.g. {@code SCOPE_spector:memory:read} and {@code SCOPE_memory:read}).</li>
 * </ul>
 */
public final class SpectorAuthorityMapper {

    /** Standard Spring Security role prefix. */
    public static final String ROLE_PREFIX = "ROLE_";

    /** Standard Spring Security scope prefix. */
    public static final String SCOPE_PREFIX = "SCOPE_";

    private SpectorAuthorityMapper() {}

    /**
     * Builds the unified set of Spring Security granted authorities for the given roles and scopes.
     *
     * @param roles  the principal's assigned roles (may be {@code null} or empty)
     * @param scopes the principal's directly assigned scopes (may be {@code null} or empty)
     * @return the list of granted authorities
     */
    public static List<GrantedAuthority> toAuthorities(Collection<String> roles, Collection<String> scopes) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        if (roles != null) {
            for (String role : roles) {
                if (role == null || role.isBlank()) {
                    continue;
                }
                String trimmed = role.trim();
                String raw = trimmed;
                if (raw.regionMatches(true, 0, "ROLE_", 0, 5) || raw.regionMatches(true, 0, "ROLE-", 0, 5)) {
                    raw = raw.substring(5).trim();
                }
                if (raw.isBlank()) {
                    continue;
                }

                authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + raw));
                String upper = raw.toUpperCase();
                String lower = raw.toLowerCase();
                if (!upper.equals(raw)) {
                    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + upper));
                }
                if (!lower.equals(raw)) {
                    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + lower));
                }

                // CamelCase / PascalCase normalization (e.g. "SuperAdmin" -> "super-admin")
                String kebabFromCamel = raw.replaceAll("(?<=[a-z0-9])(?=[A-Z])", "-").toLowerCase();
                if (!kebabFromCamel.equals(lower)) {
                    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + kebabFromCamel));
                    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + kebabFromCamel.toUpperCase()));
                    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + kebabFromCamel.replace('-', '_')));
                    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + kebabFromCamel.toUpperCase().replace('-', '_')));
                }

                if (raw.contains("-") || raw.contains("_")) {
                    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + lower.replace('_', '-')));
                    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + upper.replace('-', '_')));
                    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + lower.replace('-', '_')));
                    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + upper.replace('_', '-')));
                }
            }
        }

        if (scopes != null) {
            for (String scope : scopes) {
                if (scope != null && !scope.isBlank()) {
                    String s = scope.trim();
                    String rawScope = s;
                    if (rawScope.regionMatches(true, 0, "SCOPE_", 0, 6) || rawScope.regionMatches(true, 0, "SCOPE-", 0, 6)) {
                        rawScope = rawScope.substring(6).trim();
                    }
                    if (!rawScope.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority(SCOPE_PREFIX + rawScope));
                    }
                }
            }
        }

        return new ArrayList<>(authorities);
    }

    /**
     * Builds authorities for a single role.
     *
     * @param role the role name
     * @return the granted authorities
     */
    public static List<GrantedAuthority> forRole(String role) {
        return toAuthorities(role != null ? List.of(role) : List.of(), List.of());
    }

    /**
     * Builds authorities for a single role along with its implied constituent scopes
     * per {@link SpectorRoles#scopesForRole(String)}.
     *
     * @param role the role name
     * @return the granted authorities covering role variants and implied scopes
     */
    public static List<GrantedAuthority> forRoleWithScopes(String role) {
        if (role == null || role.isBlank()) {
            return List.of();
        }
        String trimmed = role.trim();
        String raw = trimmed;
        if (raw.regionMatches(true, 0, "ROLE_", 0, 5) || raw.regionMatches(true, 0, "ROLE-", 0, 5)) {
            raw = raw.substring(5).trim();
        }
        String kebab = raw.replaceAll("(?<=[a-z0-9])(?=[A-Z])", "-").toLowerCase().replace('_', '-');
        Set<String> impliedScopes = SpectorRoles.scopesForRole(kebab);
        return toAuthorities(List.of(role), impliedScopes);
    }

    /**
     * Builds authorities for a collection of scopes.
     *
     * @param scopes the scopes
     * @return the granted authorities
     */
    public static List<GrantedAuthority> forScopes(Collection<String> scopes) {
        return toAuthorities(List.of(), scopes);
    }
}
