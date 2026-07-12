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
package com.spectrayan.spector.node.api.dto;

/**
 * A node in the memory graph response.
 *
 * <p>Represents a single cognitive memory in the graph visualization,
 * with enough metadata for rendering (tier color, node size by importance,
 * tooltip text, temporal position).</p>
 *
 * @param id          unique memory identifier
 * @param tier        cognitive tier (WORKING, EPISODIC, SEMANTIC, PROCEDURAL)
 * @param textPreview truncated text content (max 120 chars)
 * @param importance  importance score (0.0 to 1.0) — controls node size
 * @param valence     emotional valence (-128 to 127) — controls node hue shift
 * @param timestampMs creation timestamp (epoch millis) — controls temporal filtering
 */
public record GraphNodeDto(
    String id,
    String tier,
    String textPreview,
    float importance,
    int valence,
    long timestampMs
) {}

