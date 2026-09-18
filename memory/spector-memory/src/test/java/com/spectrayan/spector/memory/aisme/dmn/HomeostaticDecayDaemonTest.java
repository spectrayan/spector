/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.memory.aisme.dmn;

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
