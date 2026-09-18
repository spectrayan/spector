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
package com.spectrayan.spector.memory.synapse;

import com.spectrayan.spector.kernel.id.MemoryId;

import java.util.List;

/**
 * Context container for query-time associative prior resolution (MR-06).
 *
 * <p>Computed once per query to allow O(1) hash probes during Phase 6 score fusion.</p>
 *
 * @param contextMemoryIds recent working-memory window or explicit context memory IDs
 * @param queryTags        resolved query synaptic tags
 * @param queryTagMask     query synaptic tag bitmask
 */
public record QueryAssociativeContext(
        List<String> contextMemoryIds,
        List<String> queryTags,
        long queryTagMask
) {
    public static final QueryAssociativeContext EMPTY =
            new QueryAssociativeContext(List.of(), List.of(), 0L);
}
