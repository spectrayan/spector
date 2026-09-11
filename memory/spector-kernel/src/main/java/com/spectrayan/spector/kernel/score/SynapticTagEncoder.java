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
package com.spectrayan.spector.kernel.score;

import com.spectrayan.spector.core.cognitive.SynapticTagMath;

import java.util.Collection;

/**
 * 64-bit inline Bloom filter encoder for synaptic tags (R17.1).
 *
 * @deprecated since 0.1.0-beta, forRemoval = true. Use {@link SynapticTagMath}.
 */
@Deprecated(since = "0.1.0-beta", forRemoval = true)
public final class SynapticTagEncoder {

    private SynapticTagEncoder() {}

    public static long encode(String... tags) {
        return SynapticTagMath.encode(tags);
    }

    public static long encode(Collection<String> tags) {
        return SynapticTagMath.encode(tags);
    }

    public static long encodeTag(String tag) {
        return SynapticTagMath.encodeTag(tag);
    }

    public static boolean matches(long recordFilter, long queryMask) {
        return SynapticTagMath.matches(recordFilter, queryMask);
    }

    public static boolean matches(long recordFilter, String tag) {
        return SynapticTagMath.matches(recordFilter, tag);
    }

    public static float overlapRatio(long recordTags, long queryTags) {
        return SynapticTagMath.overlapRatio(recordTags, queryTags);
    }

    public static long merge(long a, long b) {
        return SynapticTagMath.merge(a, b);
    }

    public static int bitCount(long filter) {
        return SynapticTagMath.bitCount(filter);
    }

    public static int popcount(long filter) {
        return SynapticTagMath.popcount(filter);
    }

    public static double falsePositiveProbability(int numTags) {
        return SynapticTagMath.falsePositiveProbability(numTags);
    }
}
