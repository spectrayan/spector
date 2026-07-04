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
package com.spectrayan.spector.memory.temporal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for importance-based temporal chain pruning.
 *
 * <p>Verifies that {@link TemporalChain#pruneByImportance} correctly protects
 * high-importance memories from pruning while allowing low-importance old
 * memories to be removed.</p>
 */
class TemporalImportancePruneTest {

    private TemporalChain chain;

    @BeforeEach
    void setUp() {
        chain = new TemporalChain(100);
    }

    @AfterEach
    void tearDown() {
        chain.close();
    }

    @Test
    void prune_lowImportanceOldSessionIsPruned() {
        // Link memories 0 → 1 → 2 in session 1
        chain.link(1, 0, 1);
        chain.link(2, 1, 1);

        // All linked — cutoff far in the future ensures they're "old enough"
        long futureCutoff = System.currentTimeMillis() + 10_000_000L;

        // Low importance for all
        int pruned = chain.pruneByImportance(futureCutoff, 1.0f,
                memIdx -> 0.5f);  // all below threshold

        // All 3 nodes should be pruned (they're linked and low-importance)
        assertThat(pruned).isGreaterThanOrEqualTo(2);  // At least the linked ones

        // Verify they're actually unlinked
        assertThat(chain.isLinked(0)).isFalse();
        assertThat(chain.isLinked(1)).isFalse();
        assertThat(chain.isLinked(2)).isFalse();
    }

    @Test
    void prune_highImportanceProtected() {
        // Link memories 0 → 1 → 2 in session 1
        chain.link(1, 0, 1);
        chain.link(2, 1, 1);

        long futureCutoff = System.currentTimeMillis() + 10_000_000L;

        // High importance for all — should NOT prune
        int pruned = chain.pruneByImportance(futureCutoff, 1.0f,
                memIdx -> 5.0f);  // all above threshold

        assertThat(pruned).isEqualTo(0);

        // Verify they're still linked
        assertThat(chain.isLinked(1)).isTrue();
        assertThat(chain.isLinked(2)).isTrue();
    }

    @Test
    void prune_selectiveByImportance() {
        // Session 1: 0 → 1 → 2 (low importance)
        chain.link(1, 0, 1);
        chain.link(2, 1, 1);

        // Session 2: 5 → 6 → 7 (high importance)
        chain.link(6, 5, 2);
        chain.link(7, 6, 2);

        long futureCutoff = System.currentTimeMillis() + 10_000_000L;

        // Session 1 memories are low importance, session 2 high importance
        int pruned = chain.pruneByImportance(futureCutoff, 1.0f,
                memIdx -> {
                    if (memIdx >= 5 && memIdx <= 7) return 5.0f;  // high
                    return 0.5f;  // low
                });

        // Session 1 nodes should be pruned, session 2 should survive
        assertThat(pruned).isGreaterThanOrEqualTo(2);  // session 1

        // Session 2 chain should be intact
        assertThat(chain.isLinked(6)).isTrue();
        assertThat(chain.isLinked(7)).isTrue();
    }

    @Test
    void prune_nullProviderReturnsZero() {
        chain.link(1, 0, 1);

        int pruned = chain.pruneByImportance(
                System.currentTimeMillis() + 10_000_000L, 1.0f, null);

        assertThat(pruned).isEqualTo(0);
    }

    @Test
    void prune_notOldEnoughIsProtected() {
        // Link memories
        chain.link(1, 0, 1);
        chain.link(2, 1, 1);

        // Cutoff in the past — nodes are newer than cutoff
        long pastCutoff = System.currentTimeMillis() - 10_000_000L;

        // Even with low importance, shouldn't prune (not old enough)
        int pruned = chain.pruneByImportance(pastCutoff, 1.0f,
                memIdx -> 0.0f);

        assertThat(pruned).isEqualTo(0);
        assertThat(chain.isLinked(1)).isTrue();
    }
}
