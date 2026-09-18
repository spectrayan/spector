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
package com.spectrayan.spector.memory.pathway.express.persona;

import com.spectrayan.spector.memory.aisme.homeostasis.InteroceptiveState;
import com.spectrayan.spector.memory.model.ProsodyParameterVector;
import com.spectrayan.spector.memory.model.VocalProsodyDNA;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class VocalProsodyTransferEngineTest {

    @Test
    public void testHighArousalJoy() {
        VocalProsodyDNA dna = VocalProsodyDNA.NEUTRAL;
        InteroceptiveState state = new InteroceptiveState(0.8f, 0.8f, 0.5f, new float[0], System.currentTimeMillis(), 1);
        
        ProsodyParameterVector vector = VocalProsodyTransferEngine.compute(dna, state);
        
        assertNotNull(vector);
        assertEquals("HIGH_AROUSAL_POSITIVE", vector.emotionalTone());
        assertTrue(vector.targetF0Hz() > dna.baselineF0Hz());
        assertTrue(vector.tempoMultiplier() > 1.0f);
    }

    @Test
    public void testLowArousalSadness() {
        VocalProsodyDNA dna = VocalProsodyDNA.NEUTRAL;
        InteroceptiveState state = new InteroceptiveState(-0.5f, -0.6f, -0.4f, new float[0], System.currentTimeMillis(), 1);
        
        ProsodyParameterVector vector = VocalProsodyTransferEngine.compute(dna, state);
        
        assertNotNull(vector);
        assertEquals("LOW_AROUSAL_NEGATIVE", vector.emotionalTone());
        assertTrue(vector.targetF0Hz() < dna.baselineF0Hz());
        assertTrue(vector.tempoMultiplier() < 1.0f);
    }

    @Test
    public void testNeutralState() {
        VocalProsodyDNA dna = VocalProsodyDNA.NEUTRAL;
        InteroceptiveState state = InteroceptiveState.NEUTRAL;
        
        ProsodyParameterVector vector = VocalProsodyTransferEngine.compute(dna, state);
        
        assertNotNull(vector);
        assertEquals("NEUTRAL", vector.emotionalTone());
        assertEquals(dna.baselineF0Hz(), vector.targetF0Hz(), 0.01f);
        assertEquals(1.0f, vector.tempoMultiplier(), 0.01f);
    }
}
