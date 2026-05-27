package com.spectrayan.spector.memory.inhibition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SuppressionSetTest {

    @Test
    void newSetIsEmpty() {
        var set = new SuppressionSet();
        assertThat(set.size()).isZero();
        assertThat(set.isSuppressed("memory-1")).isFalse();
    }

    @Test
    void suppressAndCheck() {
        var set = new SuppressionSet();
        set.suppress("memory-1", "incorrect answer");
        assertThat(set.isSuppressed("memory-1")).isTrue();
        assertThat(set.size()).isEqualTo(1);
    }

    @Test
    void unsuppressRemoves() {
        var set = new SuppressionSet();
        set.suppress("memory-1");
        set.unsuppress("memory-1");
        assertThat(set.isSuppressed("memory-1")).isFalse();
        assertThat(set.size()).isZero();
    }

    @Test
    void clearRemovesAll() {
        var set = new SuppressionSet();
        set.suppress("m1");
        set.suppress("m2");
        set.suppress("m3");
        assertThat(set.size()).isEqualTo(3);

        set.clear();
        assertThat(set.size()).isZero();
    }

    @Test
    void suppressedIdsReturnsUnmodifiableView() {
        var set = new SuppressionSet();
        set.suppress("m1");
        set.suppress("m2");

        var ids = set.suppressedIds();
        assertThat(ids).containsExactlyInAnyOrder("m1", "m2");
    }
}
