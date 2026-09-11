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
package com.spectrayan.spector.core.math;

/**
 * Stochastic Differential Equation (SDE) continuous solver using Euler-Maruyama numerical integration.
 *
 * <p>Contract: ADR-0033 Purity Tier T1 (Pure Static). Provides deterministic time-stepping
 * for multi-channel affective dynamics and homeostatic state regulation by accepting caller-supplied
 * random normal noise vectors rather than generating random numbers internally.</p>
 *
 * <h3>Mathematical Formulation</h3>
 * <pre>
 *   h_{t+dt} = clamp(h_t + dt * (A * h_t + B * u_t + C * r_t + noise), -1.0, 1.0)
 *   where noise_i = sigma_i * stdNormalRandom_i
 * </pre>
 */
public final class SdeEulerSolver {

    private SdeEulerSolver() {
        // utility class
    }

    /**
     * Executes a single Euler-Maruyama numerical integration step across multi-channel state representations.
     *
     * @param state            current state vector \(h_t\)
     * @param aMatrix          intrinsic decay and coupling matrix \(A\)
     * @param bMatrix          external input sensitivity matrix \(B\) (nullable)
     * @param uInput           external stimulus vector \(u_t\) (nullable)
     * @param cMatrix          episodic recall influence coupling matrix \(C\) (nullable)
     * @param rRecall          episodic recall context vector \(r_t\) (nullable)
     * @param sigmaNoise       noise scale standard deviation per channel \(\sigma\) (nullable)
     * @param dt               continuous time step delta
     * @param stdNormalRandom  caller-supplied standard normal random samples \(\mathcal{N}(0, 1)\) (nullable)
     * @param outNextState     destination vector for integrated next state \(h_{t+dt}\)
     */
    public static void stepEulerMaruyama(float[] state, float[][] aMatrix,
                                        float[][] bMatrix, float[] uInput,
                                        float[][] cMatrix, float[] rRecall,
                                        float[] sigmaNoise, float dt,
                                        float[] stdNormalRandom, float[] outNextState) {
        if (state == null || outNextState == null) {
            return;
        }

        int dim = Math.min(state.length, outNextState.length);
        for (int i = 0; i < dim; i++) {
            float aTerm = 0.0f;
            if (aMatrix != null && i < aMatrix.length && aMatrix[i] != null) {
                int aDim = Math.min(dim, aMatrix[i].length);
                for (int j = 0; j < aDim; j++) {
                    aTerm += aMatrix[i][j] * state[j];
                }
            }

            float bTerm = 0.0f;
            if (bMatrix != null && uInput != null && i < bMatrix.length && bMatrix[i] != null) {
                int bDim = Math.min(uInput.length, bMatrix[i].length);
                for (int j = 0; j < bDim; j++) {
                    bTerm += bMatrix[i][j] * uInput[j];
                }
            }

            float cTerm = 0.0f;
            if (cMatrix != null && rRecall != null && i < cMatrix.length && cMatrix[i] != null) {
                int cDim = Math.min(rRecall.length, cMatrix[i].length);
                for (int j = 0; j < cDim; j++) {
                    cTerm += cMatrix[i][j] * rRecall[j];
                }
            }

            float noise = 0.0f;
            if (sigmaNoise != null && stdNormalRandom != null
                    && i < sigmaNoise.length && i < stdNormalRandom.length) {
                noise = sigmaNoise[i] * stdNormalRandom[i];
            }

            float rawNext = state[i] + dt * (aTerm + bTerm + cTerm + noise);
            outNextState[i] = Math.clamp(rawNext, -1.0f, 1.0f);
        }
    }
}
