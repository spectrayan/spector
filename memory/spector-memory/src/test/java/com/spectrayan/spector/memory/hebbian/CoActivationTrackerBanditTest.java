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
package com.spectrayan.spector.memory.hebbian;

import static org.assertj.core.api.Assertions.*;

import com.spectrayan.spector.memory.adaptor.RunningStats;
import com.spectrayan.spector.memory.model.CognitiveProfile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link CoActivationTracker} bandit statistics persistence (COAX v2 format).
 */
@DisplayName("CoActivationTracker — Bandit Stats")
class CoActivationTrackerBanditTest {

    @TempDir
    Path tempDir;

    // ══════════════════════════════════════════════════════════════
    // Fresh state
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("new tracker has empty bandit stats")
    void emptyBanditStatsOnFreshTracker() {
        try (var tracker = new CoActivationTracker(64)) {
            Map<Long, EnumMap<CognitiveProfile, RunningStats>> stats = tracker.banditStats();
            assertThat(stats).isNotNull();
            assertThat(stats).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Update and retrieval
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("updateBanditStats() then banditStats() returns the data")
    void updateAndRetrieveBanditStats() {
        try (var tracker = new CoActivationTracker(64)) {
            var bandit = new ConcurrentHashMap<Long, EnumMap<CognitiveProfile, RunningStats>>();
            var profileStats = new EnumMap<CognitiveProfile, RunningStats>(CognitiveProfile.class);
            profileStats.put(CognitiveProfile.DEBUGGING,
                    new RunningStats(0.8f, 15, 12, System.currentTimeMillis()));
            bandit.put(42L, profileStats);

            tracker.updateBanditStats(bandit);

            Map<Long, EnumMap<CognitiveProfile, RunningStats>> retrieved = tracker.banditStats();
            assertThat(retrieved).hasSize(1);
            assertThat(retrieved).containsKey(42L);
            assertThat(retrieved.get(42L)).containsKey(CognitiveProfile.DEBUGGING);

            RunningStats rs = retrieved.get(42L).get(CognitiveProfile.DEBUGGING);
            assertThat(rs.ema()).isEqualTo(0.8f);
            assertThat(rs.totalSignals()).isEqualTo(15);
            assertThat(rs.positiveSignals()).isEqualTo(12);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Save / Load round-trip (COAX v2)
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("save + load round-trip preserves bandit stats in COAX v2 format")
    void saveLoadRoundTripV2() {
        Path file = tempDir.resolve("tracker.coax");

        long ctxHash = 12345L;
        float ema = 0.75f;
        int totalSignals = 20;
        int positiveSignals = 15;
        long lastUpdatedMs = System.currentTimeMillis();

        // Save
        try (var tracker = new CoActivationTracker(64)) {
            // Add some co-activation data too
            tracker.recordCoActivation("java", "database");

            // Set bandit stats
            var bandit = new ConcurrentHashMap<Long, EnumMap<CognitiveProfile, RunningStats>>();
            var profileStats = new EnumMap<CognitiveProfile, RunningStats>(CognitiveProfile.class);
            profileStats.put(CognitiveProfile.EXPLORING,
                    new RunningStats(ema, totalSignals, positiveSignals, lastUpdatedMs));
            bandit.put(ctxHash, profileStats);
            tracker.updateBanditStats(bandit);

            tracker.save(file);
        }

        // Load and verify
        try (var loaded = CoActivationTracker.load(file, 64, 128)) {
            Map<Long, EnumMap<CognitiveProfile, RunningStats>> banditStats = loaded.banditStats();
            assertThat(banditStats).containsKey(ctxHash);

            RunningStats rs = banditStats.get(ctxHash).get(CognitiveProfile.EXPLORING);
            assertThat(rs).isNotNull();
            assertThat(rs.ema()).isCloseTo(ema, within(1e-6f));
            assertThat(rs.totalSignals()).isEqualTo(totalSignals);
            assertThat(rs.positiveSignals()).isEqualTo(positiveSignals);
            assertThat(rs.lastUpdatedMs()).isEqualTo(lastUpdatedMs);

            // Also verify co-activation data survived
            assertThat(loaded.getCoActivation("java", "database")).isGreaterThan(0);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // v1 backward compatibility
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("loading a v1-format file returns empty bandit stats")
    void loadV1FileReturnsEmptyBanditStats() throws IOException {
        // Create a minimal v1 COAX file:
        // v1 header: magic(4) + version=1(4) + pairCap(4) + edgeCap(4) + pairs=0(4) + edges=0(4)
        // followed by empty pair table, empty edge table, empty tag index
        Path v1File = tempDir.resolve("v1.coax");
        int pairCap = 64;
        int edgeCap = 64;

        try (FileChannel ch = FileChannel.open(v1File,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            // 24-byte v1 header
            ByteBuffer header = ByteBuffer.allocate(24);
            header.putInt(0x434F4158);  // "COAX" magic
            header.putInt(1);           // version 1
            header.putInt(pairCap);     // pair capacity
            header.putInt(edgeCap);     // edge capacity
            header.putInt(0);           // pair count = 0
            header.putInt(0);           // edge count = 0
            header.flip();
            ch.write(header);

            // Pair table: pairCap * 32 bytes (all zeros)
            ByteBuffer pairBuf = ByteBuffer.allocate(pairCap * 32);
            ch.write(pairBuf);

            // Edge table: edgeCap * 40 bytes (all zeros)
            ByteBuffer edgeBuf = ByteBuffer.allocate(edgeCap * 40);
            ch.write(edgeBuf);

            // Tag index: count = 0
            ByteBuffer tagCount = ByteBuffer.allocate(4);
            tagCount.putInt(0);
            tagCount.flip();
            ch.write(tagCount);

            ch.force(true);
        }

        try (var loaded = CoActivationTracker.load(v1File, 64, 128)) {
            Map<Long, EnumMap<CognitiveProfile, RunningStats>> banditStats = loaded.banditStats();
            assertThat(banditStats).isNotNull();
            assertThat(banditStats).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // File size verification
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("saved file with bandit stats is larger than one without")
    void banditStatsIncludedInSaveOutput() throws IOException {
        Path fileNoBandit = tempDir.resolve("no-bandit.coax");
        Path fileWithBandit = tempDir.resolve("with-bandit.coax");

        // Save tracker WITHOUT bandit stats
        try (var tracker = new CoActivationTracker(64)) {
            tracker.recordCoActivation("alpha", "beta");
            tracker.save(fileNoBandit);
        }

        // Save tracker WITH bandit stats
        try (var tracker = new CoActivationTracker(64)) {
            tracker.recordCoActivation("alpha", "beta");
            var bandit = new ConcurrentHashMap<Long, EnumMap<CognitiveProfile, RunningStats>>();
            var profileStats = new EnumMap<CognitiveProfile, RunningStats>(CognitiveProfile.class);
            profileStats.put(CognitiveProfile.DEBUGGING,
                    new RunningStats(0.9f, 50, 45, System.currentTimeMillis()));
            profileStats.put(CognitiveProfile.EXPLORING,
                    new RunningStats(0.6f, 30, 18, System.currentTimeMillis()));
            bandit.put(1L, profileStats);
            tracker.updateBanditStats(bandit);
            tracker.save(fileWithBandit);
        }

        long sizeNoBandit = Files.size(fileNoBandit);
        long sizeWithBandit = Files.size(fileWithBandit);

        // With bandit: 2 entries × 32 bytes = 64 bytes extra
        assertThat(sizeWithBandit).isGreaterThan(sizeNoBandit);
        // At minimum, the bandit section adds 2*32 = 64 bytes
        assertThat(sizeWithBandit - sizeNoBandit).isGreaterThanOrEqualTo(64);
    }
}
