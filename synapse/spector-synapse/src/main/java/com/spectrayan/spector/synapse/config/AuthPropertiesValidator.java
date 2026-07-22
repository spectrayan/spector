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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.spectrayan.spector.synapse.config.SynapseProperties.AuthProperties;

import jakarta.annotation.PostConstruct;

/**
 * Fail-fast startup validation for the {@code spector.auth.*} configuration.
 *
 * <p>The {@link AuthProperties} record's compact constructors silently coerce out-of-range numeric
 * values to their documented defaults, which is appropriate for optional tuning knobs but is
 * <strong>unsafe</strong> for required secrets and hard range bounds. This validator runs during
 * context initialization (via {@link PostConstruct}) and <strong>aborts startup</strong> with a
 * message that identifies the offending configuration key when authentication is enabled and a
 * value is missing or out of range, rather than starting the server with a silently corrected
 * (and possibly surprising) configuration.</p>
 *
 * <p>Validation is only performed when {@code spector.auth.enabled=true}; when the feature is off
 * the server preserves its legacy single-user behavior and these keys carry no meaning.</p>
 *
 * <p>To distinguish an <em>absent</em> key (which legitimately falls back to a documented default)
 * from a <em>supplied-but-invalid</em> value (which must abort startup, per Requirements 18.5 and
 * 13.6), numeric bounds are checked against the <em>raw</em> resolved values read from the Spring
 * {@link Environment} rather than against the already-coerced {@link AuthProperties} record.
 * Required secrets are validated against the bound record because the record does not coerce
 * them.</p>
 *
 * <p>Requirements: 13.6 (lockout ranges), 14.6/14.7 (PBKDF2 iterations), 18.3 (required secrets),
 * 18.5 (out-of-range abort).</p>
 */
@Component
public class AuthPropertiesValidator {

    private static final Logger log = LoggerFactory.getLogger(AuthPropertiesValidator.class);

    private static final String KEY_JWT_SECRET = "spector.auth.jwt.secret";
    private static final String KEY_ADMIN_PASSWORD = "spector.auth.default-admin.password";
    private static final String KEY_PBKDF2_ITERATIONS = "spector.auth.pbkdf2.iterations";
    private static final String KEY_LOCKOUT_MAX_ATTEMPTS = "spector.auth.lockout.max-attempts";
    private static final String KEY_LOCKOUT_MINUTES = "spector.auth.lockout.minutes";

    private static final int MAX_ATTEMPTS_LOWER = 1;
    private static final int MAX_ATTEMPTS_UPPER = 100;
    private static final int LOCKOUT_MINUTES_LOWER = 1;
    private static final int LOCKOUT_MINUTES_UPPER = 1440;
    private static final int PBKDF2_ITERATIONS_LOWER = 1;

    private final SynapseProperties properties;
    private final Environment environment;

    public AuthPropertiesValidator(SynapseProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    /**
     * Validate the auth configuration during context startup.
     *
     * @throws IllegalStateException when authentication is enabled and a required secret is empty
     *                               or a supplied numeric value is out of its documented range;
     *                               the message identifies the offending configuration key
     */
    @PostConstruct
    public void validate() {
        AuthProperties auth = properties.auth();
        if (auth == null || !auth.enabled()) {
            // Feature off: legacy single-user behavior; auth keys carry no meaning.
            return;
        }

        // 18.3: required secrets must resolve to a non-empty value when auth is enabled.
        requireNonEmptySecret(KEY_JWT_SECRET, auth.jwt() == null ? null : auth.jwt().secret());
        requireNonEmptySecret(
                KEY_ADMIN_PASSWORD,
                auth.defaultAdmin() == null ? null : auth.defaultAdmin().password());

        // 14.6/14.7: a supplied PBKDF2 iteration count must be >= 1 (absent -> default 310000).
        requireInRange(KEY_PBKDF2_ITERATIONS, PBKDF2_ITERATIONS_LOWER, Integer.MAX_VALUE);

        // 13.6: supplied lockout bounds must fall within their documented ranges
        // (absent -> documented defaults of 5 attempts / 15 minutes).
        requireInRange(KEY_LOCKOUT_MAX_ATTEMPTS, MAX_ATTEMPTS_LOWER, MAX_ATTEMPTS_UPPER);
        requireInRange(KEY_LOCKOUT_MINUTES, LOCKOUT_MINUTES_LOWER, LOCKOUT_MINUTES_UPPER);

        log.info("Auth configuration validated: multi-user authentication is enabled");
    }

    private void requireNonEmptySecret(String key, String boundValue) {
        if (boundValue == null || boundValue.isBlank()) {
            abort(key, "must resolve to a non-empty value when 'spector.auth.enabled=true' "
                    + "(provide it via a ${env:VAR} placeholder)");
        }
    }

    /**
     * Validate a numeric key against an inclusive range, distinguishing an absent key (which
     * legitimately falls back to a documented default and is therefore skipped) from a
     * supplied-but-invalid value (which aborts startup).
     */
    private void requireInRange(String key, int lowerInclusive, int upperInclusive) {
        String raw = environment.getProperty(key);
        if (raw == null || raw.isBlank()) {
            // Absent: documented default applies (18.4); no validation failure.
            return;
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            abort(key, "must be an integer between " + lowerInclusive + " and " + upperInclusive
                    + " inclusive, but was '" + raw + "'");
            return; // unreachable; abort throws
        }
        if (value < lowerInclusive || value > upperInclusive) {
            abort(key, "must be an integer between " + lowerInclusive + " and " + upperInclusive
                    + " inclusive, but was " + value);
        }
    }

    private void abort(String key, String detail) {
        String message = "Invalid auth configuration: '" + key + "' " + detail;
        log.error(message);
        throw new IllegalStateException(message);
    }
}
