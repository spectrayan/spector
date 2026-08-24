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
