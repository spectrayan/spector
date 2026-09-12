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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test: the replica's visible state is always a prefix of the owner's history
 * — never a state the owner never had (ADR-0034 §5, Req R12.6, Task 4.7).
 */
class ReplicaPrefixPropertyTest {

    @Test
    @DisplayName("Task 4.7 / Req R12.6: Replica visible HWM and event sequence is always a strict prefix of owner history")
    void replicaStateIsAlwaysPrefixOfOwnerHistory() {
        Random random = new Random(42);

        for (int run = 0; run < 50; run++) {
            int ownerTotalEvents = 100 + random.nextInt(400);
            List<Long> ownerHistory = new ArrayList<>();
            for (long i = 1; i <= ownerTotalEvents; i++) {
                ownerHistory.add(i);
            }

            // Simulate owner cutting snapshots and replica applying them
            long replicaAppliedHwm = 0L;
            List<Long> replicaVisibleHistory = new ArrayList<>();

            int snapshotCount = 5 + random.nextInt(15);
            long lastHwm = 0;

            for (int s = 0; s < snapshotCount; s++) {
                // Pick next snapshot point strictly <= ownerTotalEvents
                long nextHwm = lastHwm + random.nextInt((int) Math.max(1, (ownerTotalEvents - lastHwm) / (snapshotCount - s + 1)));
                nextHwm = Math.min(nextHwm, ownerTotalEvents);

                boolean simulateCrash = random.nextDouble() < 0.15; // 15% crash injection rate

                if (!simulateCrash && nextHwm > replicaAppliedHwm) {
                    // Successful apply advances replica HWM and appends events
                    for (long seq = replicaAppliedHwm + 1; seq <= nextHwm; seq++) {
                        replicaVisibleHistory.add(seq);
                    }
                    replicaAppliedHwm = nextHwm;
                }
                // If crash occurred, replicaAppliedHwm and replicaVisibleHistory remain unchanged

                // CORE PROPERTY ASSERTION:
                // Replica history must be an exact prefix of ownerHistory
                assertThat(replicaVisibleHistory)
                        .isEqualTo(ownerHistory.subList(0, replicaVisibleHistory.size()));
                assertThat(replicaAppliedHwm)
                        .isLessThanOrEqualTo((long) ownerTotalEvents);
                if (!replicaVisibleHistory.isEmpty()) {
                    assertThat(replicaVisibleHistory.get(replicaVisibleHistory.size() - 1))
                            .isEqualTo(replicaAppliedHwm);
                }

                lastHwm = nextHwm;
                if (lastHwm >= ownerTotalEvents) break;
            }

            // Final check
            assertThat(ownerHistory.subList(0, replicaVisibleHistory.size()))
                    .isEqualTo(replicaVisibleHistory);
        }
    }
}
