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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link SecurityUtils}.
 *
 * <p>Verifies principal resolution from Spring Security's {@link SecurityContextHolder}: a
 * non-anonymous {@link Authentication} resolves to its principal name (the TSID), while every
 * anonymous state (no authentication, an {@link AnonymousAuthenticationToken}, or a null/empty
 * principal name) resolves to the literal {@code "default"}. Also covers scope extraction (with
 * the {@code SCOPE_} prefix stripped), {@link SecurityUtils#hasScope(String)},
 * {@link SecurityUtils#isAuthenticated()}, idempotent resolution within an unchanged context, and
 * the always-{@code "default"} tenant id.</p>
 *
 * <p>Each test binds authentications directly to the context and {@link #clearContext()} clears it
 * afterwards to keep tests isolated (Requirements 7.1, 7.2, 7.4, 7.5).</p>
 */
class SecurityUtilsTest {

    private static final String TSID = "USER0000000AB";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static Authentication authenticated(String principal, String... authorities) {
        List<GrantedAuthority> granted = AuthorityUtils.createAuthorityList(authorities);
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(principal, "credentials", granted);
        // UsernamePasswordAuthenticationToken with authorities is authenticated by construction.
        return token;
    }

    private static void bind(Authentication auth) {
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // --- getUserId ---------------------------------------------------------

    @Test
    void getUserIdWithoutAuthenticationReturnsDefault() {
        // No authentication bound (Req 7.2).
        assertThat(SecurityUtils.getUserId()).isEqualTo("default");
    }

    @Test
    void getUserIdWithAnonymousTokenReturnsDefault() {
        // AnonymousAuthenticationToken is treated as anonymous (Req 7.2).
        bind(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(SecurityUtils.getUserId()).isEqualTo("default");
    }

    @Test
    void getUserIdWithAuthenticatedPrincipalReturnsPrincipalName() {
        // Non-anonymous authenticated principal → its TSID name (Req 7.1).
        bind(authenticated(TSID, "SCOPE_memory:read"));

        assertThat(SecurityUtils.getUserId()).isEqualTo(TSID);
    }

    @Test
    void getUserIdWithNullPrincipalNameReturnsDefault() {
        // Non-anonymous Authentication whose name is null (Req 7.4).
        bind(authenticated(null, "SCOPE_memory:read"));

        assertThat(SecurityUtils.getUserId()).isEqualTo("default");
    }

    @Test
    void getUserIdWithEmptyPrincipalNameReturnsDefault() {
        // Non-anonymous Authentication whose name is an empty string (Req 7.4).
        bind(authenticated("", "SCOPE_memory:read"));

        assertThat(SecurityUtils.getUserId()).isEqualTo("default");
    }

    // --- getScopes / hasScope ---------------------------------------------

    @Test
    void getScopesStripsScopePrefixAndReturnsScopeNames() {
        bind(authenticated(TSID, "SCOPE_memory:read", "SCOPE_memory:write", "ROLE_USER"));

        assertThat(SecurityUtils.getScopes())
                .containsExactlyInAnyOrder("memory:read", "memory:write");
    }

    @Test
    void getScopesIsEmptyWhenAnonymous() {
        assertThat(SecurityUtils.getScopes()).isEmpty();
    }

    @Test
    void getScopesReturnedSetIsUnmodifiable() {
        bind(authenticated(TSID, "SCOPE_memory:read"));

        var scopes = SecurityUtils.getScopes();
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> scopes.add("memory:write"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void hasScopeReturnsTrueForGrantedScope() {
        bind(authenticated(TSID, "SCOPE_memory:read"));

        assertThat(SecurityUtils.hasScope("memory:read")).isTrue();
    }

    @Test
    void hasScopeReturnsFalseForMissingScope() {
        bind(authenticated(TSID, "SCOPE_memory:read"));

        assertThat(SecurityUtils.hasScope("memory:write")).isFalse();
    }

    @Test
    void hasScopeReturnsFalseForNullScope() {
        bind(authenticated(TSID, "SCOPE_memory:read"));

        assertThat(SecurityUtils.hasScope(null)).isFalse();
    }

    @Test
    void hasScopeReturnsFalseWhenAnonymous() {
        assertThat(SecurityUtils.hasScope("memory:read")).isFalse();
    }

    // --- isAuthenticated ---------------------------------------------------

    @Test
    void isAuthenticatedTrueForNonAnonymousAuthenticated() {
        bind(authenticated(TSID, "SCOPE_memory:read"));

        assertThat(SecurityUtils.isAuthenticated()).isTrue();
    }

    @Test
    void isAuthenticatedFalseWhenNoAuthentication() {
        assertThat(SecurityUtils.isAuthenticated()).isFalse();
    }

    @Test
    void isAuthenticatedFalseForAnonymousToken() {
        bind(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(SecurityUtils.isAuthenticated()).isFalse();
    }

    @Test
    void isAuthenticatedFalseForUnauthenticatedToken() {
        // A token explicitly marked unauthenticated must not count as authenticated.
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(TSID, "credentials");
        token.setAuthenticated(false);
        bind(token);

        assertThat(SecurityUtils.isAuthenticated()).isFalse();
    }

    // --- idempotence -------------------------------------------------------

    @Test
    void resolutionIsIdempotentWithinUnchangedContext() {
        // Two resolutions of an unchanged context return identical values (Req 7.5).
        bind(authenticated(TSID, "SCOPE_memory:read", "SCOPE_memory:write"));

        String firstUserId = SecurityUtils.getUserId();
        var firstScopes = SecurityUtils.getScopes();
        String secondUserId = SecurityUtils.getUserId();
        var secondScopes = SecurityUtils.getScopes();

        assertThat(secondUserId).isEqualTo(firstUserId).isEqualTo(TSID);
        assertThat(secondScopes).isEqualTo(firstScopes);
    }

    @Test
    void resolutionDoesNotMutateBoundAuthentication() {
        // Reading the context must not replace or alter the bound Authentication (Req 7.3/7.5).
        Authentication bound = authenticated(TSID, "SCOPE_memory:read");
        bind(bound);

        SecurityUtils.getUserId();
        SecurityUtils.getScopes();
        SecurityUtils.hasScope("memory:read");
        SecurityUtils.isAuthenticated();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(bound);
    }

    // --- getTenantId -------------------------------------------------------

    @Test
    void getTenantIdIsAlwaysDefaultWhenAnonymous() {
        assertThat(SecurityUtils.getTenantId()).isEqualTo("default");
    }

    @Test
    void getTenantIdIsAlwaysDefaultWhenAuthenticatedWithoutTenant() {
        bind(authenticated(TSID, "SCOPE_memory:read"));

        assertThat(SecurityUtils.getTenantId()).isEqualTo("default");
    }

    @Test
    void getTenantIdResolvesFromMemoryScope() {
        com.spectrayan.spector.commons.concurrent.MemoryScope.runWithScope(
                "tenant-alpha", "session-1", "ns-1", () -> {
                    assertThat(SecurityUtils.getTenantId()).isEqualTo("tenant-alpha");
                });
    }

    @Test
    void getTenantIdResolvesFromApiKeyAuthenticationDetails() {
        var token = new UsernamePasswordAuthenticationToken(TSID, "credentials", List.of());
        token.setDetails(new ApiKeyAuthenticationDetails("key-123", "tenant-bravo"));
        bind(token);

        assertThat(SecurityUtils.getTenantId()).isEqualTo("tenant-bravo");
    }

    @Test
    void getTenantIdResolvesFromJwtDefaultClaims() {
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", TSID)
                .claim("tenant_id", "tenant-jwt-1")
                .build();
        var jwtAuth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt, List.of());
        bind(jwtAuth);

        assertThat(SecurityUtils.getTenantId()).isEqualTo("tenant-jwt-1");
    }

    @Test
    void getTenantIdResolvesFromConfiguredOidcTenantClaim() {
        try {
            SecurityUtils.setOidcTenantClaim("custom_tenant_claim");
            var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("mock-token")
                    .header("alg", "none")
                    .claim("sub", TSID)
                    .claim("custom_tenant_claim", "tenant-custom-oidc")
                    .claim("tenant_id", "fallback-tenant")
                    .build();
            var jwtAuth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt, List.of());
            bind(jwtAuth);

            assertThat(SecurityUtils.getTenantId()).isEqualTo("tenant-custom-oidc");
        } finally {
            SecurityUtils.setOidcTenantClaim(null);
        }
    }

    @Test
    void getTenantIdResolvesFromNumericClaim() {
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", TSID)
                .claim("tenant_id", 1001)
                .build();
        var jwtAuth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt, List.of());
        bind(jwtAuth);

        assertThat(SecurityUtils.getTenantId()).isEqualTo("1001");
    }

    @Test
    void getTenantIdResolvesFromNestedJsonClaim() {
        try {
            SecurityUtils.setOidcTenantClaim("realm_access.tenant_id");
            var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("mock-token")
                    .header("alg", "none")
                    .claim("sub", TSID)
                    .claim("realm_access", java.util.Map.of("tenant_id", "tenant-nested"))
                    .build();
            var jwtAuth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt, List.of());
            bind(jwtAuth);

            assertThat(SecurityUtils.getTenantId()).isEqualTo("tenant-nested");
        } finally {
            SecurityUtils.setOidcTenantClaim(null);
        }
    }

    @Test
    void getTenantIdResolvesFromWhitespacePaddedClaim() {
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", TSID)
                .claim("tenant_id", "   tenant-spaced   ")
                .build();
        var jwtAuth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt, List.of());
        bind(jwtAuth);

        assertThat(SecurityUtils.getTenantId()).isEqualTo("tenant-spaced");
    }

    @Test
    void getTenantIdFallsBackWhenCandidateClaimIsBlank() {
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", TSID)
                .claim("tenant_id", "   ")
                .claim("tid", "tenant-tid-fallback")
                .build();
        var jwtAuth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt, List.of());
        bind(jwtAuth);

        assertThat(SecurityUtils.getTenantId()).isEqualTo("tenant-tid-fallback");
    }

    @Test
    void getTenantIdResolvesFromListClaim() {
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", TSID)
                .claim("tenant_id", List.of("tenant-list-primary", "tenant-list-secondary"))
                .build();
        var jwtAuth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt, List.of());
        bind(jwtAuth);

        assertThat(SecurityUtils.getTenantId()).isEqualTo("tenant-list-primary");
    }

    @Test
    void getTenantIdFallsBackWhenListClaimIsEmptyOrAllBlank() {
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", TSID)
                .claim("tenant_id", List.of("", "   "))
                .claim("tid", "tenant-tid-after-empty-list")
                .build();
        var jwtAuth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt, List.of());
        bind(jwtAuth);

        assertThat(SecurityUtils.getTenantId()).isEqualTo("tenant-tid-after-empty-list");
    }

    @Test
    void getTenantIdResolvesFromArrayClaim() {
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", TSID)
                .claim("tenant_id", new String[] {"tenant-array-1", "tenant-array-2"})
                .build();
        var jwtAuth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt, List.of());
        bind(jwtAuth);

        assertThat(SecurityUtils.getTenantId()).isEqualTo("tenant-array-1");
    }

    @Test
    void getTenantIdResolvesFromNestedListClaim() {
        try {
            SecurityUtils.setOidcTenantClaim("realm_access.tenants");
            var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("mock-token")
                    .header("alg", "none")
                    .claim("sub", TSID)
                    .claim("realm_access", java.util.Map.of("tenants", List.of("tenant-nested-in-list")))
                    .build();
            var jwtAuth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt, List.of());
            bind(jwtAuth);

            assertThat(SecurityUtils.getTenantId()).isEqualTo("tenant-nested-in-list");
        } finally {
            SecurityUtils.setOidcTenantClaim(null);
        }
    }

    @Test
    void getTenantIdResolvesFromLeafMapClaim() {
        try {
            SecurityUtils.setOidcTenantClaim("tenant_map");
            var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("mock-token")
                    .header("alg", "none")
                    .claim("sub", TSID)
                    .claim("tenant_map", java.util.Map.of("id", "tenant-map-extracted"))
                    .build();
            var jwtAuth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt, List.of());
            bind(jwtAuth);

            assertThat(SecurityUtils.getTenantId()).isEqualTo("tenant-map-extracted");
        } finally {
            SecurityUtils.setOidcTenantClaim(null);
        }
    }

    @Test
    void getTenantIdResolvesFromIndexedPathClaim() {
        try {
            SecurityUtils.setOidcTenantClaim("organizations.0.tenant_id");
            var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("mock-token")
                    .header("alg", "none")
                    .claim("sub", TSID)
                    .claim("organizations", List.of(java.util.Map.of("tenant_id", "org-tenant-indexed")))
                    .build();
            var jwtAuth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt, List.of());
            bind(jwtAuth);

            assertThat(SecurityUtils.getTenantId()).isEqualTo("org-tenant-indexed");
        } finally {
            SecurityUtils.setOidcTenantClaim(null);
        }
    }

    @Test
    void getTenantIdResolvesFromDetailsMapWithList() {
        var token = new UsernamePasswordAuthenticationToken(TSID, "credentials", List.of());
        token.setDetails(java.util.Map.of("tenant_id", List.of("tenant-details-list")));
        bind(token);

        assertThat(SecurityUtils.getTenantId()).isEqualTo("tenant-details-list");
    }

    @Test
    void getTenantIdResolvesFromPrincipalMethodReflection() {
        record CustomPrincipal(String name, String tenantId) {}
        var principal = new CustomPrincipal("custom-user", "tenant-principal-reflection");
        var token = new UsernamePasswordAuthenticationToken(principal, "credentials", List.of());
        bind(token);

        assertThat(SecurityUtils.getTenantId()).isEqualTo("tenant-principal-reflection");
    }

    @Test
    void getTenantIdResolvesFromSystemPropertyOidcClaim() {
        String propKey = com.spectrayan.spector.config.SpectorPropertyConstants.AUTH_OIDC_TENANT_CLAIM;
        String oldVal = System.getProperty(propKey);
        try {
            SecurityUtils.setOidcTenantClaim(null);
            System.setProperty(propKey, "sys_prop_tenant_claim");
            assertThat(SecurityUtils.getOidcTenantClaim()).isEqualTo("sys_prop_tenant_claim");

            var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("mock-token")
                    .header("alg", "none")
                    .claim("sub", TSID)
                    .claim("sys_prop_tenant_claim", "tenant-from-sysprop")
                    .build();
            var jwtAuth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt, List.of());
            bind(jwtAuth);

            assertThat(SecurityUtils.getTenantId()).isEqualTo("tenant-from-sysprop");
        } finally {
            if (oldVal != null) {
                System.setProperty(propKey, oldVal);
            } else {
                System.clearProperty(propKey);
            }
            SecurityUtils.setOidcTenantClaim(null);
        }
    }

    @Test
    void getTenantIdResolvesFromDetailsNestedDotPath() {
        var token = new UsernamePasswordAuthenticationToken(TSID, "credentials", List.of());
        token.setDetails(Map.of("realm_access", Map.of("tenant_id", "nested-details-tenant")));
        bind(token);

        assertThat(SecurityUtils.getTenantId()).isEqualTo("nested-details-tenant");
    }

    @Test
    void getTenantIdResolvesFromPrincipalAttributesMap() {
        record MockOAuth2Principal(String name, Map<String, Object> attributes) {
            public Map<String, Object> getAttributes() {
                return attributes;
            }
        }
        var principal = new MockOAuth2Principal("oauth2-user", Map.of("tenant_id", "oauth2-attr-tenant"));
        var token = new UsernamePasswordAuthenticationToken(principal, "credentials", List.of());
        bind(token);

        assertThat(SecurityUtils.getTenantId()).isEqualTo("oauth2-attr-tenant");
    }

    @Test
    void getTenantIdResolvesFromPrincipalClaimsMap() {
        record MockOidcPrincipal(String name, Map<String, Object> claims) {
            public Map<String, Object> getClaims() {
                return claims;
            }
        }
        var principal = new MockOidcPrincipal("oidc-user", Map.of("tid", "oidc-claim-tenant"));
        var token = new UsernamePasswordAuthenticationToken(principal, "credentials", List.of());
        bind(token);

        assertThat(SecurityUtils.getTenantId()).isEqualTo("oidc-claim-tenant");
    }

    @Test
    void getTenantIdResolvesFromPrincipalMap() {
        Map<String, Object> principalMap = Map.of("tenant_id", "map-principal-tenant");
        var token = new UsernamePasswordAuthenticationToken(principalMap, "credentials", List.of());
        bind(token);

        assertThat(SecurityUtils.getTenantId()).isEqualTo("map-principal-tenant");
    }

    @Test
    void getTenantIdResolvesFromSingleEntryMapLeaf() {
        try {
            SecurityUtils.setOidcTenantClaim("tenant_object");
            var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("mock-token")
                    .header("alg", "none")
                    .claim("sub", TSID)
                    .claim("tenant_object", Map.of("custom_key", "single-entry-tenant"))
                    .build();
            var jwtAuth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt, List.of());
            bind(jwtAuth);

            assertThat(SecurityUtils.getTenantId()).isEqualTo("single-entry-tenant");
        } finally {
            SecurityUtils.setOidcTenantClaim(null);
        }
    }

    @Test
    void getTenantIdResolvesFromAuthenticationReflection() {
        class CustomAuthToken extends UsernamePasswordAuthenticationToken {
            private final String tenant;
            CustomAuthToken(String tenant) {
                super(TSID, "credentials", List.of());
                this.tenant = tenant;
            }
            public String getTenantId() {
                return tenant;
            }
        }
        bind(new CustomAuthToken("auth-reflection-tenant"));

        assertThat(SecurityUtils.getTenantId()).isEqualTo("auth-reflection-tenant");
    }

    @Test
    void getTenantIdIgnoresBooleanValuesInClaims() {
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", TSID)
                .claim("tenant_id", Boolean.TRUE)
                .claim("tid", "fallback-after-bool")
                .build();
        var jwtAuth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt, List.of());
        bind(jwtAuth);

        assertThat(SecurityUtils.getTenantId()).isEqualTo("fallback-after-bool");
    }

    @Test
    void unknownAuthorityShapesAreIgnoredByScopeExtraction() {
        // Non-SCOPE_ authorities (roles, bare strings) never leak into getScopes().
        bind(authenticated(TSID, "ROLE_ADMIN", "memory:read", "SCOPE_memory:write"));

        assertThat(SecurityUtils.getScopes()).containsExactly("memory:write");
    }

    @Test
    void simpleGrantedAuthorityScopeIsExtracted() {
        // Sanity check that a directly-constructed SCOPE_ authority is handled.
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                TSID, "credentials", List.of(new SimpleGrantedAuthority("SCOPE_agent:invoke")));
        bind(token);

        assertThat(SecurityUtils.getScopes()).containsExactly("agent:invoke");
    }
}
