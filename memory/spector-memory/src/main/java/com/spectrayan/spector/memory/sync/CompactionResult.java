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
package com.spectrayan.spector.memory.sync;

import com.spectrayan.spector.kernel.api.MemoryType;

/**
 * Result of a vacuum/compaction operation on a tier store.
 *
 * @param tier               the memory tier that was compacted
 * @param beforeCount        total records before compaction (live + tombstoned)
 * @param afterCount         live records after compaction
 * @param tombstonesRemoved  number of tombstoned records removed
 * @param bytesReclaimed     bytes freed by removing tombstoned records
 * @param durationMs         compaction duration in milliseconds
 */
public record CompactionResult(
    MemoryType tier,
    int beforeCount,
    int afterCount,
    int tombstonesRemoved,
    long bytesReclaimed,
    long durationMs
) {}
