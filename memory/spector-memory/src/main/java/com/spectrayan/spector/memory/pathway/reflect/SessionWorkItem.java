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
package com.spectrayan.spector.memory.pathway.reflect;

import java.util.List;
import java.util.Objects;

/**
 * Immutable work item representing an episodic session to be consolidated.
 *
 * @param partitionSeq the sequence ID of the partition containing this session
 * @param sessionId    the 8B TSID hash identifying the episodic session
 * @param offsets      unmodifiable ordered list of byte offsets for unconsolidated turns in this session
 * @param timestampMs  the session watermark timestamp (e.g. latest turn timestamp in the session)
 * @since 1.5.0
 */
public record SessionWorkItem(
        int partitionSeq,
        long sessionId,
        List<Long> offsets,
        long timestampMs
) {
    public SessionWorkItem {
        offsets = (offsets != null) ? List.copyOf(offsets) : List.of();
    }
}
