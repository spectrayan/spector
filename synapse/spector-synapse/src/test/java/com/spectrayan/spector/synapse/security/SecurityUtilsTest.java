/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.security;

import java.util.List;

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
    void getTenantIdIsAlwaysDefaultWhenAuthenticated() {
        bind(authenticated(TSID, "SCOPE_memory:read"));

        assertThat(SecurityUtils.getTenantId()).isEqualTo("default");
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
