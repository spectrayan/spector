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
package com.spectrayan.spector.provider.extraction;

import java.util.List;

/**
 * Zero-shot span tagger. Implementations must not log the text they score.
 */
public interface SpanExtractor extends AutoCloseable {

    /**
     * Scores {@code text} against {@code labels}.
     *
     * @param text   source text; {@code null} is empty
     * @param labels prompt labels in class order; {@code null} uses the labels from the spec
     * @return non-overlapping mentions, highest score first within a region, then by start
     */
    List<SpanMention> extract(String text, List<String> labels);

    @Override
    void close();
}
