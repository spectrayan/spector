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
package com.spectrayan.spector.synapse.config;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.spectrayan.spector.synapse.config.SynapseProperties.AuthProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.DefaultAdminProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.JwtProperties;

/**
 * Unit tests for {@link AuthPropertiesValidator} fail-fast startup validation.
 *
 * <p>Numeric bounds are validated against the <em>raw</em> Spring {@link MockEnvironment} values
 * (an absent key legitimately falls back to a documented default; a supplied-but-invalid value
 * must abort), whereas required secrets are validated against the bound {@link AuthProperties}
 * record.</p>
 *
 * <p>Requirements: 13.6 (lockout ranges), 14.6/14.7 (PBKDF2 iterations), 18.3 (required secrets),
 * 18.4 (absent → default, no abort), 18.5 (out-of-range abort).</p>
 */
@DisplayName("AuthPropertiesValidator — startup validation")
class AuthPropertiesValidatorTest {

    private static final String VALID_SECRET = "a-sufficiently-long-signing-secret";
    private static final String VALID_ADMIN_PW = "seed-admin-password";

    /** Builds a {@link SynapseProperties} carrying the supplied auth block. */
    private static SynapseProperties propsWith(AuthProperties auth) {
        return new SynapseProperties(0, null, null, null, null, null, auth);
    }

    /** Auth block with valid secrets so numeric-range checks are reachable. */
    private static AuthProperties enabledWithValidSecrets() {
        return new AuthProperties(
                true,
                new JwtProperties(VALID_SECRET, Duration.ofHours(1)),
                null, null,
                new DefaultAdminProperties(VALID_ADMIN_PW),
                null, null, null);
    }

    private static AuthPropertiesValidator validator(AuthProperties auth, MockEnvironment env) {
        return new AuthPropertiesValidator(propsWith(auth), env);
    }

    @Test
    @DisplayName("disabled auth skips all validation even with empty secrets")
    void disabledSkipsValidation() {
        AuthProperties disabled = new AuthProperties(
                false, new JwtProperties("", null), null, null,
                new DefaultAdminProperties(""), null, null, null);
        MockEnvironment env = new MockEnvironment()
                .withProperty("spector.auth.pbkdf2.iterations", "0")
                .withProperty("spector.auth.lockout.max-attempts", "9999");

        assertThatCode(() -> validator(disabled, env).validate())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("happy path: enabled with valid secrets and in-range values does not abort")
    void happyPathDoesNotAbort() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spector.auth.pbkdf2.iterations", "310000")
                .withProperty("spector.auth.lockout.max-attempts", "5")
                .withProperty("spector.auth.lockout.minutes", "15");

        assertThatCode(() -> validator(enabledWithValidSecrets(), env).validate())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("happy path: absent numeric keys fall back to defaults without aborting")
    void absentNumericKeysUseDefaults() {
        // No numeric properties supplied → defaults apply, no abort (18.4).
        assertThatCode(() -> validator(enabledWithValidSecrets(), new MockEnvironment()).validate())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("empty jwt.secret while enabled aborts naming the jwt.secret key")
    void emptyJwtSecretAborts() {
        AuthProperties auth = new AuthProperties(
                true, new JwtProperties("  ", null), null, null,
                new DefaultAdminProperties(VALID_ADMIN_PW), null, null, null);

        assertThatThrownBy(() -> validator(auth, new MockEnvironment()).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spector.auth.jwt.secret");
    }

    @Test
    @DisplayName("empty default-admin.password while enabled aborts naming the password key")
    void emptyAdminPasswordAborts() {
        AuthProperties auth = new AuthProperties(
                true, new JwtProperties(VALID_SECRET, null), null, null,
                new DefaultAdminProperties(null), null, null, null);

        assertThatThrownBy(() -> validator(auth, new MockEnvironment()).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spector.auth.default-admin.password");
    }

    @Test
    @DisplayName("supplied pbkdf2.iterations < 1 aborts naming the iterations key")
    void pbkdf2IterationsBelowOneAborts() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spector.auth.pbkdf2.iterations", "0");

        assertThatThrownBy(() -> validator(enabledWithValidSecrets(), env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spector.auth.pbkdf2.iterations");
    }

    @Test
    @DisplayName("lockout.max-attempts below the lower bound aborts naming the key")
    void lockoutMaxAttemptsBelowLowerBoundAborts() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spector.auth.lockout.max-attempts", "0");

        assertThatThrownBy(() -> validator(enabledWithValidSecrets(), env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spector.auth.lockout.max-attempts");
    }

    @Test
    @DisplayName("lockout.max-attempts above the upper bound aborts naming the key")
    void lockoutMaxAttemptsAboveUpperBoundAborts() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spector.auth.lockout.max-attempts", "101");

        assertThatThrownBy(() -> validator(enabledWithValidSecrets(), env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spector.auth.lockout.max-attempts");
    }

    @Test
    @DisplayName("lockout.minutes below the lower bound aborts naming the key")
    void lockoutMinutesBelowLowerBoundAborts() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spector.auth.lockout.minutes", "0");

        assertThatThrownBy(() -> validator(enabledWithValidSecrets(), env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spector.auth.lockout.minutes");
    }

    @Test
    @DisplayName("lockout.minutes above the upper bound aborts naming the key")
    void lockoutMinutesAboveUpperBoundAborts() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spector.auth.lockout.minutes", "1441");

        assertThatThrownBy(() -> validator(enabledWithValidSecrets(), env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spector.auth.lockout.minutes");
    }

    @Test
    @DisplayName("non-numeric supplied numeric value aborts naming the key")
    void nonNumericValueAborts() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spector.auth.lockout.minutes", "not-a-number");

        assertThatThrownBy(() -> validator(enabledWithValidSecrets(), env).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spector.auth.lockout.minutes");
    }

    @Test
    @DisplayName("abort message is prefixed with the invalid-configuration marker")
    void abortMessageIsDescriptive() {
        AuthProperties auth = new AuthProperties(
                true, new JwtProperties("", null), null, null,
                new DefaultAdminProperties(VALID_ADMIN_PW), null, null, null);

        assertThatThrownBy(() -> validator(auth, new MockEnvironment()).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Invalid auth configuration:");
    }

    @Test
    @DisplayName("null auth block is treated as disabled and skips validation")
    void nullAuthSkipsValidation() {
        // SynapseProperties coerces a null auth block into a disabled default.
        SynapseProperties props = new SynapseProperties(0, null, null, null, null, null, null);
        AuthPropertiesValidator v = new AuthPropertiesValidator(props, new MockEnvironment());

        assertThat(props.auth().enabled()).isFalse();
        assertThatCode(v::validate).doesNotThrowAnyException();
    }
}
