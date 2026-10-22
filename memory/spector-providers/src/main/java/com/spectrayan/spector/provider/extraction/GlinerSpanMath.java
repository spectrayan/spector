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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Whitespace splitting, word masks, span indexes and greedy decode for a GLiNER
 * uni-encoder. The word mask stores a 1-based text-word index on the first subtoken
 * of each text word and zero everywhere else, including the label prompt.
 */
public final class GlinerSpanMath {

    /** A whitespace-delimited word and its character span in the source. */
    public record Word(String text, int start, int end) {
    }

    private GlinerSpanMath() {
    }

    /**
     * Splits on Unicode whitespace, matching Python {@code str.split()}.
     *
     * @param text source; {@code null} is empty
     * @return words in order
     */
    public static List<Word> words(String text) {
        List<Word> words = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return words;
        }
        int i = 0;
        int n = text.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i >= n) {
                break;
            }
            int start = i;
            while (i < n && !Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            words.add(new Word(text.substring(start, i), start, i));
        }
        return words;
    }

    /**
     * Builds the GLiNER {@code words_mask}. {@code wordIds} uses a negative value for
     * special tokens, the same way the tokenizer reports "no word".
     *
     * @param wordIds     tokenizer word ids, one per token
     * @param promptWords number of prompt words before the text ({@code <<ENT>>}, label, {@code <<SEP>>})
     * @return mask of the same length; text words are 1-based
     */
    public static long[] wordsMask(long[] wordIds, int promptWords) {
        long[] mask = new long[wordIds.length];
        int wordsCount = 0;
        Long prev = null;
        for (int i = 0; i < wordIds.length; i++) {
            long wordId = wordIds[i];
            if (wordId < 0) {
                prev = null;
                continue;
            }
            if (prev == null || wordId != prev) {
                if (wordsCount >= promptWords) {
                    mask[i] = wordId - promptWords + 1;
                }
                wordsCount++;
                prev = wordId;
            }
        }
        return mask;
    }

    /**
     * Every inclusive {@code (start, start + width)} pair, width in {@code [0, maxWidth)}.
     * Pairs that run past the last word stay in the array so the tensor width is fixed;
     * {@link #spanMask} marks them false.
     *
     * @param wordCount text words
     * @param maxWidth  model span width
     * @return {@code [wordCount * maxWidth][2]}
     */
    public static long[][] spanIndex(int wordCount, int maxWidth) {
        long[][] index = new long[wordCount * maxWidth][2];
        int slot = 0;
        for (int start = 0; start < wordCount; start++) {
            for (int width = 0; width < maxWidth; width++) {
                index[slot][0] = start;
                index[slot][1] = start + width;
                slot++;
            }
        }
        return index;
    }

    /**
     * @param wordCount text words
     * @param maxWidth  model span width
     * @return true when the inclusive end word is inside the text
     */
    public static boolean[] spanMask(int wordCount, int maxWidth) {
        boolean[] mask = new boolean[wordCount * maxWidth];
        int slot = 0;
        for (int start = 0; start < wordCount; start++) {
            for (int width = 0; width < maxWidth; width++) {
                mask[slot++] = start + width < wordCount;
            }
        }
        return mask;
    }

    /**
     * Sigmoid, threshold, then greedy non-overlapping selection. Longer and shorter
     * spans that share a word lose to the higher score.
     *
     * @param logits    {@code [word][width][class]} raw scores
     * @param text      original text, used to slice the surface form
     * @param words     whitespace words of {@code text}
     * @param labels    class names, index-aligned with the last logit axis
     * @param threshold minimum probability
     * @return mentions ordered by start
     */
    public static List<SpanMention> decode(float[][][] logits, String text, List<Word> words,
                                            List<String> labels, float threshold) {
        if (logits.length == 0 || words.isEmpty() || labels.isEmpty()) {
            return List.of();
        }
        int wordCount = Math.min(words.size(), logits.length);
        int classes = Math.min(labels.size(), logits[0][0].length);
        List<Hit> hits = new ArrayList<>();
        for (int start = 0; start < wordCount; start++) {
            int widths = logits[start].length;
            for (int width = 0; width < widths; width++) {
                int end = start + width;
                if (end >= wordCount) {
                    continue;
                }
                for (int label = 0; label < classes; label++) {
                    float probability = sigmoid(logits[start][width][label]);
                    if (probability >= threshold) {
                        hits.add(new Hit(start, end, label, probability));
                    }
                }
            }
        }
        hits.sort(Comparator.comparingDouble((Hit hit) -> -hit.score)
                .thenComparingInt(hit -> hit.start)
                .thenComparingInt(hit -> hit.end));
        List<Hit> kept = new ArrayList<>();
        for (Hit hit : hits) {
            boolean overlaps = false;
            for (Hit chosen : kept) {
                if (hit.start <= chosen.end && chosen.start <= hit.end) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                kept.add(hit);
            }
        }
        kept.sort(Comparator.comparingInt(hit -> hit.start));
        List<SpanMention> mentions = new ArrayList<>(kept.size());
        String source = text == null ? "" : text;
        for (Hit hit : kept) {
            Word first = words.get(hit.start);
            Word last = words.get(hit.end);
            String surface = source.substring(first.start, last.end);
            mentions.add(new SpanMention(surface, labels.get(hit.label), first.start, last.end, hit.score));
        }
        return mentions;
    }

    static float sigmoid(float value) {
        if (value >= 20f) {
            return 1f;
        }
        if (value <= -20f) {
            return 0f;
        }
        return (float) (1.0 / (1.0 + Math.exp(-value)));
    }

    private record Hit(int start, int end, int label, float score) {
    }
}
