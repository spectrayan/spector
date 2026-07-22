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
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for the password round-trip contract of the
 * {@link Pbkdf2PasswordEncoder} bean wired by
 * {@code com.spectrayan.spector.synapse.config.SecurityConfig#passwordEncoder(...)} (jqwik).
 *
 * <p>The encoder is constructed here exactly as the production bean is — a
 * {@link Pbkdf2PasswordEncoder} with an empty secret, a 16-byte salt, and the
 * {@code PBKDF2WithHmacSHA256} algorithm — <b>except</b> that this test uses a deliberately
 * <b>reduced iteration count</b> ({@value #TEST_ITERATIONS}) rather than the production default of
 * {@code 310000}. PBKDF2 is intentionally slow at the production count, and the round-trip
 * properties verified here hold at <em>any</em> iteration count, so a modest value keeps the
 * property fast without weakening what is proven.</p>
 *
 * <h3>Property 5: Password round-trip via {@code Pbkdf2PasswordEncoder}</h3>
 * <ul>
 *   <li>FOR ANY password {@code p} (1..256 chars): {@code matches(p, encode(p))} is {@code true}
 *       (Requirement 14.2).</li>
 *   <li>FOR ANY two passwords {@code p != p'}: {@code matches(p', encode(p))} is {@code false}
 *       (Requirement 14.3).</li>
 *   <li>Encoding the same password twice yields distinct outputs (independent per-encode salt),
 *       yet both encodings still match the original password (Requirement 14.4).</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 14.2, 14.3, 14.4</b></p>
 */
class PasswordEncoderRoundTripPropertyTest {

    /**
     * Reduced PBKDF2 iteration count used for speed. The round-trip properties are independent of
     * the iteration count; production uses {@code 310000} (OWASP-2024 baseline).
     */
    private static final int TEST_ITERATIONS = 1_000;

    /** Salt length (bytes) matching the production {@code SecurityConfig} bean. */
    private static final int SALT_LENGTH = 16;

    /**
     * Builds the encoder identically to {@code SecurityConfig.passwordEncoder(...)} apart from the
     * reduced iteration count documented above.
     *
     * @return a fresh {@link Pbkdf2PasswordEncoder} instance
     */
    private static Pbkdf2PasswordEncoder newEncoder() {
        return new Pbkdf2PasswordEncoder(
                "",
                SALT_LENGTH,
                TEST_ITERATIONS,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
    }

    /** Passwords of 1..256 characters drawn from the full BMP (excludes surrogate code points). */
    @Provide
    Arbitrary<String> passwords() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(256)
                .excludeChars('\uD800', '\uDBFF', '\uDC00', '\uDFFF');
    }

    /** Pairs of <em>distinct</em> passwords, each 1..256 characters. */
    @Provide
    Arbitrary<String[]> distinctPasswordPairs() {
        return Combinators.combine(passwords(), passwords())
                .as((a, b) -> new String[] {a, b})
                .filter(pair -> !pair[0].equals(pair[1]));
    }

    /**
     * Requirement 14.2 — FOR ANY password {@code p}, matching {@code p} against its own encoding
     * confirms a match.
     */
    @Property(tries = 200)
    void matchesOwnEncoding(@ForAll("passwords") String password) {
        Pbkdf2PasswordEncoder encoder = newEncoder();

        String encoded = encoder.encode(password);

        assertThat(encoder.matches(password, encoded))
                .as("password must match its own encoding")
                .isTrue();
    }

    /**
     * Requirement 14.3 — FOR ANY two passwords with differing character sequences, matching one
     * against the other's encoding reports no match.
     */
    @Property(tries = 200)
    void differentPasswordsDoNotMatch(@ForAll("distinctPasswordPairs") String[] pair) {
        Pbkdf2PasswordEncoder encoder = newEncoder();
        String first = pair[0];
        String second = pair[1];

        String encodedFirst = encoder.encode(first);

        assertThat(encoder.matches(second, encodedFirst))
                .as("a distinct password must not match another password's encoding")
                .isFalse();
    }

    /**
     * Requirement 14.4 — Encoding the same password two or more times uses an independent
     * per-encode salt, so the encodings are not byte-for-byte identical; yet each independently
     * salted encoding still matches the original password.
     */
    @Property(tries = 200)
    void independentSaltPerEncodeStillMatches(@ForAll("passwords") String password) {
        Pbkdf2PasswordEncoder encoder = newEncoder();

        String firstEncoding = encoder.encode(password);
        String secondEncoding = encoder.encode(password);

        assertThat(firstEncoding)
                .as("independent per-encode salt must produce distinct encodings")
                .isNotEqualTo(secondEncoding);
        assertThat(encoder.matches(password, firstEncoding))
                .as("first independently-salted encoding must match the original")
                .isTrue();
        assertThat(encoder.matches(password, secondEncoding))
                .as("second independently-salted encoding must match the original")
                .isTrue();
    }
}
