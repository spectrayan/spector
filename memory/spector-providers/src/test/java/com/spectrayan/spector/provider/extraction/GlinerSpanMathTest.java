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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;

/**
 * Offline GLiNER packing and decode. No model file is loaded.
 */
class GlinerSpanMathTest {

    @Test
    @DisplayName("whitespace words keep character offsets")
    void wordsKeepOffsets() {
        List<GlinerSpanMath.Word> words = GlinerSpanMath.words("Alex works at Acme");
        assertThat(words).extracting(GlinerSpanMath.Word::text)
                .containsExactly("Alex", "works", "at", "Acme");
        assertThat(words.get(0).start()).isZero();
        assertThat(words.get(3).start()).isEqualTo(14);
        assertThat(words.get(3).end()).isEqualTo(18);
    }

    @Test
    @DisplayName("words_mask is 1-based on the first subtoken of each text word")
    void wordsMaskSkipsPrompt() {
        // [CLS] prompt(7 words) text(6 words) [SEP], matching a DeBERTa encode of the probe sentence.
        long[] wordIds = {-1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, -1};
        assertThat(GlinerSpanMath.wordsMask(wordIds, 7))
                .containsExactly(0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 0);
    }

    @Test
    @DisplayName("continuation subtokens stay zero")
    void continuationSubtokenIsZero() {
        long[] wordIds = {-1, 0, 1, 1, 2, -1};
        assertThat(GlinerSpanMath.wordsMask(wordIds, 1))
                .containsExactly(0, 0, 1, 0, 2, 0);
    }

    @Test
    @DisplayName("span index is inclusive and the mask rejects ends past the last word")
    void spanIndexAndMask() {
        long[][] index = GlinerSpanMath.spanIndex(2, 3);
        assertThat(index).isDeepEqualTo(new long[][]{
                {0, 0}, {0, 1}, {0, 2},
                {1, 1}, {1, 2}, {1, 3}});
        assertThat(GlinerSpanMath.spanMask(2, 3))
                .containsExactly(true, true, false, true, false, false);
    }

    @Test
    @DisplayName("greedy decode keeps the higher score and drops the overlap")
    void greedyDropsOverlap() {
        // two words, width 2, one class. Word 0 width 0 is a strong hit; the span covering both is stronger
        // and must win, so the single word is dropped.
        float[][][] logits = new float[][][]{
                {{logit(0.2f), logit(0.0f)}, {logit(0.9f), logit(0.0f)}},
                {{logit(0.1f), logit(0.0f)}, {logit(0.0f), logit(0.0f)}}
        };
        String text = "Ada Lovelace";
        List<GlinerSpanMath.Word> words = GlinerSpanMath.words(text);
        List<SpanMention> mentions = GlinerSpanMath.decode(logits, text, words, List.of("person", "other"), 0.5f);
        assertThat(mentions).hasSize(1);
        assertThat(mentions.get(0).text()).isEqualTo("Ada Lovelace");
        assertThat(mentions.get(0).label()).isEqualTo("person");
        assertThat(mentions.get(0).start()).isZero();
        assertThat(mentions.get(0).end()).isEqualTo(text.length());
    }

    @Test
    @DisplayName("a config that is not a whitespace GLiNER model is rejected")
    void configRejectsOtherModels() {
        assertThatThrownBy(() -> GlinerModelConfig.parse("{\"model_type\":\"bert\",\"max_width\":12,\"max_len\":384}",
                "gliner_config.json"))
                .isInstanceOf(SpectorConfigException.class)
                .extracting(ex -> ((SpectorConfigException) ex).errorCode())
                .isEqualTo(ErrorCode.EXTRACTION_MODEL_INVALID);
    }

    @Test
    @DisplayName("a config with overflowing integer fields is rejected")
    void configRejectsOverflowingIntegers() {
        assertThatThrownBy(() -> GlinerModelConfig.parse(
                "{\"model_type\":\"gliner\",\"words_splitter_type\":\"whitespace\",\"ent_token\":\"<<ENT>>\",\"sep_token\":\"<<SEP>>\",\"max_width\":99999999999999,\"max_len\":384}",
                "gliner_config.json"))
                .isInstanceOf(SpectorConfigException.class)
                .extracting(ex -> ((SpectorConfigException) ex).errorCode())
                .isEqualTo(ErrorCode.EXTRACTION_MODEL_INVALID);
    }

    private static float logit(float probability) {
        double p = Math.min(1 - 1e-6, Math.max(1e-6, probability));
        return (float) Math.log(p / (1.0 - p));
    }
}
