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
package com.spectrayan.spector.memory.kernel.shape;

import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RegistryMemoryContractTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("registryEntryRegisterAndResolve")
    void registryEntryRegisterAndResolve() {
        RegistryLayout layout = new RegistryLayout();
        MemoryId id = MemoryId.of("test", "registry");
        Path file = tempDir.resolve("test_registry.dat");

        try (var reg = new DefaultRegistryMemory(id, layout, 100, 4096L, file)) {
            assertThat(reg.shape()).isEqualTo(MemoryShape.REGISTRY);

            int ord1 = reg.intern("entity.person");
            int ord2 = reg.intern("entity.organization");
            int ord3 = reg.intern("entity.person"); // duplicate

            assertThat(ord1).isEqualTo(0);
            assertThat(ord2).isEqualTo(1);
            assertThat(ord3).isEqualTo(0); // resolved existing
            assertThat(reg.size()).isEqualTo(2);

            assertThat(reg.nameOf(0)).isEqualTo("ENTITY.PERSON");
            assertThat(reg.nameOf(1)).isEqualTo("ENTITY.ORGANIZATION");
            assertThat(reg.idOf("entity.person")).isEqualTo(0);
            assertThat(reg.idOf("entity.organization")).isEqualTo(1);
            assertThat(reg.idOf("nonexistent")).isEqualTo(-1);

            reg.flush();
        }

        // Re-open and verify persistence
        try (var reg2 = new DefaultRegistryMemory(id, layout, 100, 4096L, file)) {
            assertThat(reg2.size()).isEqualTo(2);
            assertThat(reg2.nameOf(0)).isEqualTo("ENTITY.PERSON");
            assertThat(reg2.nameOf(1)).isEqualTo("ENTITY.ORGANIZATION");
        }
    }
}
