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
package com.spectrayan.spector.memory.aisme.hopfield;

/**
 * Mathematical kernel formulation for Modern Associative Memory Networks.
 *
 * @since 1.3.0
 */
public enum KernelType {
    /**
     * Standard Log-Sum-Exp (LSE) Gaussian kernel (Ramsauer et al., 2021).
     * Infinite support, asymptotic iterative convergence.
     */
    LSE,

    /**
     * Statistically optimal Log-Sum-ReLU (LSR) Epanechnikov kernel (Hoover et al., 2025).
     * Compact finite support, exact single-step (T=1) pattern retrieval, zero transcendental CPU calls.
     */
    LSR
}
