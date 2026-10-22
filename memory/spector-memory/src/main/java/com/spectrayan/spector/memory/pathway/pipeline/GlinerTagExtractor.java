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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorException;
import com.spectrayan.spector.commons.error.SpectorMemoryException;
import com.spectrayan.spector.provider.extraction.SpanExtractor;
import com.spectrayan.spector.provider.extraction.SpanMention;

/**
 * Tags are the surface forms of GLiNER spans, normalized the same way as the
 * content tagger. GLiNER does not appraise valence or arousal. A failed inference
 * fails the write.
 */
public final class GlinerTagExtractor implements TagExtractor {

    private static final Pattern TAG_CLEAN = Pattern.compile("[^a-z0-9_-]");

    private final SpanExtractor spans;
    private final List<String> labels;

    /**
     * @param spans  shared span session
     * @param labels prompt labels
     */
    public GlinerTagExtractor(SpanExtractor spans, List<String> labels) {
        this.spans = spans;
        this.labels = List.copyOf(labels);
    }

    @Override
    public String[] extract(String id, String text) {
        List<SpanMention> mentions;
        try {
            mentions = spans.extract(text, labels);
        } catch (SpectorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_EXTRACTION_FAILED, id,
                    e.getClass().getSimpleName());
        }
        Set<String> tags = new LinkedHashSet<>();
        for (SpanMention mention : mentions) {
            String clean = TAG_CLEAN.matcher(mention.text().toLowerCase(Locale.ROOT)).replaceAll("");
            if (clean.length() >= 2 && !clean.chars().allMatch(Character::isDigit)) {
                tags.add(clean);
            }
        }
        return tags.toArray(String[]::new);
    }
}
