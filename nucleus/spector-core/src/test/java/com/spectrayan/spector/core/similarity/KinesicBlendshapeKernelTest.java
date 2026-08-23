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
import static org.junit.jupiter.api.Assertions.*;

public class KinesicBlendshapeKernelTest {
    @Test
    public void testComputeBlendshapes() {
        float[] bs = KinesicBlendshapeKernel.computeBlendshapes(0.8f, 0.5f, 0.5f, 0.1f, 0.5f, 0.1f);
        assertEquals(52, bs.length);
        assertTrue(bs[23] > 0); // mouthSmileLeft
        assertTrue(bs[17] > 0); // jawOpen
    }
    
    @Test
    public void testComputeGazeVector() {
        float[] gaze = KinesicBlendshapeKernel.computeGazeVector(0.0f, 0.0f, 0.0f, 0.8f);
        assertTrue(gaze[0] > 0);
        assertTrue(gaze[1] < 0);
    }
    
    @Test
    public void testComputeHeadPose() {
        float[] pose = KinesicBlendshapeKernel.computeHeadPose(0.0f, 0.0f, 0.0f, 0.5f);
        assertEquals(3, pose.length);
    }
}
