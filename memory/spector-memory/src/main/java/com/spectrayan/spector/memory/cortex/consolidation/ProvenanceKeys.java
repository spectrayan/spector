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
package com.spectrayan.spector.memory.cortex.consolidation;

import com.spectrayan.spector.kernel.id.TsidGenerator;

import java.nio.charset.StandardCharsets;

/**
 * Maps memory IDs to the 64-bit keys stored in provenance rows ({@code target_tsid},
 * {@code source_tsid}).
 *
 * <p>TSID-shaped IDs, optionally with a short prefix such as {@code cns-}, map to their decoded
 * TSID, so keys match the rows written by episodic consolidation and skill lineage. Any other ID
 * maps to a stable 64-bit FNV-1a hash of its UTF-8 bytes. Zero is never returned, since a zero
 * {@code source_tsid} means "no memory source".</p>
 */
public final class ProvenanceKeys {

    private static final int TSID_LENGTH = 13;
    private static final int MAX_PREFIX_LENGTH = 8;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private ProvenanceKeys() {
    }

    /**
     * Returns the provenance key for a memory ID.
     *
     * @param memoryId the memory ID (must not be null)
     * @return a non-zero 64-bit key
     */
    public static long of(String memoryId) {
        String core = memoryId;
        int dash = memoryId.indexOf('-');
        if (dash > 0 && dash <= MAX_PREFIX_LENGTH && memoryId.length() - dash - 1 == TSID_LENGTH) {
            core = memoryId.substring(dash + 1);
        }
        if (core.length() == TSID_LENGTH) {
            try {
                long tsid = TsidGenerator.decodeCrockford(core);
                if (tsid != 0L) {
                    return tsid;
                }
            } catch (RuntimeException notATsid) {
                // fall through to the hash
            }
        }
        return hash(memoryId);
    }

    private static long hash(String memoryId) {
        long h = FNV_OFFSET_BASIS;
        for (byte b : memoryId.getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xff);
            h *= FNV_PRIME;
        }
        return h == 0L ? 1L : h;
    }
}
