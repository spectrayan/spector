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
package com.spectrayan.spector.memory.pipeline;

/**
 * Result of tag extraction that optionally includes emotional context (valence/arousal).
 *
 * <p>When the tag extractor is LLM-powered, it can also assess the emotional tone
 * of the content alongside the tags. This avoids a separate LLM call for sentiment
 * analysis — the tag extraction prompt is extended to include valence/arousal.</p>
 *
 * @param tags    extracted synaptic tag strings
 * @param valence emotional valence: -128 (extremely negative) to +127 (extremely positive), 0 = neutral
 * @param arousal emotional intensity: 0 (calm) to 255 (extreme), stored as signed byte. 0 = neutral
 */
public record TagExtractionResult(String[] tags, byte valence, byte arousal) {

    /** Creates a tags-only result with neutral emotional context. */
    public static TagExtractionResult tagsOnly(String[] tags) {
        return new TagExtractionResult(tags, (byte) 0, (byte) 0);
    }

    /** Returns true if emotional context (valence or arousal) was provided. */
    public boolean hasEmotionalContext() {
        return valence != 0 || arousal != 0;
    }
}
