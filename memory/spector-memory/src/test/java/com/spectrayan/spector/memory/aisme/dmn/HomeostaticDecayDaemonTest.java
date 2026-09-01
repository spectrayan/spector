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
package com.spectrayan.spector.memory.aisme.dmn;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.aisme.homeostasis.HomeostaticCore;
import com.spectrayan.spector.memory.aisme.homeostasis.InteroceptiveState;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HomeostaticDecayDaemon}.
 */
class HomeostaticDecayDaemonTest {

    @Test
    void run_decaysPosteriorAndStepsHomeostasis() {
        MentalStateTracker mentalStateTracker = mock(MentalStateTracker.class);
        HomeostaticCore homeostaticCore = mock(HomeostaticCore.class);

        when(homeostaticCore.currentState()).thenReturn(InteroceptiveState.NEUTRAL);

        HomeostaticDecayDaemon daemon = new HomeostaticDecayDaemon(mentalStateTracker, homeostaticCore, 0.05f);
        daemon.run();

        verify(mentalStateTracker).decay(anyLong(), eq(0.05f));
        verify(homeostaticCore).step(any(float[].class), eq(1.0f));
    }
}
