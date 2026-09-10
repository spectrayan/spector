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
package com.spectrayan.spector.kernel.engram;

/**
 * Versioned 64-bit packed representation of primary engram header and strength fields.
 *
 * <p>Used for fused scoring across memory slabs without object allocation (R7.4).</p>
 *
 * <h3>Bit Allocation (64 bits, Version 1)</h3>
 * <pre>
 *   Bits 00..07 (8B):  flags (tombstone, consolidated, pinned, resolved, modality)
 *   Bits 08..15 (8B):  valence (signed int8, -128..127)
 *   Bits 16..23 (8B):  arousal (unsigned uint8, 0..255)
 *   Bits 24..31 (8B):  agentRecallCount (unsigned uint8 clamped, 0..255)
 *   Bits 32..47 (16B): importance (quantized float in [0.0, 10.0], 16-bit integer scale 6553.5)
 *   Bits 48..55 (8B):  storageStrength (quantized float in [1.0, 5.0], 8-bit scale 60.0)
 *   Bits 56..59 (4B):  version (always 1)
 *   Bits 60..63 (4B):  memoryTypeOrdinal (0..3)
 * </pre>
 */
public final class HeaderBits {

    public static final int VERSION = 1;

    private static final long MASK_8 = 0xFFL;
    private static final long MASK_16 = 0xFFFFL;
    private static final long MASK_4 = 0x0FL;

    private static final int SHIFT_FLAGS = 0;
    private static final int SHIFT_VALENCE = 8;
    private static final int SHIFT_AROUSAL = 16;
    private static final int SHIFT_RECALL_COUNT = 24;
    private static final int SHIFT_IMPORTANCE = 32;
    private static final int SHIFT_STORAGE = 48;
    private static final int SHIFT_VERSION = 56;
    private static final int SHIFT_TYPE = 60;

    private static final float IMPORTANCE_SCALE = 6553.5f; // maps 0.0..10.0 to 0..65535
    private static final float STORAGE_SCALE = 60.0f;      // maps 1.0..5.0 to 0..240

    private HeaderBits() {}

    public static long pack(
            byte flags, byte valence, byte arousal, int agentRecallCount,
            float importance, float storageStrength, int typeOrdinal) {
        return pack(VERSION, flags, valence, arousal, agentRecallCount, importance, storageStrength, typeOrdinal);
    }

    public static long pack(
            int version, byte flags, byte valence, byte arousal, int agentRecallCount,
            float importance, float storageStrength, int typeOrdinal) {
        long f = (flags & MASK_8) << SHIFT_FLAGS;
        long v = (valence & MASK_8) << SHIFT_VALENCE;
        long a = (arousal & MASK_8) << SHIFT_AROUSAL;
        long rc = (Math.min(255, Math.max(0, agentRecallCount)) & MASK_8) << SHIFT_RECALL_COUNT;

        int qImp = (int) Math.round(Math.max(0.0f, Math.min(10.0f, importance)) * IMPORTANCE_SCALE);
        long imp = (qImp & MASK_16) << SHIFT_IMPORTANCE;

        float clampedS = Math.max(1.0f, Math.min(5.0f, storageStrength));
        int qStorage = (int) Math.round((clampedS - 1.0f) * STORAGE_SCALE);
        long st = (qStorage & MASK_8) << SHIFT_STORAGE;

        long ver = (version & MASK_4) << SHIFT_VERSION;
        long typ = (typeOrdinal & MASK_4) << SHIFT_TYPE;

        return f | v | a | rc | imp | st | ver | typ;
    }

    public static int version(long bits) {
        return (int) ((bits >>> SHIFT_VERSION) & MASK_4);
    }

    public static void checkVersion(long bits) {
        int v = version(bits);
        if (v != VERSION) {
            throw new IllegalArgumentException("Unsupported HeaderBits version: " + v + " (expected: " + VERSION + ")");
        }
    }

    public static byte flags(long bits) {
        return (byte) ((bits >>> SHIFT_FLAGS) & MASK_8);
    }

    public static byte valence(long bits) {
        return (byte) ((bits >>> SHIFT_VALENCE) & MASK_8);
    }

    public static byte arousal(long bits) {
        return (byte) ((bits >>> SHIFT_AROUSAL) & MASK_8);
    }

    public static int agentRecallCount(long bits) {
        return (int) ((bits >>> SHIFT_RECALL_COUNT) & MASK_8);
    }

    public static float importance(long bits) {
        int qImp = (int) ((bits >>> SHIFT_IMPORTANCE) & MASK_16);
        return qImp / IMPORTANCE_SCALE;
    }

    public static float storageStrength(long bits) {
        int qStorage = (int) ((bits >>> SHIFT_STORAGE) & MASK_8);
        return 1.0f + (qStorage / STORAGE_SCALE);
    }

    public static int typeOrdinal(long bits) {
        return (int) ((bits >>> SHIFT_TYPE) & MASK_4);
    }
}
