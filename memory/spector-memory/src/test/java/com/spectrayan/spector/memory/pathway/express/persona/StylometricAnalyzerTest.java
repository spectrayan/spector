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
