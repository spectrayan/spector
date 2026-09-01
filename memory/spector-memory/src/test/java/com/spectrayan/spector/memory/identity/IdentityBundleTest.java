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
package com.spectrayan.spector.memory.identity;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.spectrayan.spector.commons.error.SpectorMemoryException;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.InterestLevel;
import com.spectrayan.spector.memory.model.PersonaContext;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.memory.model.TenantSoul;
import com.spectrayan.spector.memory.model.UserSoul;
import com.spectrayan.spector.memory.neurodivergent.IcnuWeights;

@DisplayName("IdentityBundle Specifications")
class IdentityBundleTest {

    @Nested
    @DisplayName("In-Memory Heap Bundle")
    class HeapBundleTests {

        @Test
        @DisplayName("Initial state is empty across all regions")
        void initialEmpty() {
            try (IdentityBundle bundle = IdentityBundle.heap()) {
                for (IdentityRegionId region : IdentityRegionId.values()) {
                    assertThat(bundle.isEmpty(region)).isTrue();
                    assertThat(bundle.getVersion(region)).isZero();
                }
                assertThat(bundle.readSoul()).isEmpty();
                assertThat(bundle.readSalience()).isEmpty();
                assertThat(bundle.readContinuity()).isEmpty();
                assertThat(bundle.readPolicy()).isEmpty();
            }
        }

        @Test
        @DisplayName("Writes and reads UserSoul")
        void readWriteUserSoul() {
            try (IdentityBundle bundle = IdentityBundle.heap()) {
                PersonaContext persona = PersonaContext.builder()
                        .about("Security Researcher")
                        .occupation("DevOps")
                        .build();
                UserSoul soul = new UserSoul("usr-1", "Alice", "Security Researcher", persona, null, (short) 1, Instant.now(), Instant.now());

                bundle.writeSoul(soul);

                assertThat(bundle.isEmpty(IdentityRegionId.SOUL)).isFalse();
                assertThat(bundle.getVersion(IdentityRegionId.SOUL)).isEqualTo(1);

                Optional<SoulContext> read = bundle.readSoul();
                assertThat(read).isPresent();
                assertThat(read.get()).isInstanceOf(UserSoul.class);

                UserSoul userSoul = (UserSoul) read.get();
                assertThat(userSoul.id()).isEqualTo("usr-1");
                assertThat(userSoul.name()).isEqualTo("Alice");
                assertThat(userSoul.description()).isEqualTo("Security Researcher");
            }
        }

        @Test
        @DisplayName("Writes and reads AgentSoul")
        void readWriteAgentSoul() {
            try (IdentityBundle bundle = IdentityBundle.heap()) {
                AgentSoul agentSoul = AgentSoul.builder()
                        .id("agent-forge")
                        .name("Forge")
                        .description("Senior Full-Stack Developer")
                        .systemPrompt("You are Forge.")
                        .purpose("Build high-performance systems")
                        .personality("Pragmatic, rigorous")
                        .tools(List.of("compile", "test"))
                        .soulVersion((short) 2)
                        .build();

                bundle.writeSoul(agentSoul);

                Optional<SoulContext> read = bundle.readSoul();
                assertThat(read).isPresent();
                assertThat(read.get()).isInstanceOf(AgentSoul.class);

                AgentSoul readAgent = (AgentSoul) read.get();
                assertThat(readAgent.id()).isEqualTo("agent-forge");
                assertThat(readAgent.name()).isEqualTo("Forge");
                assertThat(readAgent.tools()).containsExactly("compile", "test");
            }
        }

        @Test
        @DisplayName("Writes and reads TenantSoul")
        void readWriteTenantSoul() {
            try (IdentityBundle bundle = IdentityBundle.heap()) {
                TenantSoul tenantSoul = new TenantSoul("ten-1", "Acme Health", "Healthcare platform",
                        List.of("cardiology"), List.of("HIPAA"), null, (short) 1, Instant.now(), Instant.now());

                bundle.writeSoul(tenantSoul);

                Optional<SoulContext> read = bundle.readSoul();
                assertThat(read).isPresent();
                assertThat(read.get()).isInstanceOf(TenantSoul.class);
                assertThat(read.get().name()).isEqualTo("Acme Health");
            }
        }

        @Test
        @DisplayName("Writes and reads SalienceProfile")
        void readWriteSalienceProfile() {
            try (IdentityBundle bundle = IdentityBundle.heap()) {
                SalienceProfile profile = SalienceProfile.builder()
                        .interest("kubernetes", InterestLevel.CRITICAL)
                        .interest("java panama", InterestLevel.HIGH)
                        .icnuWeights(new IcnuWeights(0.4f, 0.2f, 0.3f, 0.1f))
                        .alpha(0.7f)
                        .beta(0.3f)
                        .build();

                bundle.writeSalience(profile);

                assertThat(bundle.isEmpty(IdentityRegionId.SALIENCE)).isFalse();
                assertThat(bundle.getVersion(IdentityRegionId.SALIENCE)).isEqualTo(1);

                Optional<SalienceProfile> read = bundle.readSalience();
                assertThat(read).isPresent();
                assertThat(read.get().alpha()).isEqualTo(0.7f);
                assertThat(read.get().beta()).isEqualTo(0.3f);
                assertThat(read.get().interests()).hasSize(2);
            }
        }

        @Test
        @DisplayName("Writes and reads Continuity and Policy")
        void readWriteContinuityAndPolicy() {
            try (IdentityBundle bundle = IdentityBundle.heap()) {
                bundle.writeContinuity("Historical narrative v1");
                bundle.writePolicy("HIPAA compliance floor");

                assertThat(bundle.readContinuity()).contains("Historical narrative v1");
                assertThat(bundle.readPolicy()).contains("HIPAA compliance floor");

                bundle.clearRegion(IdentityRegionId.CONTINUITY);
                assertThat(bundle.readContinuity()).isEmpty();
                assertThat(bundle.isEmpty(IdentityRegionId.CONTINUITY)).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("File-Backed Mmap Bundle")
    class FileBundleTests {

        @Test
        @DisplayName("Persists data across close and reopen")
        void persistenceAcrossReopen(@TempDir Path tempDir) {
            Path bundlePath = tempDir.resolve("accounts/01/23/0123456789abc/identity.bundle");

            try (IdentityBundle bundle = IdentityBundle.open(bundlePath, true)) {
                UserSoul soul = new UserSoul("0123456789abc", "Bob", "Architect", null, null);
                bundle.writeSoul(soul);

                SalienceProfile profile = SalienceProfile.builder()
                        .interest("security", InterestLevel.CRITICAL)
                        .build();
                bundle.writeSalience(profile);
            }

            assertThat(Files.exists(bundlePath)).isTrue();
            assertThat(bundlePath.toFile().length()).isEqualTo(IdentityBundleHeader.TOTAL_INITIAL_SIZE);

            try (IdentityBundle reopened = IdentityBundle.open(bundlePath, false)) {
                Optional<SoulContext> soul = reopened.readSoul();
                assertThat(soul).isPresent();
                assertThat(soul.get().name()).isEqualTo("Bob");

                Optional<SalienceProfile> salience = reopened.readSalience();
                assertThat(salience).isPresent();
                assertThat(salience.get().interests()).hasSize(1);
            }
        }

        @Test
        @DisplayName("Throws when payload exceeds region allocated capacity")
        void capacityExceeded(@TempDir Path tempDir) {
            Path bundlePath = tempDir.resolve("overflow/identity.bundle");

            try (IdentityBundle bundle = IdentityBundle.open(bundlePath, true)) {
                byte[] hugePayload = new byte[(int) IdentityBundleHeader.DEFAULT_REGION_ALLOCATION + 1];
                assertThatThrownBy(() -> bundle.writeRaw(IdentityRegionId.SOUL, hugePayload))
                        .isInstanceOf(SpectorMemoryException.class);
            }
        }
    }
}
