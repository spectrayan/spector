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
package com.spectrayan.spector.synapse.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IdentityPaths Specification")
class IdentityPathsTest {

    @Test
    @DisplayName("Shards account identity bundle path by first 4 characters")
    void accountIdentitySharded() {
        Path root = Path.of("/data/spector");
        Path path = IdentityPaths.accountIdentityBundle(root, "0123456789abc");

        assertThat(path).isEqualTo(Path.of("/data/spector/identity/accounts/01/23/0123456789abc/identity.bundle"));
    }

    @Test
    @DisplayName("Shards tenant identity bundle path by first 4 characters")
    void tenantIdentitySharded() {
        Path root = Path.of("/data/spector");
        Path path = IdentityPaths.tenantIdentityBundle(root, "ten-hospital-99");

        assertThat(path).isEqualTo(Path.of("/data/spector/identity/tenants/te/n-/ten-hospital-99/identity.bundle"));
    }

    @Test
    @DisplayName("Handles short identifiers gracefully")
    void shortIdentifiers() {
        Path root = Path.of("/data/spector");
        Path path = IdentityPaths.accountIdentityBundle(root, "a");

        assertThat(path).isEqualTo(Path.of("/data/spector/identity/accounts/00/00/a/identity.bundle"));
    }

    @Test
    @DisplayName("Shards tenant-scoped account identity bundle path")
    void tenantAccountIdentitySharded() {
        Path root = Path.of("/data/spector");
        Path path = IdentityPaths.tenantAccountIdentityBundle(root, "ten-hospital-99", "0123456789abc");

        assertThat(path).isEqualTo(Path.of("/data/spector/identity/tenants/te/n-/ten-hospital-99/accounts/01/23/0123456789abc/identity.bundle"));
    }

    @Test
    @DisplayName("Shards enterprise tenant-rooted namespace directory")
    void enterpriseNamespaceDirSharded() {
        Path root = Path.of("/data/spector");
        Path path = IdentityPaths.enterpriseNamespaceDir(root, "ten-hospital-99", "0123456789abc");

        assertThat(path).isEqualTo(Path.of("/data/spector/tenants/te/n-/ten-hospital-99/namespaces/01/23/0123456789abc"));
    }
}
