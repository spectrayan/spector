package com.spectrayan.spector.memory.dopamine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlashbulbPolicyTest {

    @Test
    void belowThresholdReturnsNormal() {
        var policy = new FlashbulbPolicy(3.0);
        var decision = policy.evaluate(2.5);
        assertThat(decision.isFlashbulb()).isFalse();
        assertThat(decision.importance()).isEqualTo(-1f);
        assertThat(decision.pinned()).isFalse();
    }

    @Test
    void aboveThresholdTriggersFlashbulb() {
        var policy = new FlashbulbPolicy(3.0);
        var decision = policy.evaluate(4.0);
        assertThat(decision.isFlashbulb()).isTrue();
        assertThat(decision.importance()).isEqualTo(10.0f);
        assertThat(decision.pinned()).isTrue();
    }

    @Test
    void exactThresholdDoesNotTrigger() {
        var policy = new FlashbulbPolicy(3.0);
        var decision = policy.evaluate(3.0);
        assertThat(decision.isFlashbulb()).isFalse();
    }

    @Test
    void customThreshold() {
        var policy = new FlashbulbPolicy(1.0);
        assertThat(policy.evaluate(1.5).isFlashbulb()).isTrue();
        assertThat(policy.evaluate(0.5).isFlashbulb()).isFalse();
    }
}
