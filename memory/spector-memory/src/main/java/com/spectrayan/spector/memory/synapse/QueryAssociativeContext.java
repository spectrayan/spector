/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.synapse;

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
