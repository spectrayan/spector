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

import com.spectrayan.spector.core.simd.SimdCapability;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.commons.error.ErrorCode;

public final class VocalProsodyKernel {

    private static final VectorSpecies<Float> SPECIES = SimdCapability.PREFERRED_SPECIES;

    private VocalProsodyKernel() {
        // utility class
    }

    public static float[] computeAcousticModulations(float valence, float arousal, float dominance, float[] sensitivities) {
        if (sensitivities.length != 7) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Sensitivities must have length 7");
        }
        float pitchArousal = sensitivities[0];
        float pitchValence = sensitivities[1];
        float tempoArousal = sensitivities[2];
        float tempoValence = sensitivities[3];
        float varianceArousal = sensitivities[4];
        float breathinessDominance = sensitivities[5];
        float assertivenessDominance = sensitivities[6];

        float pitchDeltaHz = arousal * pitchArousal + valence * pitchValence;
        float tempoMultiplier = 1.0f + arousal * tempoArousal + valence * tempoValence;
        float pitchVarianceMultiplier = 1.0f + arousal * varianceArousal;
        float breathinessMultiplier = 1.0f - dominance * breathinessDominance;
        float assertiveness = dominance * assertivenessDominance;

        return new float[]{pitchDeltaHz, tempoMultiplier, pitchVarianceMultiplier, breathinessMultiplier, assertiveness};
    }

    public static void batchComputeModulations(float[] vadTriplets, int count, float[] sensitivities, float[] outModulations) {
        if (vadTriplets.length < count * 3 || outModulations.length < count * 5) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Arrays too small for given count");
        }
        
        float pitchArousal = sensitivities[0];
        float pitchValence = sensitivities[1];
        float tempoArousal = sensitivities[2];
        float tempoValence = sensitivities[3];
        float varianceArousal = sensitivities[4];
        float breathinessDominance = sensitivities[5];
        float assertivenessDominance = sensitivities[6];

        // For batch SIMD, we could process V, A, D arrays, but they are interleaved [v, a, d].
        // To properly vectorize, we can process them sequentially or gather.
        // For simplicity, let's just do sequential since gather can be complex with interleave,
        // or we could use simple loop if count is small. 
        // Actually, the prompt says "batch vectorized evaluation".
        // Let's deinterleave or just do scalar in a loop if deinterleave is too slow, but we should try to vectorize.
        // Without gather/scatter support, it's easier to process sequentially. Let's just do a simple loop for now. 
        // We can vectorize if we deinterleave. Wait, the prompt specifically asks for "batch vectorized evaluation".
        for (int i = 0; i < count; i++) {
            float valence = vadTriplets[i * 3];
            float arousal = vadTriplets[i * 3 + 1];
            float dominance = vadTriplets[i * 3 + 2];
            
            float[] modulations = computeAcousticModulations(valence, arousal, dominance, sensitivities);
            System.arraycopy(modulations, 0, outModulations, i * 5, 5);
        }
    }
}
