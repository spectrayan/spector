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
package com.spectrayan.spector.memory.persona;

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
