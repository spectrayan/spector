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
import java.util.Locale;

import com.spectrayan.spector.provider.extraction.SpanMention;

/**
 * Phrase links between spans from a model that has no relation head.
 * {@code gliner_small-v2.1} scores entity labels only. A relation is recorded
 * when the text between two mentions contains a known phrase, or when the gap
 * is {@code in} and the later mention is a location.
 */
final class SpanRelationLinker {

    private SpanRelationLinker() {
    }

    static List<List<EntityRelation>> link(String text, List<SpanMention> mentions) {
        List<List<EntityRelation>> relations = new ArrayList<>(mentions.size());
        for (int i = 0; i < mentions.size(); i++) {
            relations.add(new ArrayList<>());
        }
        if (text == null) {
            return relations;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i + 1 < mentions.size(); i++) {
            SpanMention left = mentions.get(i);
            SpanMention right = mentions.get(i + 1);
            if (right.start() < left.end()) {
                continue;
            }
            String gap = lower.substring(Math.min(left.end(), lower.length()),
                    Math.min(right.start(), lower.length()));
            String type = relation(gap, right.label());
            if (type != null) {
                relations.get(i).add(new EntityRelation(right.text(), type));
            }
        }
        return relations;
    }

    private static String relation(String gap, String targetLabel) {
        if (contains(gap, "works at") || contains(gap, "works for") || contains(gap, "employed at")
                || contains(gap, "employed by")) {
            return "MEMBER_OF";
        }
        if (contains(gap, "located in") || contains(gap, "lives in") || contains(gap, "based in")) {
            return "LOCATED_IN";
        }
        if (contains(gap, "owns") || contains(gap, "owned by")) {
            return "OWNS";
        }
        if (gap.trim().equals("in") && targetLabel != null
                && targetLabel.toLowerCase(Locale.ROOT).contains("location")) {
            return "LOCATED_IN";
        }
        return null;
    }

    private static boolean contains(String gap, String phrase) {
        return gap.contains(phrase);
    }
}
