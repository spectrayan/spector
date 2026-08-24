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
package com.spectrayan.spector.core.expression;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;

public class KinesicBlendshapeKernel {
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    public static float[] computeBlendshapes(float valence, float arousal, float dominance, float cognitiveLoad, float speechActivity, float asymmetryBias) {
        float[] blendshapes = new float[52];
        
        // Smiles: mouthSmileLeft, mouthSmileRight (indices 30, 31 typically, but we'll use assumed indices or just populate them. Let's use standard ARKit indices if we can, or just arbitrary since they are part of a 52-length array)
        // Let's use indices 0-51 arbitrarily but mapped well. ARKit standard:
        // 0: eyeBlinkLeft, 1: eyeLookDownLeft, 2: eyeLookInLeft, 3: eyeLookOutLeft, 4: eyeLookUpLeft, 5: eyeSquintLeft, 6: eyeWideLeft
        // 7: eyeBlinkRight, 8: eyeLookDownRight, 9: eyeLookInRight, 10: eyeLookOutRight, 11: eyeLookUpRight, 12: eyeSquintRight, 13: eyeWideRight
        // 14: jawForward, 15: jawLeft, 16: jawRight, 17: jawOpen
        // 18: mouthClose, 19: mouthFunnel, 20: mouthPucker, 21: mouthLeft, 22: mouthRight, 23: mouthSmileLeft, 24: mouthSmileRight
        // 25: mouthFrownLeft, 26: mouthFrownRight, 27: mouthDimpleLeft, 28: mouthDimpleRight, 29: mouthStretchLeft, 30: mouthStretchRight
        // 31: mouthRollLower, 32: mouthRollUpper, 33: mouthShrugLower, 34: mouthShrugUpper, 35: mouthPressLeft, 36: mouthPressRight
        // 37: mouthLowerDownLeft, 38: mouthLowerDownRight, 39: mouthUpperUpLeft, 40: mouthUpperUpRight
        // 41: browDownLeft, 42: browDownRight, 43: browInnerUp, 44: browOuterUpLeft, 45: browOuterUpRight
        // 46: cheekPuff, 47: cheekSquintLeft, 48: cheekSquintRight
        // 49: noseSneerLeft, 50: noseSneerRight, 51: tongueOut
        
        int mouthSmileLeft = 23;
        int mouthSmileRight = 24;
        int mouthFrownLeft = 25;
        int mouthFrownRight = 26;
        int mouthPressLeft = 35;
        int mouthPressRight = 36;
        int browDownLeft = 41;
        int browDownRight = 42;
        int browInnerUp = 43;
        int eyeSquintLeft = 5;
        int eyeSquintRight = 12;
        int eyeWideLeft = 6;
        int eyeWideRight = 13;
        int jawOpen = 17;
        int mouthFunnel = 19;
        int cheekPuff = 46;
        int cheekSquintLeft = 47;
        int cheekSquintRight = 48;

        if (valence > 0) {
            float smile = Math.min(1.0f, valence + arousal);
            blendshapes[mouthSmileLeft] = Math.min(1.0f, smile * (1.0f + asymmetryBias));
            blendshapes[mouthSmileRight] = Math.min(1.0f, smile * (1.0f - asymmetryBias));
            
            if (valence > 0.5f) {
                blendshapes[cheekPuff] = Math.min(1.0f, valence * 0.5f);
                blendshapes[cheekSquintLeft] = Math.min(1.0f, valence * 0.8f);
                blendshapes[cheekSquintRight] = Math.min(1.0f, valence * 0.8f);
            }
        } else {
            float frown = Math.min(1.0f, -valence + dominance);
            blendshapes[mouthFrownLeft] = Math.min(1.0f, frown * (1.0f + asymmetryBias));
            blendshapes[mouthFrownRight] = Math.min(1.0f, frown * (1.0f - asymmetryBias));
            blendshapes[mouthPressLeft] = Math.min(1.0f, frown * 0.5f);
            blendshapes[mouthPressRight] = Math.min(1.0f, frown * 0.5f);
        }

        blendshapes[browDownLeft] = Math.min(1.0f, cognitiveLoad * 0.5f + (valence < 0 ? -valence * 0.5f : 0));
        blendshapes[browDownRight] = Math.min(1.0f, cognitiveLoad * 0.5f + (valence < 0 ? -valence * 0.5f : 0));

        if (arousal > 0.5f && dominance < 0.5f) {
            blendshapes[browInnerUp] = Math.min(1.0f, arousal * 0.8f);
        }

        if (Math.abs(valence) > 0.8f || arousal > 0.8f) {
            blendshapes[eyeSquintLeft] = Math.min(1.0f, arousal * 0.6f);
            blendshapes[eyeSquintRight] = Math.min(1.0f, arousal * 0.6f);
        }

        if (arousal > 0.5f && Math.abs(valence) < 0.2f) { // surprise
            blendshapes[eyeWideLeft] = Math.min(1.0f, arousal * 0.9f);
            blendshapes[eyeWideRight] = Math.min(1.0f, arousal * 0.9f);
        }

        blendshapes[jawOpen] = Math.min(1.0f, speechActivity * 0.8f);
        blendshapes[mouthFunnel] = Math.min(1.0f, speechActivity * 0.5f);

        // Clamp using SIMD
        for (int i = 0; i < SPECIES.loopBound(blendshapes.length); i += SPECIES.length()) {
            FloatVector v = FloatVector.fromArray(SPECIES, blendshapes, i);
            v = v.max(0.0f).min(1.0f);
            v.intoArray(blendshapes, i);
        }
        for (int i = SPECIES.loopBound(blendshapes.length); i < blendshapes.length; i++) {
            blendshapes[i] = Math.max(0.0f, Math.min(1.0f, blendshapes[i]));
        }

        return blendshapes;
    }

    public static float[] computeGazeVector(float valence, float arousal, float dominance, float cognitiveLoad) {
        float[] gaze = new float[3];
        if (cognitiveLoad > 0.6f) {
            gaze[0] = 15.0f * cognitiveLoad; // pitch up
            gaze[1] = -10.0f * cognitiveLoad; // yaw left
            gaze[2] = 0.0f;
        } else if (dominance > 0.5f) {
            gaze[0] = 0.0f;
            gaze[1] = 0.0f;
            gaze[2] = 0.0f;
        } else {
            gaze[0] = -5.0f * (1.0f - dominance);
            gaze[1] = 5.0f * arousal;
            gaze[2] = 0.0f;
        }
        return gaze;
    }

    public static float[] computeHeadPose(float valence, float arousal, float dominance, float noddingCadence) {
        float[] head = new float[3];
        head[0] = noddingCadence * 10.0f; // pitch
        head[1] = arousal * 5.0f; // yaw
        head[2] = dominance > 0.5f ? 0.0f : 2.0f; // roll
        return head;
    }
}
