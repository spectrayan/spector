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
package com.spectrayan.spector.kernel.util;

/**
 * Pure-Java xxHash64 implementation.
 *
 * @deprecated since 0.1.0-beta, forRemoval = true. Use {@link com.spectrayan.spector.core.math.XxHash64}.
 */
@Deprecated(since = "0.1.0-beta", forRemoval = true)
public final class XxHash64 {

    private XxHash64() {
        // Prevent instantiation
    }

    /**
     * Computes the 64-bit hash of the given byte array.
     *
     * @param input the input bytes
     * @return the computed 64-bit hash
     */
    public static long hash(byte[] input) {
        return com.spectrayan.spector.core.math.XxHash64.hash(input);
    }
}
