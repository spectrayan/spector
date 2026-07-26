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
package com.spectrayan.spector.hdc;

import java.util.Objects;

/**
 * Deterministic seed vector generation.
 */
public final class HypervectorFactory {

    private HypervectorFactory() {
        // Utility class
    }

    /**
     * Generates a deterministic random binary hypervector from a token's hashCode as seed.
     *
     * @param token The token string.
     * @param dimensions The number of dimensions.
     * @return A deterministic random hypervector.
     */
    public static Hypervector seedFor(String token, int dimensions) {
        Objects.requireNonNull(token, "token cannot be null");
        return Hypervector.random(dimensions, token.hashCode());
    }
}
