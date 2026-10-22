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

import java.nio.file.Path;
import java.util.List;

/**
 * Local files and decode settings for a span model. Nothing is downloaded.
 *
 * @param modelPath       ONNX weights
 * @param tokenizerPath   Hugging Face {@code tokenizer.json}
 * @param configPath      GLiNER {@code gliner_config.json}
 * @param threshold       minimum sigmoid score
 * @param intraOpThreads  ONNX Runtime intra-op threads, at least 1
 * @param maxWidth        requested span width; must match the model config
 * @param maxTokens       requested sequence cap; must match the model config
 * @param labels          prompt labels, in class order
 */
public record SpanModelSpec(
        Path modelPath,
        Path tokenizerPath,
        Path configPath,
        float threshold,
        int intraOpThreads,
        int maxWidth,
        int maxTokens,
        List<String> labels
) {
}
