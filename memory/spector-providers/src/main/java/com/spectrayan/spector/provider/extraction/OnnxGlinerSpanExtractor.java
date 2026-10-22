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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;
import com.spectrayan.spector.commons.error.SpectorMemoryException;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/**
 * GLiNER uni-encoder over ONNX Runtime. One session is shared and guarded by
 * the extractor lock. Intra-op threads default to one so int8 GEMM is not
 * preempted across cores. The text being scored is never logged.
 */
public final class OnnxGlinerSpanExtractor implements SpanExtractor {

    private static final Logger log = LoggerFactory.getLogger(OnnxGlinerSpanExtractor.class);

    private final HuggingFaceTokenizer tokenizer;
    private final OrtSession session;
    private final OrtSession.SessionOptions options;
    private final GlinerModelConfig config;
    private final float threshold;
    private final List<String> defaultLabels;
    private final Object lock = new Object();
    private boolean closed;

    private OnnxGlinerSpanExtractor(HuggingFaceTokenizer tokenizer, OrtSession session,
                                    OrtSession.SessionOptions options, GlinerModelConfig config,
                                    float threshold, List<String> defaultLabels) {
        this.tokenizer = tokenizer;
        this.session = session;
        this.options = options;
        this.config = config;
        this.threshold = threshold;
        this.defaultLabels = List.copyOf(defaultLabels);
    }

    /**
     * Opens the session. A missing file, a config that is not a whitespace GLiNER
     * span model, or a label list longer than {@code max_types} fails here.
     *
     * @param spec local files and decode settings
     * @return an open extractor
     */
    public static OnnxGlinerSpanExtractor open(SpanModelSpec spec) {
        requireFile(spec.modelPath(), "model-path");
        requireFile(spec.tokenizerPath(), "tokenizer-path");
        requireFile(spec.configPath(), "config-path");
        GlinerModelConfig config = readConfig(spec.configPath());
        if (spec.maxWidth() > 0 && spec.maxWidth() != config.maxWidth) {
            throw new SpectorConfigException(ErrorCode.EXTRACTION_MODEL_INVALID, "max-width",
                    spec.maxWidth() + " does not match gliner_config.json " + config.maxWidth);
        }
        if (spec.maxTokens() > 0 && spec.maxTokens() != config.maxLen) {
            throw new SpectorConfigException(ErrorCode.EXTRACTION_MODEL_INVALID, "max-tokens",
                    spec.maxTokens() + " does not match gliner_config.json " + config.maxLen);
        }
        List<String> labels = spec.labels() == null ? List.of() : spec.labels();
        if (labels.isEmpty()) {
            throw new SpectorConfigException(ErrorCode.EXTRACTION_MODEL_INVALID, "labels", "none configured");
        }
        if (labels.size() > config.maxTypes) {
            throw new SpectorConfigException(ErrorCode.EXTRACTION_MODEL_INVALID, "labels",
                    labels.size() + " exceeds max_types " + config.maxTypes);
        }
        HuggingFaceTokenizer tokenizer = null;
        OrtSession.SessionOptions options = null;
        OrtSession session = null;
        try {
            tokenizer = HuggingFaceTokenizer.newInstance(spec.tokenizerPath());
            options = new OrtSession.SessionOptions();
            int threads = Math.max(1, spec.intraOpThreads());
            options.setIntraOpNumThreads(threads);
            options.setInterOpNumThreads(1);
            options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);
            session = OrtEnvironment.getEnvironment().createSession(spec.modelPath().toString(), options);
            long bytes = Files.size(spec.modelPath());
            log.info("[GLiNER] loaded {} ({} bytes, max_width={}, max_len={}, threads={})",
                    spec.modelPath().getFileName(), bytes, config.maxWidth, config.maxLen, threads);
            OnnxGlinerSpanExtractor extractor = new OnnxGlinerSpanExtractor(
                    tokenizer, session, options, config, spec.threshold(), labels);
            tokenizer = null;
            session = null;
            options = null;
            return extractor;
        } catch (SpectorConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new SpectorConfigException(ErrorCode.EXTRACTION_MODEL_INVALID, e,
                    spec.modelPath().toString(), e.getClass().getSimpleName());
        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (OrtException ignored) {
                    // The open already failed.
                }
            }
            if (options != null) {
                options.close();
            }
            if (tokenizer != null) {
                tokenizer.close();
            }
        }
    }

    @Override
    public List<SpanMention> extract(String text, List<String> labels) {
        List<String> used = labels == null || labels.isEmpty() ? defaultLabels : labels;
        if (text == null || text.isBlank() || used.isEmpty()) {
            return List.of();
        }
        synchronized (lock) {
            if (closed) {
                throw new SpectorMemoryException(ErrorCode.MEMORY_EXTRACTION_FAILED, "span", "session is closed");
            }
            try {
                return infer(text, used);
            } catch (SpectorMemoryException e) {
                throw e;
            } catch (RuntimeException e) {
                log.error("[GLiNER] inference failed ({})", ErrorCode.MEMORY_EXTRACTION_FAILED.id(), e);
                throw new SpectorMemoryException(ErrorCode.MEMORY_EXTRACTION_FAILED, "span",
                        e.getClass().getSimpleName());
            }
        }
    }

    private List<SpanMention> infer(String text, List<String> labels) {
        List<GlinerSpanMath.Word> words = GlinerSpanMath.words(text);
        if (words.isEmpty()) {
            return List.of();
        }
        if (words.size() > config.maxLen) {
            words = new ArrayList<>(words.subList(0, config.maxLen));
        }
        List<String> prompt = new ArrayList<>(labels.size() * 2 + 1);
        for (String label : labels) {
            prompt.add(config.entToken);
            prompt.add(label);
        }
        prompt.add(config.sepToken);
        List<String> pieces = new ArrayList<>(prompt.size() + words.size());
        pieces.addAll(prompt);
        for (GlinerSpanMath.Word word : words) {
            pieces.add(word.text());
        }
        Encoding encoding = fit(pieces, prompt.size(), words);
        int kept = encodingWords(pieces, prompt.size());
        if (kept < words.size()) {
            words = words.subList(0, kept);
        }
        long[] ids = encoding.getIds();
        long[] mask = GlinerSpanMath.wordsMask(encoding.getWordIds(), prompt.size());
        int wordCount = words.size();
        long[][] spanIndex = GlinerSpanMath.spanIndex(wordCount, config.maxWidth);
        boolean[] spanMask = GlinerSpanMath.spanMask(wordCount, config.maxWidth);
        OrtEnvironment environment = OrtEnvironment.getEnvironment();
        try (OnnxTensor idsTensor = OnnxTensor.createTensor(environment, new long[][]{ids});
             OnnxTensor attention = OnnxTensor.createTensor(environment, new long[][]{encoding.getAttentionMask()});
             OnnxTensor wordsMask = OnnxTensor.createTensor(environment, new long[][]{mask});
             OnnxTensor lengths = OnnxTensor.createTensor(environment, new long[][]{{wordCount}});
             OnnxTensor spans = OnnxTensor.createTensor(environment, new long[][][]{spanIndex});
             OnnxTensor spanMaskTensor = OnnxTensor.createTensor(environment, new boolean[][]{spanMask});
             OrtSession.Result result = session.run(Map.of(
                     "input_ids", idsTensor,
                     "attention_mask", attention,
                     "words_mask", wordsMask,
                     "text_lengths", lengths,
                     "span_idx", spans,
                     "span_mask", spanMaskTensor))) {
            OnnxTensor logitsTensor = (OnnxTensor) result.get("logits").orElseThrow();
            float[][][][] logits = (float[][][][]) logitsTensor.getValue();
            if (logits[0].length == 0 || logits[0][0][0].length < labels.size()) {
                throw new SpectorMemoryException(ErrorCode.MEMORY_EXTRACTION_FAILED, "span",
                        "logit classes do not match the label list");
            }
            return GlinerSpanMath.decode(logits[0], text, words, labels, threshold);
        } catch (OrtException e) {
            log.error("[GLiNER] inference failed ({})", ErrorCode.MEMORY_EXTRACTION_FAILED.id());
            throw new SpectorMemoryException(ErrorCode.MEMORY_EXTRACTION_FAILED, "span", "OrtException");
        }
    }

    /**
     * Drops text words from the end until the token sequence fits {@code max_len}.
     * The returned encoding's word-id space still starts with the prompt.
     */
    private Encoding fit(List<String> pieces, int promptWords, List<GlinerSpanMath.Word> words) {
        Encoding encoding = tokenizer.encode(pieces, true, false);
        while (encoding.getIds().length > config.maxLen && pieces.size() > promptWords) {
            pieces.remove(pieces.size() - 1);
            words.remove(words.size() - 1);
            encoding = tokenizer.encode(pieces, true, false);
        }
        return encoding;
    }

    private static int encodingWords(List<String> pieces, int promptWords) {
        return Math.max(0, pieces.size() - promptWords);
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            try {
                session.close();
            } catch (OrtException e) {
                log.warn("[GLiNER] session close failed: {}", e.getClass().getSimpleName());
            }
            options.close();
            tokenizer.close();
        }
    }

    private static void requireFile(Path path, String key) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new SpectorConfigException(ErrorCode.EXTRACTION_MODEL_INVALID, key,
                    (path == null ? "not set" : path + " does not exist"));
        }
    }

    private static GlinerModelConfig readConfig(Path path) {
        try {
            return GlinerModelConfig.parse(Files.readString(path), path.toString());
        } catch (IOException e) {
            throw new SpectorConfigException(ErrorCode.EXTRACTION_MODEL_INVALID, path.toString(),
                    "could not be read");
        }
    }
}
