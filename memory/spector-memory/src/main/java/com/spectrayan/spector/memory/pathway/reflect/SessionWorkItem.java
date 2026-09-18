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
