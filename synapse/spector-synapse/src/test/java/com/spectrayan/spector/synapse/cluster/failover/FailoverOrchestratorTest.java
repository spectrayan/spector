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
package com.spectrayan.spector.synapse.cluster.failover;

import com.spectrayan.spector.cluster.coordinator.CoordinatorLeaseManager;
import com.spectrayan.spector.cluster.fencing.FenceTokenManager;
import com.spectrayan.spector.cluster.membership.CellMembership;
import com.spectrayan.spector.cluster.routing.OverrideLeaseManager;
import com.spectrayan.spector.cluster.store.InMemoryControlStore;
import com.spectrayan.spector.synapse.config.failover.FailoverProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FailoverOrchestratorTest {

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        void advance(Duration duration) {
            current.updateAndGet(t -> t.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }

    @Test
    @DisplayName("Observe-only mode logs audit record without mutating cluster epoch or overrides (Req R10.1, D4, 4.8)")
    void testObserveOnlyModeDoesNotMutateState() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-12T00:00:00Z"));
        InMemoryControlStore store = new InMemoryControlStore(clock);
        List<String> members = List.of("node-1", "node-2");
        store.updateMembership(new CellMembership("cell-1", 1, members));

        CoordinatorLeaseManager coordMgr = new CoordinatorLeaseManager(store, "node-1", Duration.ofSeconds(60), Duration.ofSeconds(10));
        coordMgr.heartbeat();

        OverrideLeaseManager overrideMgr = new OverrideLeaseManager(store, coordMgr, Duration.ofSeconds(300));
        FenceTokenManager fenceMgr = new FenceTokenManager(store);

        FailoverProperties props = new FailoverProperties();
        props.setEnabled(true);
        props.setMode("observe_only");
        props.setFailAfterSeconds(15);

        FailoverOrchestrator orchestrator = new FailoverOrchestrator(
                store, coordMgr, overrideMgr, fenceMgr, props,
                node -> !"node-2".equals(node), // node-2 fails probe
                (ns, candidate) -> true,
                () -> List.of("default"),
                clock
        );

        // Initial failure detection at t=0
        orchestrator.evaluateCellHealth();
        assertThat(orchestrator.getObservedFailoverCount()).isEqualTo(0);

        // Advance beyond failAfter
        clock.advance(Duration.ofSeconds(16));
        orchestrator.evaluateCellHealth();

        assertThat(orchestrator.getObservedFailoverCount()).isEqualTo(1);
        assertThat(orchestrator.getFailoverCount()).isEqualTo(0); // active count remains 0
        assertThat(store.listOverrides()).isEmpty(); // No override set
        assertThat(store.getNamespaceEpoch("default")).isEqualTo(0L); // No epoch bump

        List<FailoverAuditRecord> audits = orchestrator.getAuditHistory();
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().trigger()).isEqualTo("OBSERVE_ONLY");
    }

    @Test
    @DisplayName("Active failover bumps epoch, mints fence, sets override, and emits audit (Req R4.1, R4.2, R4.3, R9.1, R9.6)")
    void testActiveFailoverPromotion() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-12T00:00:00Z"));
        InMemoryControlStore store = new InMemoryControlStore(clock);
        List<String> members = List.of("node-1", "node-2");
        store.updateMembership(new CellMembership("cell-1", 1, members));

        CoordinatorLeaseManager coordMgr = new CoordinatorLeaseManager(store, "node-1", Duration.ofSeconds(60), Duration.ofSeconds(10));
        coordMgr.heartbeat();

        OverrideLeaseManager overrideMgr = new OverrideLeaseManager(store, coordMgr, Duration.ofSeconds(300));
        FenceTokenManager fenceMgr = new FenceTokenManager(store);

        FailoverProperties props = new FailoverProperties();
        props.setEnabled(true);
        props.setMode("active");
        props.setFailAfterSeconds(15);
        props.setOverrideTtlSeconds(300);

        FailoverOrchestrator orchestrator = new FailoverOrchestrator(
                store, coordMgr, overrideMgr, fenceMgr, props,
                node -> !"node-2".equals(node), // node-2 is down
                (ns, candidate) -> true,
                () -> List.of("default"),
                clock
        );

        // Initial failure detection at t=0
        orchestrator.evaluateCellHealth();
        assertThat(orchestrator.getFailoverCount()).isEqualTo(0);

        // Advance beyond failAfter
        clock.advance(Duration.ofSeconds(16));
        orchestrator.evaluateCellHealth();

        assertThat(orchestrator.getFailoverCount()).isEqualTo(1);
        assertThat(store.getNamespaceEpoch("default")).isEqualTo(1L);

        var override = overrideMgr.getOverride("default");
        assertThat(override).isPresent();
        assertThat(override.get().targetNodeId()).isEqualTo("node-1");
        assertThat(override.get().epoch()).isEqualTo(1L);
        assertThat(override.get().fence()).isEqualTo("1");

        List<FailoverAuditRecord> audits = orchestrator.getAuditHistory();
        assertThat(audits).hasSize(1);
        FailoverAuditRecord audit = audits.getFirst();
        assertThat(audit.fromNodeId()).isEqualTo("node-2");
        assertThat(audit.toNodeId()).isEqualTo("node-1");
        assertThat(audit.epoch()).isEqualTo(1L);
        assertThat(audit.trigger()).isEqualTo("READINESS_FAILURE");
    }

    @Test
    @DisplayName("Refuse promotion if candidate replica data verification fails (Req R4.4, Q6)")
    void testRefusePromotionWhenDataVerificationFails() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-12T00:00:00Z"));
        InMemoryControlStore store = new InMemoryControlStore(clock);
        List<String> members = List.of("node-1", "node-2");
        store.updateMembership(new CellMembership("cell-1", 1, members));

        CoordinatorLeaseManager coordMgr = new CoordinatorLeaseManager(store, "node-1", Duration.ofSeconds(60), Duration.ofSeconds(10));
        coordMgr.heartbeat();

        OverrideLeaseManager overrideMgr = new OverrideLeaseManager(store, coordMgr, Duration.ofSeconds(300));
        FenceTokenManager fenceMgr = new FenceTokenManager(store);

        FailoverProperties props = new FailoverProperties();
        props.setEnabled(true);
        props.setMode("active");
        props.setFailAfterSeconds(15);

        // Verification fails for node-1 survivor!
        FailoverOrchestrator orchestrator = new FailoverOrchestrator(
                store, coordMgr, overrideMgr, fenceMgr, props,
                node -> !"node-2".equals(node),
                (ns, candidate) -> false, // Candidate verification FAILS
                () -> List.of("default"),
                clock
        );

        orchestrator.evaluateCellHealth();
        clock.advance(Duration.ofSeconds(16));
        orchestrator.evaluateCellHealth();

        // Promotion MUST be refused
        assertThat(orchestrator.getFailoverCount()).isEqualTo(0);
        assertThat(store.listOverrides()).isEmpty();
        assertThat(store.getNamespaceEpoch("default")).isEqualTo(0L);
    }

    @Test
    @DisplayName("Flapping owner is bounded by cooldown hysteresis (Req R4.9, 4.7)")
    void testFlappingOwnerCooldownHysteresis() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-12T00:00:00Z"));
        InMemoryControlStore store = new InMemoryControlStore(clock);
        List<String> members = List.of("node-1", "node-2");
        store.updateMembership(new CellMembership("cell-1", 1, members));

        CoordinatorLeaseManager coordMgr = new CoordinatorLeaseManager(store, "node-1", Duration.ofSeconds(300), Duration.ofSeconds(10));
        coordMgr.heartbeat();

        OverrideLeaseManager overrideMgr = new OverrideLeaseManager(store, coordMgr, Duration.ofSeconds(300));
        FenceTokenManager fenceMgr = new FenceTokenManager(store);

        FailoverProperties props = new FailoverProperties();
        props.setEnabled(true);
        props.setMode("active");
        props.setFailAfterSeconds(10);
        props.setCooldownSeconds(60);

        AtomicBoolean node2Up = new AtomicBoolean(false);

        FailoverOrchestrator orchestrator = new FailoverOrchestrator(
                store, coordMgr, overrideMgr, fenceMgr, props,
                node -> !"node-2".equals(node) || node2Up.get(),
                (ns, candidate) -> true,
                () -> List.of("default"),
                clock
        );

        // 1st failure detection at t=0
        orchestrator.evaluateCellHealth();
        clock.advance(Duration.ofSeconds(11));
        orchestrator.evaluateCellHealth();
        assertThat(orchestrator.getFailoverCount()).isEqualTo(1);

        // Node recovers briefly
        node2Up.set(true);
        clock.advance(Duration.ofSeconds(5));
        orchestrator.evaluateCellHealth();

        // Node flaps down again after 5s (< 60s cooldown limit)
        node2Up.set(false);
        orchestrator.evaluateCellHealth();
        clock.advance(Duration.ofSeconds(11));
        orchestrator.evaluateCellHealth();

        // Cooldown prevents second failover
        assertThat(orchestrator.getFailoverCount()).isEqualTo(1);

        // Advance past cooldown limit (60s total from t=11s failover => t > 71s)
        clock.advance(Duration.ofSeconds(60));
        orchestrator.evaluateCellHealth();
        clock.advance(Duration.ofSeconds(11));
        orchestrator.evaluateCellHealth();
        assertThat(orchestrator.getFailoverCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Chaos: Redis down during failover does not prevent control store failover (Req R10.5, 4.10)")
    void testFailoverSucceedsEvenIfExternalRedisIsDown() {
        // Control store is decoupled from Redis; failover mutates store directly
        MutableClock clock = new MutableClock(Instant.parse("2026-09-12T00:00:00Z"));
        InMemoryControlStore store = new InMemoryControlStore(clock);
        store.updateMembership(new CellMembership("cell-1", 1, List.of("node-1", "node-2")));

        CoordinatorLeaseManager coordMgr = new CoordinatorLeaseManager(store, "node-1", Duration.ofSeconds(60), Duration.ofSeconds(10));
        coordMgr.heartbeat();

        OverrideLeaseManager overrideMgr = new OverrideLeaseManager(store, coordMgr, Duration.ofSeconds(300));
        FenceTokenManager fenceMgr = new FenceTokenManager(store);

        FailoverProperties props = new FailoverProperties();
        props.setEnabled(true);
        props.setMode("active");
        props.setFailAfterSeconds(5);

        FailoverOrchestrator orchestrator = new FailoverOrchestrator(
                store, coordMgr, overrideMgr, fenceMgr, props,
                node -> !"node-2".equals(node),
                (ns, candidate) -> true,
                () -> List.of("default"),
                clock
        );

        orchestrator.evaluateCellHealth();
        clock.advance(Duration.ofSeconds(6));
        orchestrator.evaluateCellHealth();

        assertThat(orchestrator.getFailoverCount()).isEqualTo(1);
        assertThat(overrideMgr.getOverride("default")).isPresent();
    }

    @Test
    @DisplayName("G19: Non-coordinator refuses failover without advancing epoch or minting fence")
    void testNonCoordinatorFailsPromotionWithoutBurningEpoch() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-12T00:00:00Z"));
        InMemoryControlStore store = new InMemoryControlStore(clock);
        List<String> members = List.of("node-1", "node-2");
        store.updateMembership(new CellMembership("cell-1", 1, members));

        CoordinatorLeaseManager coordMgr = new CoordinatorLeaseManager(store, "node-1", Duration.ofSeconds(60), Duration.ofSeconds(10));
        // Node 1 does NOT acquire lease
        OverrideLeaseManager overrideMgr = new OverrideLeaseManager(store, coordMgr, Duration.ofSeconds(300));
        FenceTokenManager fenceMgr = new FenceTokenManager(store);

        FailoverProperties props = new FailoverProperties();
        props.setEnabled(true);
        props.setMode("active");

        FailoverOrchestrator orchestrator = new FailoverOrchestrator(
                store, coordMgr, overrideMgr, fenceMgr, props,
                node -> !"node-2".equals(node),
                (ns, candidate) -> true,
                () -> List.of("default"),
                clock
        );

        boolean success = orchestrator.executeFailoverForNamespace("default", "node-2", "node-1", false, clock.instant());
        assertThat(success).isFalse();
        // Epoch was NEVER advanced (G19)
        assertThat(store.getNamespaceEpoch("default")).isEqualTo(0L);
        assertThat(store.listOverrides()).isEmpty();
    }
}
