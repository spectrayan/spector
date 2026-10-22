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
package com.spectrayan.spector.memory.graph;

import java.util.ArrayList;
import java.util.List;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorException;
import com.spectrayan.spector.commons.error.SpectorMemoryException;
import com.spectrayan.spector.provider.extraction.SpanExtractor;
import com.spectrayan.spector.provider.extraction.SpanMention;

/**
 * Entities and phrase-linked relations from a GLiNER span model.
 * An inference failure is {@link ErrorCode#MEMORY_EXTRACTION_FAILED}; there is no
 * dictionary fallback. The memory text is not included in the exception.
 */
public final class GlinerEntityExtractor implements EntityExtractor {

    private final SpanExtractor spans;
    private final List<String> labels;
    private final int maxEntities;

    /**
     * @param spans       shared span session
     * @param labels      prompt labels
     * @param maxEntities cap; zero or negative keeps every mention
     */
    public GlinerEntityExtractor(SpanExtractor spans, List<String> labels, int maxEntities) {
        this.spans = spans;
        this.labels = List.copyOf(labels);
        this.maxEntities = maxEntities;
    }

    @Override
    public List<ExtractedEntity> extract(String id, String text) {
        List<SpanMention> mentions;
        try {
            mentions = spans.extract(text, labels);
        } catch (SpectorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_EXTRACTION_FAILED, id,
                    e.getClass().getSimpleName());
        }
        if (maxEntities > 0 && mentions.size() > maxEntities) {
            mentions = mentions.subList(0, maxEntities);
        }
        List<List<EntityRelation>> relations = SpanRelationLinker.link(text, mentions);
        List<ExtractedEntity> entities = new ArrayList<>(mentions.size());
        for (int i = 0; i < mentions.size(); i++) {
            SpanMention mention = mentions.get(i);
            entities.add(new ExtractedEntity(mention.text(), mention.label(), List.copyOf(relations.get(i))));
        }
        return entities;
    }
}
