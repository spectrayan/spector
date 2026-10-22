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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;

/**
 * GLiNER session tests. The int8 model is not part of the build: the inference
 * test runs when {@code SPECTOR_GLINER_MODEL_DIR} (or {@code /tmp/gliner-small})
 * contains {@code onnx/model_int8.onnx}, {@code tokenizer.json} and
 * {@code gliner_config.json}, and is skipped otherwise.
 */
class OnnxGlinerSpanExtractorTest {

    private static final List<String> LABELS = List.of("person", "organization", "location");

    @Test
    @DisplayName("a missing model file fails startup and names the path")
    void missingModelFails(@TempDir Path dir) {
        assertThatThrownBy(() -> OnnxGlinerSpanExtractor.open(spec(dir.resolve("missing.onnx"),
                dir.resolve("tokenizer.json"), dir.resolve("gliner_config.json"), 12, 384)))
                .isInstanceOf(SpectorConfigException.class)
                .hasMessageContaining("does not exist")
                .extracting(ex -> ((SpectorConfigException) ex).errorCode())
                .isEqualTo(ErrorCode.EXTRACTION_MODEL_INVALID);
    }

    @Test
    @DisplayName("max-width that disagrees with the model config fails startup")
    void widthMismatchFails(@TempDir Path dir) throws Exception {
        Path model = dir.resolve("model.onnx");
        Path tokenizer = dir.resolve("tokenizer.json");
        Path config = dir.resolve("gliner_config.json");
        Files.writeString(model, "not a model");
        Files.writeString(tokenizer, "{}");
        Files.writeString(config, """
                {"model_type":"gliner","words_splitter_type":"whitespace","ent_token":"<<ENT>>","sep_token":"<<SEP>>","max_width":12,"max_len":384,"max_types":25}
                """);
        assertThatThrownBy(() -> OnnxGlinerSpanExtractor.open(spec(model, tokenizer, config, 8, 384)))
                .isInstanceOf(SpectorConfigException.class)
                .hasMessageContaining("max-width")
                .extracting(ex -> ((SpectorConfigException) ex).errorCode())
                .isEqualTo(ErrorCode.EXTRACTION_MODEL_INVALID);
    }

    @Test
    @DisplayName("gliner_small int8 finds person, organization and location")
    void smallModelFindsEntities() {
        Path dir = modelDir();
        Path model = dir.resolve("onnx/model_int8.onnx");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(model)
                && Files.isRegularFile(dir.resolve("tokenizer.json"))
                && Files.isRegularFile(dir.resolve("gliner_config.json")),
                "GLiNER model not present; set SPECTOR_GLINER_MODEL_DIR");
        try (SpanExtractor extractor = OnnxGlinerSpanExtractor.open(
                spec(model, dir.resolve("tokenizer.json"), dir.resolve("gliner_config.json"), 12, 384))) {
            List<SpanMention> mentions = extractor.extract("Alex works at Acme in Denver", LABELS);
            assertThat(mentions).extracting(SpanMention::text).contains("Alex", "Acme", "Denver");
            assertThat(mention(mentions, "Alex").label()).isEqualTo("person");
            assertThat(mention(mentions, "Acme").label()).isEqualTo("organization");
            assertThat(mention(mentions, "Denver").label()).isEqualTo("location");
            assertThat(mention(mentions, "Alex").score()).isGreaterThan(0.5f);
        }
    }

    private static SpanMention mention(List<SpanMention> mentions, String text) {
        return mentions.stream().filter(m -> m.text().equals(text)).findFirst().orElseThrow();
    }

    private static SpanModelSpec spec(Path model, Path tokenizer, Path config, int width, int tokens) {
        return new SpanModelSpec(model, tokenizer, config, 0.5f, 1, width, tokens, LABELS);
    }

    private static Path modelDir() {
        String configured = System.getenv("SPECTOR_GLINER_MODEL_DIR");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return Path.of("/tmp/gliner-small");
    }
}
