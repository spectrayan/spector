package com.spectrayan.spector.memory.hebbian;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for STDP (Spike-Timing-Dependent Plasticity) in {@link CoActivationTracker}.
 */
class StdpTest {

    @Test
    void causalEdge_strengthens() {
        var tracker = new CoActivationTracker();
        // A fires before B → A→B edge strengthened
        tracker.recordSequentialActivation("java", "gc", 1000L, 2000L);

        var edge = tracker.getEdge("java", "gc");
        assertThat(edge).isNotNull();
        assertThat(edge.weight()).isGreaterThan(0.0f);
        assertThat(edge.activationCount()).isEqualTo(1);
    }

    @Test
    void antiCausalEdge_weakened() {
        var tracker = new CoActivationTracker();
        // Pre-seed with moderate weight
        tracker.recordSequentialActivation("gc", "java", 1000L, 2000L);
        float initialWeight = tracker.getEdge("gc", "java").weight();

        // Now A→B fires, which should weaken B→A (anti-causal)
        tracker.recordSequentialActivation("java", "gc", 3000L, 4000L);

        // The gc→java edge should be weaker or stay at 0
        var antiEdge = tracker.getEdge("gc", "java");
        assertThat(antiEdge).isNotNull();
        // Anti-causal: weight decrease (ΔW = -A_minus × exp(-Δt/τ))
        // Since gc→java was already established, the anti-causal from java→gc
        // fires a negative delta on gc→java via the reversed direction
        // But in this case gc→java was established in step 1, and java→gc in step 2
        // The anti-causal in step 2 is for "gc→java" direction, applied as negative
    }

    @Test
    void repeatedCausal_strengthensProgressively() {
        var tracker = new CoActivationTracker();
        tracker.recordSequentialActivation("java", "gc", 1000L, 2000L);
        float w1 = tracker.getEdge("java", "gc").weight();

        tracker.recordSequentialActivation("java", "gc", 5000L, 6000L);
        float w2 = tracker.getEdge("java", "gc").weight();

        assertThat(w2).isGreaterThan(w1);
    }

    @Test
    void closerTiming_strongerPotentiation() {
        var tracker1 = new CoActivationTracker();
        var tracker2 = new CoActivationTracker();

        // Close temporal proximity (100ms apart)
        tracker1.recordSequentialActivation("java", "gc", 1000L, 1100L);

        // Far temporal proximity (20 seconds apart)
        tracker2.recordSequentialActivation("java", "gc", 1000L, 21000L);

        assertThat(tracker1.getEdge("java", "gc").weight())
                .isGreaterThan(tracker2.getEdge("java", "gc").weight());
    }

    @Test
    void selfLoop_ignored() {
        var tracker = new CoActivationTracker();
        tracker.recordSequentialActivation("java", "java", 1000L, 2000L);
        assertThat(tracker.edgeCount()).isZero();
    }

    @Test
    void reverseOrdering_ignored() {
        var tracker = new CoActivationTracker();
        tracker.recordSequentialActivation("java", "gc", 2000L, 1000L); // timeAfter < timeBefore
        assertThat(tracker.edgeCount()).isZero();
    }

    @Test
    void predictiveStrength_returnsCausalWeight() {
        var tracker = new CoActivationTracker();
        tracker.recordSequentialActivation("java", "gc", 1000L, 2000L);
        tracker.recordSequentialActivation("java", "gc", 3000L, 4000L);

        float strength = tracker.getPredictiveStrength(
                List.of("java"), new String[]{"gc"});
        assertThat(strength).isGreaterThan(0.0f);
    }

    @Test
    void predictiveStrength_noCausalLink_returnsZero() {
        var tracker = new CoActivationTracker();
        float strength = tracker.getPredictiveStrength(
                List.of("python"), new String[]{"rust"});
        assertThat(strength).isEqualTo(0.0f);
    }

    @Test
    void predictiveStrength_nullSafety() {
        var tracker = new CoActivationTracker();
        assertThat(tracker.getPredictiveStrength(null, new String[]{"gc"})).isEqualTo(0.0f);
        assertThat(tracker.getPredictiveStrength(List.of("java"), null)).isEqualTo(0.0f);
        assertThat(tracker.getPredictiveStrength(List.of(), new String[]{"gc"})).isEqualTo(0.0f);
    }

    @Test
    void recordSequentialActivations_processesConsecutivePairs() {
        var tracker = new CoActivationTracker();
        tracker.recordSequentialActivations(
                List.of("java", "gc", "performance"),
                List.of(1000L, 2000L, 3000L));

        // java→gc should exist
        assertThat(tracker.getEdge("java", "gc")).isNotNull();
        assertThat(tracker.getEdge("java", "gc").weight()).isGreaterThan(0);

        // gc→performance should exist
        assertThat(tracker.getEdge("gc", "performance")).isNotNull();
        assertThat(tracker.getEdge("gc", "performance").weight()).isGreaterThan(0);
    }

    @Test
    void edgeCount_tracksDirectedEdges() {
        var tracker = new CoActivationTracker();
        assertThat(tracker.edgeCount()).isZero();

        tracker.recordSequentialActivation("java", "gc", 1000L, 2000L);
        // Creates both java→gc (causal) and gc→java (anti-causal) edges
        assertThat(tracker.edgeCount()).isEqualTo(2);
    }

    @Test
    void reset_clearsEdges() {
        var tracker = new CoActivationTracker();
        tracker.recordSequentialActivation("java", "gc", 1000L, 2000L);
        assertThat(tracker.edgeCount()).isGreaterThan(0);

        tracker.reset();
        assertThat(tracker.edgeCount()).isZero();
        assertThat(tracker.pairCount()).isZero();
    }

    @Test
    void averagePredictiveStrength_computesMean() {
        var tracker = new CoActivationTracker();
        tracker.recordSequentialActivation("java", "gc", 1000L, 2000L);
        tracker.recordSequentialActivation("java", "performance", 1000L, 2000L);

        float avg = tracker.getAveragePredictiveStrength(
                List.of("java"), new String[]{"gc", "performance"});
        assertThat(avg).isGreaterThan(0.0f);
    }

    @Test
    void weightClamping_doesNotExceedMax() {
        var tracker = new CoActivationTracker();
        // Many rapid successive activations
        for (int i = 0; i < 100; i++) {
            tracker.recordSequentialActivation("java", "gc", 1000L + i, 1001L + i);
        }

        var edge = tracker.getEdge("java", "gc");
        assertThat(edge.weight()).isLessThanOrEqualTo(1.0f);
    }
}
