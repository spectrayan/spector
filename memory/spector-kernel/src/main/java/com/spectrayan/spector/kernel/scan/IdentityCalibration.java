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
package com.spectrayan.spector.kernel.scan;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flyweight factory for identity scalar quantization calibration arrays.
 */
public final class IdentityCalibration {

    private static final ConcurrentHashMap<Integer, float[]> MINS_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, float[]> SCALES_CACHE = new ConcurrentHashMap<>();

    private IdentityCalibration() {}

    /**
     * Returns a cached identity minimum array for the given dimension count.
     * All values are -1.0f.
     */
    public static float[] mins(int dims) {
        return MINS_CACHE.computeIfAbsent(dims, d -> {
            float[] mins = new float[d];
            Arrays.fill(mins, -1.0f);
            return mins;
        });
    }

    /**
     * Returns a cached identity scale array for the given dimension count.
     * All values are 2.0/255.
     */
    public static float[] scales(int dims) {
        return SCALES_CACHE.computeIfAbsent(dims, d -> {
            float[] scales = new float[d];
            Arrays.fill(scales, 2.0f / 255.0f);
            return scales;
        });
    }
}
