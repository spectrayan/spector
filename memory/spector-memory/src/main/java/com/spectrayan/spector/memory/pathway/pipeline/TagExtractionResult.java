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
package com.spectrayan.spector.memory.pathway.pipeline;

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
