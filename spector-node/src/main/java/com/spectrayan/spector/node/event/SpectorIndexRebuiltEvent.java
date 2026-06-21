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
package com.spectrayan.spector.node.event;

import java.time.Instant;

/** Fired when the search index is rebuilt or optimized (e.g., HNSW re-indexing). */
public record SpectorIndexRebuiltEvent(
        String nodeId, Instant timestamp,
        String indexType, long documentCount, long rebuildTimeMs
) implements SpectorNodeEvent {
    @Override public String eventType() { return "engine.index_rebuilt"; }
}
