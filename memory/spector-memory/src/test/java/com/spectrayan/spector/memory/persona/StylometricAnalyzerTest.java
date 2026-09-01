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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.model.StylometricFeatures;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class StylometricAnalyzerTest {

    @Test
    public void testEmptyText() {
        StylometricFeatures features = StylometricAnalyzer.analyze("");
        assertEquals(StylometricFeatures.NEUTRAL, features);
    }

    @Test
    public void testCasualText() {
        String text = "Hey there! I can't wait to see you. It's gonna be awesome!";
        StylometricFeatures features = StylometricAnalyzer.analyze(text);
        
        assertNotNull(features);
        assertTrue(features.meanSentenceLength() > 0);
        assertTrue(features.formalityScore() < 0.6f); // Expect lower formality due to contractions
        assertTrue(features.exclamationRate() > 0);
    }

    @Test
    public void testFormalText() {
        String text = "The implementation of the algorithm significantly improves computational efficiency. Furthermore, it addresses the underlying performance bottlenecks observed during execution.";
        StylometricFeatures features = StylometricAnalyzer.analyze(text);
        
        assertNotNull(features);
        assertTrue(features.meanSentenceLength() > 0);
        assertTrue(features.formalityScore() > 0.4f); // Expect higher formality
        assertEquals(0.0f, features.exclamationRate(), 0.01f);
    }
}
