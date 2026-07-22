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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Unit tests for {@link JtiBlocklist} against an in-memory H2 database whose
 * schema mirrors {@code V2__multi_user_auth.sql}.
 */
class JtiBlocklistTest {

    private JtiBlocklist blocklist;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:jti-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                CREATE TABLE jti_blocklist (
                    jti         VARCHAR(255) NOT NULL,
                    expires_at  TIMESTAMP,
                    PRIMARY KEY (jti)
                )
                """).update();
        blocklist = new JtiBlocklist(jdbc);
    }

    @Test
    void isBlockedIsFalseBeforeAdd() {
        assertThat(blocklist.isBlocked("jti-1")).isFalse();
    }

    @Test
    void addThenIsBlockedIsTrue() {
        blocklist.add("jti-1", Instant.now().plus(1, ChronoUnit.HOURS));
        assertThat(blocklist.isBlocked("jti-1")).isTrue();
    }

    @Test
    void addIsIdempotentForSameJti() {
        Instant exp = Instant.now().plus(1, ChronoUnit.HOURS);
        blocklist.add("jti-1", exp);
        // Re-adding the same jti must not fail on the primary key.
        blocklist.add("jti-1", exp.plus(1, ChronoUnit.HOURS));
        assertThat(blocklist.isBlocked("jti-1")).isTrue();
    }

    @Test
    void addAcceptsNullExpiry() {
        blocklist.add("jti-null", null);
        assertThat(blocklist.isBlocked("jti-null")).isTrue();
    }

    @Test
    void isBlockedIsFalseForNullOrBlank() {
        assertThat(blocklist.isBlocked(null)).isFalse();
        assertThat(blocklist.isBlocked("  ")).isFalse();
    }

    @Test
    void addRejectsBlankJti() {
        Instant exp = Instant.now().plus(1, ChronoUnit.HOURS);
        assertThatThrownBy(() -> blocklist.add("  ", exp))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void distinctJtisAreIndependent() {
        blocklist.add("jti-a", Instant.now().plus(1, ChronoUnit.HOURS));
        assertThat(blocklist.isBlocked("jti-a")).isTrue();
        assertThat(blocklist.isBlocked("jti-b")).isFalse();
    }
}
