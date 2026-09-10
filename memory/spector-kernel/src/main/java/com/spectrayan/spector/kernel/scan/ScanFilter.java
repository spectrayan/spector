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

/**
 * Primitive-only gate inputs for candidate pre-screening (Phases 1-4).
 *
 * <p>Contains no cognitive types, no config objects, and no callbacks (R7.1a).</p>
 */
public record ScanFilter(
        long tagMaskLo, long tagMaskHi,
        long hyperfocusMaskLo, long hyperfocusMaskHi,
        long minTimestampMs, long maxTimestampMs,
        long nowMs,
        boolean allowFuture,
        byte minValence, byte maxValence,
        float minImportance,
        byte requiredFlags, byte excludedFlags,
        boolean includeContradictions, boolean allowSimulated,
        int staleBucketThreshold,
        float weakMassThreshold
) {
    public static final ScanFilter NONE = new ScanFilter(
            0L, 0L,
            0L, 0L,
            0L, Long.MAX_VALUE,
            0L,
            true,
            Byte.MIN_VALUE, Byte.MAX_VALUE,
            0.0f,
            (byte) 0, (byte) 0,
            true, true,
            12, 0.0f
    );

    public ScanFilter(
            long tagMaskLo, long tagMaskHi,
            long hyperfocusMaskLo, long hyperfocusMaskHi,
            long minTimestampMs, long maxTimestampMs,
            boolean allowFuture,
            byte minValence, byte maxValence,
            float minImportance,
            byte requiredFlags, byte excludedFlags,
            boolean includeContradictions, boolean allowSimulated,
            int staleBucketThreshold,
            float weakMassThreshold
    ) {
        this(tagMaskLo, tagMaskHi, hyperfocusMaskLo, hyperfocusMaskHi,
                minTimestampMs, maxTimestampMs, 0L, allowFuture,
                minValence, maxValence, minImportance,
                requiredFlags, excludedFlags, includeContradictions, allowSimulated,
                staleBucketThreshold, weakMassThreshold);
    }
}
