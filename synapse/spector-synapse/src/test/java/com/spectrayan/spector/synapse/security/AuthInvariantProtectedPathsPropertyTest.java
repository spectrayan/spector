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

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import org.spectrayan.testfixtures.synapse.auth.AuthInvariantProbeController;
import org.spectrayan.testfixtures.synapse.auth.AuthInvariantWebSecurityConfig;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.lifecycle.BeforeContainer;

/**
 * Property-based tests (jqwik) for <b>Property 4: Auth invariant on protected paths</b> of the
 * multi-user auth design.
 *
 * <p>The invariant under test: FOR ANY request on a non-public path with auth enabled, a
 * non-anonymous {@code Authentication} is observed downstream <em>iff</em> the credentials are valid
 * AND satisfy the route authorization; otherwise the response is {@code 401}/{@code 403} and the
 * downstream handler is <b>not</b> invoked. A public path is reachable regardless of credentials.
 * Concretely, across the full generated space:</p>
 * <ul>
 *   <li>protected path + (no / invalid credentials) &rarr; {@code 401} and the handler is not
 *       reached (Requirements 6.1, 6.3);</li>
 *   <li>protected path + valid credentials lacking the required authority &rarr; {@code 403} and the
 *       handler is not reached (Requirement 6.4);</li>
 *   <li>protected path + valid credentials with the required authority &rarr; {@code 200} and the
 *       handler is reached with a non-anonymous {@code Authentication} downstream (Requirement
 *       6.1);</li>
 *   <li>public path &rarr; reachable regardless of the credential state (Requirement 6.2 context).</li>
 * </ul>
 *
 * <h3>Approach</h3>
 * <p>A single Spring Security filter chain is built once in {@link #setUp()} via a minimal
 * {@link AnnotationConfigWebApplicationContext}, and every jqwik try runs through the same shared
 * {@link MockMvc} instance (per the design note, a MockMvc-per-try would be prohibitively slow). The
 * fixture {@link AuthInvariantWebSecurityConfig} mirrors
 * {@link com.spectrayan.spector.synapse.config.SecurityConfig} exactly where it matters:
 * {@code spector.auth.public-paths} are {@code permitAll}, {@code /api/**} requires a non-anonymous
 * {@code Authentication}, and the <em>real</em> {@link FailClosedAuthenticationEntryPoint} /
 * {@link FailClosedAccessDeniedHandler} translate failures into the uniform {@code 401}/{@code 403}
 * responses. Because scope/role gating in the production code is expressed with method security
 * ({@code @PreAuthorize}) rather than in {@code authorizeHttpRequests}, a representative scope-gated
 * route ({@code /api/scoped} requiring {@code SCOPE_memory:write}) is added to exercise the
 * {@code 403} branch through the same authorization pipeline.</p>
 *
 * <p>The fixture config and probe controller deliberately live in the non-scanned
 * {@code com.spectrayan.testfixtures.synapse.auth} package so that {@code @SpringBootApplication}
 * component scanning in unrelated {@code @SpringBootTest} contexts never picks them up.</p>
 *
 * <h3>Limitations</h3>
 * <p>The four credential states are simulated at the authorization layer using
 * spring-security-test's {@code authentication(...)} post-processor rather than by minting real
 * tokens/API keys: {@code NONE} and {@code INVALID} both bind no {@code Authentication} (an invalid
 * credential yields no downstream principal, which is exactly the state the authorization layer
 * observes), while the two {@code VALID_*} states bind a non-anonymous {@code Authentication}
 * carrying the relevant authorities. Token/API-key <em>validation</em> itself is covered by the
 * decoder and filter unit tests; this property isolates the authorization invariant.</p>
 *
 * <p><b>Validates: Requirements 6.1, 6.3, 6.4</b></p>
 */
class AuthInvariantProtectedPathsPropertyTest {

    /** Authority required by the representative scope-gated route. */
    private static final String REQUIRED_SCOPE = "SCOPE_memory:write";

    /** Authority a valid-but-insufficient principal carries (does not satisfy the scoped route). */
    private static final String OTHER_SCOPE = "SCOPE_memory:read";

    /** Shared filter chain + dispatcher, built once for the whole container. */
    private static MockMvc mockMvc;

    @BeforeContainer
    static void setUp() {
        AnnotationConfigWebApplicationContext ctx = new AnnotationConfigWebApplicationContext();
        ctx.register(AuthInvariantWebSecurityConfig.class);
        ctx.setServletContext(new MockServletContext());
        ctx.refresh();

        mockMvc = MockMvcBuilders.webAppContextSetup(ctx)
                .apply(springSecurity())
                .build();
    }

    // ── Model ──

    /** The credential state presented on the request. */
    enum CredentialState {
        /** No credential presented at all. */
        NONE,
        /** A credential was presented but failed validation (no downstream principal bound). */
        INVALID,
        /** Valid credential whose authorities do NOT satisfy the scope-gated route. */
        VALID_WITHOUT_AUTHORITY,
        /** Valid credential whose authorities DO satisfy the scope-gated route. */
        VALID_WITH_AUTHORITY;

        boolean isValid() {
            return this == VALID_WITHOUT_AUTHORITY || this == VALID_WITH_AUTHORITY;
        }
    }

    /** The target path class exercised. */
    enum TargetPath {
        PUBLIC("/api/docs"),
        PROTECTED("/api/protected"),
        SCOPED("/api/scoped");

        final String uri;

        TargetPath(String uri) {
            this.uri = uri;
        }

        boolean isPublic() {
            return this == PUBLIC;
        }
    }

    /** Expected authorization outcome for a (credential, path) pair. */
    enum Outcome { PERMIT, UNAUTHORIZED, FORBIDDEN }

    // ── Property 4 ──

    /**
     * Requirements 6.1, 6.3, 6.4 — drives every (credential-state × path) combination through the
     * shared security filter chain and asserts the fail-closed authorization invariant: the handler
     * is reached iff the path is public or the credentials satisfy the route, and every other case
     * yields a uniform {@code 401} (missing/invalid credential) or {@code 403} (valid credential,
     * insufficient authority) with the downstream handler never invoked.
     */
    @Property(tries = 200)
    void authInvariantHoldsAcrossCredentialAndPathMatrix(
            @ForAll CredentialState credential,
            @ForAll TargetPath path) throws Exception {

        AuthInvariantProbeController.reset();

        MockHttpServletRequestBuilder request = get(path.uri);
        RequestPostProcessor principal = principalFor(credential);
        if (principal != null) {
            request = request.with(principal);
        }

        Outcome expected = expectedOutcome(credential, path);

        switch (expected) {
            case PERMIT -> {
                mockMvc.perform(request).andExpect(status().isOk());
                // Reachable path -> the downstream handler ran exactly once.
                assertThat(hitsFor(path))
                        .as("handler for %s must be invoked exactly once when permitted", path)
                        .isEqualTo(1);
                // IFF direction: a non-public handler is reached only with valid credentials, and it
                // must then observe a non-anonymous Authentication downstream (Requirement 6.1).
                if (!path.isPublic()) {
                    assertThat(credential.isValid())
                            .as("a non-public handler is only reachable with valid credentials")
                            .isTrue();
                    assertThat(AuthInvariantProbeController.lastDownstreamAuthNonAnonymous)
                            .as("downstream Authentication must be non-anonymous on %s", path)
                            .isTrue();
                }
                // On the public path the downstream principal is non-anonymous iff creds were valid.
                if (path.isPublic()) {
                    assertThat(AuthInvariantProbeController.lastDownstreamAuthNonAnonymous)
                            .as("public-path downstream principal is non-anonymous iff creds valid")
                            .isEqualTo(credential.isValid());
                }
            }
            case UNAUTHORIZED -> {
                mockMvc.perform(request).andExpect(status().isUnauthorized());
                assertThat(AuthInvariantProbeController.totalHits())
                        .as("no handler may run for a 401 on %s with %s", path, credential)
                        .isZero();
            }
            case FORBIDDEN -> {
                mockMvc.perform(request).andExpect(status().isForbidden());
                assertThat(AuthInvariantProbeController.totalHits())
                        .as("no handler may run for a 403 on %s with %s", path, credential)
                        .isZero();
            }
        }
    }

    // ── Expected-outcome oracle ──

    private static Outcome expectedOutcome(CredentialState credential, TargetPath path) {
        if (path.isPublic()) {
            return Outcome.PERMIT;                       // Req 6.2 — public regardless of creds.
        }
        if (!credential.isValid()) {
            return Outcome.UNAUTHORIZED;                 // Reqs 6.1, 6.3 — no/invalid creds -> 401.
        }
        // Valid credentials: PROTECTED only needs authentication; SCOPED needs the required scope.
        if (path == TargetPath.SCOPED && credential != CredentialState.VALID_WITH_AUTHORITY) {
            return Outcome.FORBIDDEN;                     // Req 6.4 — insufficient authority -> 403.
        }
        return Outcome.PERMIT;
    }

    private static int hitsFor(TargetPath path) {
        return switch (path) {
            case PUBLIC -> AuthInvariantProbeController.publicHits.get();
            case PROTECTED -> AuthInvariantProbeController.protectedHits.get();
            case SCOPED -> AuthInvariantProbeController.scopedHits.get();
        };
    }

    /**
     * Builds the request post-processor that binds the modeled credential state. {@code NONE} and
     * {@code INVALID} bind nothing (the authorization layer observes no principal); the two valid
     * states bind a non-anonymous {@code Authentication} carrying the appropriate authority.
     */
    private static RequestPostProcessor principalFor(CredentialState credential) {
        return switch (credential) {
            case NONE, INVALID -> null;
            case VALID_WITHOUT_AUTHORITY -> authentication(token(OTHER_SCOPE));
            case VALID_WITH_AUTHORITY -> authentication(token(REQUIRED_SCOPE));
        };
    }

    private static Authentication token(String authority) {
        return new UsernamePasswordAuthenticationToken(
                "user_00000001", "n/a", AuthorityUtils.createAuthorityList(authority));
    }
}
