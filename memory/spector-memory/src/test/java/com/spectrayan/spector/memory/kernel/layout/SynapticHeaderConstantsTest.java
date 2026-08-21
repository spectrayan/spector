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
package com.spectrayan.spector.memory.kernel.layout;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SynapticHeaderConstants} flag manipulation at offset 34 (ADR-0008).
 */
class SynapticHeaderConstantsTest {

    @Test
    void offset34_governanceAndCrystallizationFlags_workCorrectly() {
        byte flags = 0;
        assertThat(SynapticHeaderConstants.isContradicted(flags)).isFalse();
        assertThat(SynapticHeaderConstants.isRetracted(flags)).isFalse();
        assertThat(SynapticHeaderConstants.isUnverified(flags)).isFalse();
        assertThat(SynapticHeaderConstants.isRestricted(flags)).isFalse();
        assertThat(SynapticHeaderConstants.isCrystallized(flags)).isFalse();

        flags = SynapticHeaderConstants.withRetracted(flags, true);
        assertThat(SynapticHeaderConstants.isRetracted(flags)).isTrue();
        assertThat(SynapticHeaderConstants.isRestricted(flags)).isFalse();

        flags = SynapticHeaderConstants.withRestricted(flags, true);
        assertThat(SynapticHeaderConstants.isRetracted(flags)).isTrue();
        assertThat(SynapticHeaderConstants.isRestricted(flags)).isTrue();

        flags = SynapticHeaderConstants.withUnverified(flags, true);
        assertThat(SynapticHeaderConstants.isUnverified(flags)).isTrue();

        flags = SynapticHeaderConstants.withCrystallized(flags, true);
        assertThat(SynapticHeaderConstants.isCrystallized(flags)).isTrue();

        // Clear retracted
        flags = SynapticHeaderConstants.withRetracted(flags, false);
        assertThat(SynapticHeaderConstants.isRetracted(flags)).isFalse();
        assertThat(SynapticHeaderConstants.isRestricted(flags)).isTrue();
        assertThat(SynapticHeaderConstants.isUnverified(flags)).isTrue();
        assertThat(SynapticHeaderConstants.isCrystallized(flags)).isTrue();
    }
}
