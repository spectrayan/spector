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
package com.spectrayan.spector.memory.index;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ADR-0082 Phase 2.1 Quarantine Registry Tests")
class QuarantineRegistryTest {

    @Test
    @DisplayName("quarantine adds entry and isQuarantined returns true")
    void quarantine_addsEntry() {
        QuarantineRegistry registry = new QuarantineRegistry();
        registry.quarantine(42, QuarantineReason.DANGLING_ENTITY);

        assertThat(registry.isQuarantined(42)).isTrue();
        assertThat(registry.quarantinedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("unquarantine removes entry and returns true")
    void unquarantine_removesEntry() {
        QuarantineRegistry registry = new QuarantineRegistry();
        registry.quarantine(42, QuarantineReason.DANGLING_ENTITY);

        boolean released = registry.unquarantine(42);

        assertThat(released).isTrue();
        assertThat(registry.isQuarantined(42)).isFalse();
        assertThat(registry.quarantinedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("unquarantine returns false for non-quarantined edge")
    void unquarantine_returnsFalseForNonQuarantined() {
        QuarantineRegistry registry = new QuarantineRegistry();

        assertThat(registry.unquarantine(99)).isFalse();
    }

    @Test
    @DisplayName("quarantine is idempotent — re-quarantine updates reason")
    void quarantine_idempotent_updatesReason() {
        QuarantineRegistry registry = new QuarantineRegistry();
        registry.quarantine(42, QuarantineReason.DANGLING_ENTITY);
        registry.quarantine(42, QuarantineReason.ORPHANED_HYPEREDGE);

        assertThat(registry.quarantinedCount()).isEqualTo(1);
        List<QuarantinedVertex> snapshot = registry.snapshot();
        assertThat(snapshot).hasSize(1);
        assertThat(snapshot.get(0).reason()).isEqualTo(QuarantineReason.ORPHANED_HYPEREDGE);
    }

    @Test
    @DisplayName("snapshot returns unmodifiable copy")
    void snapshot_returnsUnmodifiableCopy() {
        QuarantineRegistry registry = new QuarantineRegistry();
        registry.quarantine(1, QuarantineReason.DANGLING_ENTITY);
        registry.quarantine(2, QuarantineReason.DANGLING_MEMORY);

        List<QuarantinedVertex> snapshot = registry.snapshot();

        assertThat(snapshot).hasSize(2);
        assertThat(snapshot.stream().map(QuarantinedVertex::edgeId))
                .containsExactlyInAnyOrder(1, 2);
    }

    @Test
    @DisplayName("clear removes all quarantined entries")
    void clear_removesAll() {
        QuarantineRegistry registry = new QuarantineRegistry();
        registry.quarantine(1, QuarantineReason.DANGLING_ENTITY);
        registry.quarantine(2, QuarantineReason.DANGLING_MEMORY);

        registry.clear();

        assertThat(registry.quarantinedCount()).isEqualTo(0);
        assertThat(registry.isQuarantined(1)).isFalse();
        assertThat(registry.isQuarantined(2)).isFalse();
    }

    @Test
    @DisplayName("quarantinedCount reflects live state")
    void quarantinedCount_reflectsLiveState() {
        QuarantineRegistry registry = new QuarantineRegistry();

        assertThat(registry.quarantinedCount()).isEqualTo(0);

        registry.quarantine(10, QuarantineReason.DANGLING_ENTITY);
        registry.quarantine(20, QuarantineReason.DANGLING_MEMORY);
        registry.quarantine(30, QuarantineReason.ORPHANED_HYPEREDGE);

        assertThat(registry.quarantinedCount()).isEqualTo(3);

        registry.unquarantine(20);

        assertThat(registry.quarantinedCount()).isEqualTo(2);
    }
}
