/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.replication;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guard test enforcing that identity-plane paths can NEVER be enumerated or packaged into a
 * replication manifest (ADR-0034 §9.3, §9.5, Req R9.2, Invariant N4, Task 0.2).
 */
class IdentityExclusionGuardTest {

    @ParameterizedTest(name = "Rejects identity plane path: {0}")
    @ValueSource(strings = {
            "identity.bundle",
            "accounts/acc-001/identity.bundle",
            "tenants/acme/identity.bundle",
            "/data/spector/tenants/acme/identity.bundle",
            "tenants/acme/identity.manifest",
            "accounts/system/runtime.bundle",
            "var/spector/accounts/admin/identity.bundle"
    })
    void testRejectsIdentityPlanePaths(String identityPath) {
        assertThat(ReplicationPathFilter.isIdentityPlanePath(Path.of(identityPath)))
                .as("Path '%s' must be recognized as identity-plane", identityPath)
                .isTrue();

        assertThat(ReplicationPathFilter.isIdentityPlanePath(identityPath))
                .as("Path string '%s' must be recognized as identity-plane", identityPath)
                .isTrue();

        assertThatThrownBy(() -> ReplicationPathFilter.assertNotIdentityPlane(Path.of(identityPath)))
                .isInstanceOf(SpectorValidationException.class)
                .extracting(e -> ((SpectorValidationException) e).errorCode())
                .isEqualTo(ErrorCode.ARGUMENT_INVALID);

        assertThatThrownBy(() -> ReplicationPathFilter.assertNotIdentityPlane(identityPath))
                .isInstanceOf(SpectorValidationException.class)
                .extracting(e -> ((SpectorValidationException) e).errorCode())
                .isEqualTo(ErrorCode.ARGUMENT_INVALID);
    }

    @ParameterizedTest(name = "Permits legitimate rememberer path: {0}")
    @ValueSource(strings = {
            "runtime/runtime.bundle",
            "partitions/000_1717430400/partition.bundle",
            "namespaces/user-1/runtime/runtime.bundle",
            "namespaces/user-1/partitions/001_1719849600/partition.bundle",
            "data/namespaces/acme/user-1/runtime.bundle"
    })
    void testPermitsRemembererPaths(String remembererPath) {
        assertThat(ReplicationPathFilter.isIdentityPlanePath(Path.of(remembererPath)))
                .as("Rememberer path '%s' must not be flagged as identity plane", remembererPath)
                .isFalse();

        assertThat(ReplicationPathFilter.isIdentityPlanePath(remembererPath))
                .as("Rememberer path string '%s' must not be flagged as identity plane", remembererPath)
                .isFalse();

        // Must not throw
        ReplicationPathFilter.assertNotIdentityPlane(Path.of(remembererPath));
        ReplicationPathFilter.assertNotIdentityPlane(remembererPath);
    }
}
