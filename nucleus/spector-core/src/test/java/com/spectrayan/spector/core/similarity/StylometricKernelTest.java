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
package com.spectrayan.spector.core.similarity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class StylometricKernelTest {
    @Test
    public void testStylometricDistanceAndSimilarity() {
        float[] f1 = {1.0f, 2.0f, 3.0f, 4.0f};
        float[] f2 = {1.0f, 3.0f, 1.0f, 5.0f};
        float[] weights = {1.0f, 0.5f, 0.5f, 1.0f};

        // diff: {0, -1, 2, -1}
        // diffSq: {0, 1, 4, 1}
        // weightedDiffSq: {0, 0.5, 2.0, 1.0}
        // sum: 3.5 -> sqrt(3.5)
        
        float expectedDistance = (float) Math.sqrt(3.5);
        float distance = StylometricKernel.stylometricDistance(f1, f2, weights);
        assertEquals(expectedDistance, distance, 1e-5f);
        
        float expectedSimilarity = 1.0f / (1.0f + expectedDistance);
        float similarity = StylometricKernel.stylometricSimilarity(f1, f2, weights);
        assertEquals(expectedSimilarity, similarity, 1e-5f);
    }
}
