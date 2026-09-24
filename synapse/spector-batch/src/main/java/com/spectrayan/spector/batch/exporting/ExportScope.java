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
package com.spectrayan.spector.batch.exporting;

import com.spectrayan.spector.kernel.api.MemoryType;

/**
 * Filter and chunk-sizing parameters defining an export operation's scope
 * (ADR-0045, memory-portability R1.7).
 *
 * @param namespace         target namespace to export
 * @param tier              optional tier filter (null for all tiers)
 * @param createdFrom       optional lower bound creation timestamp in epoch milliseconds (inclusive)
 * @param createdTo         optional upper bound creation timestamp in epoch milliseconds (inclusive)
 * @param includeTombstones whether to export tombstoned memories (default false)
 * @param recordsPerChunk   fixed record count per chunk file (default 1000)
 */
public record ExportScope(
        String namespace,
        MemoryType tier,
        Long createdFrom,
        Long createdTo,
        boolean includeTombstones,
        int recordsPerChunk
) {
    public static final int DEFAULT_RECORDS_PER_CHUNK = 1000;

    public ExportScope {
        if (namespace == null || namespace.isBlank()) {
            namespace = "default";
        }
        if (recordsPerChunk <= 0) {
            recordsPerChunk = DEFAULT_RECORDS_PER_CHUNK;
        }
    }

    public static ExportScope all(String namespace) {
        return new ExportScope(namespace, null, null, null, false, DEFAULT_RECORDS_PER_CHUNK);
    }
}
