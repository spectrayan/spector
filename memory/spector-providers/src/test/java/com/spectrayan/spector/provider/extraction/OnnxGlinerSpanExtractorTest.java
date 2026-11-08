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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    @Test
    @DisplayName("concurrent extract calls execute in parallel without serializing on a single monitor and return identical spans")
    void concurrentExtractsExecuteInParallelWithoutGlobalMonitor(@TempDir Path dir) throws Exception {
        SpanModelSpec modelSpec = createTestGlinerFixture(dir);
        try (SpanExtractor extractor = OnnxGlinerSpanExtractor.open(modelSpec)) {
            List<SpanMention> baseline = extractor.extract("Alex works at Acme in Denver", LABELS);
            assertThat(baseline).extracting(SpanMention::text).containsExactly("Alex", "Acme", "Denver");
            assertThat(mention(baseline, "Alex").label()).isEqualTo("person");
            assertThat(mention(baseline, "Acme").label()).isEqualTo("organization");
            assertThat(mention(baseline, "Denver").label()).isEqualTo("location");

            int parties = 4;
            CyclicBarrier insideInferBarrier = new CyclicBarrier(parties);
            List<String> barrierLabels = new AbstractList<>() {
                @Override
                public String get(int index) {
                    return LABELS.get(index);
                }

                @Override
                public int size() {
                    return LABELS.size();
                }

                @Override
                public Iterator<String> iterator() {
                    try {
                        insideInferBarrier.await(2, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new IllegalStateException("Concurrent extract calls were serialized on a global monitor", e);
                    }
                    return LABELS.iterator();
                }
            };

            try (ExecutorService pool = Executors.newFixedThreadPool(parties)) {
                List<Callable<List<SpanMention>>> tasks = new ArrayList<>(parties);
                for (int i = 0; i < parties; i++) {
                    tasks.add(() -> extractor.extract("Alex works at Acme in Denver", barrierLabels));
                }
                List<Future<List<SpanMention>>> futures = new ArrayList<>(parties);
                for (Callable<List<SpanMention>> task : tasks) {
                    futures.add(pool.submit(task));
                }
                for (Future<List<SpanMention>> future : futures) {
                    assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(baseline);
                }
            }
        }
    }

    private static SpanMention mention(List<SpanMention> mentions, String text) {
        return mentions.stream().filter(m -> m.text().equals(text)).findFirst().orElseThrow();
    }

    private static SpanModelSpec spec(Path model, Path tokenizer, Path config, int width, int tokens) {
        return new SpanModelSpec(model, tokenizer, config, 0.5f, 1, width, tokens, LABELS);
    }

    static SpanModelSpec createTestGlinerFixture(Path dir) throws Exception {
        Path model = dir.resolve("model.onnx");
        Path tokenizer = dir.resolve("tokenizer.json");
        Path config = dir.resolve("gliner_config.json");
        try (InputStream in = OnnxGlinerSpanExtractorTest.class.getResourceAsStream("/all-minilm-l6-v2-q-tokenizer.json")) {
            assertThat(in).as("bundled tokenizer resource").isNotNull();
            Files.write(tokenizer, in.readAllBytes());
        }
        Files.writeString(config, """
                {"model_type":"gliner","words_splitter_type":"whitespace","ent_token":"<<ENT>>","sep_token":"<<SEP>>","max_width":12,"max_len":384,"max_types":25}
                """);
        Files.write(model, buildMinimalGlinerOnnxModel(8, 12, 3));
        return spec(model, tokenizer, config, 12, 384);
    }

    private static byte[] buildMinimalGlinerOnnxModel(int maxWords, int maxWidth, int numClasses) {
        float[] logits = new float[maxWords * maxWidth * numClasses];
        java.util.Arrays.fill(logits, -4.0f);
        // "Alex" (word 0, width 0, class 0 = person)
        logits[(0 * maxWidth + 0) * numClasses + 0] = 4.0f;
        // "Acme" (word 3, width 0, class 1 = organization)
        logits[(3 * maxWidth + 0) * numClasses + 1] = 4.0f;
        // "Denver" (word 5, width 0, class 2 = location)
        logits[(5 * maxWidth + 0) * numClasses + 2] = 4.0f;

        ByteBuffer rawLogits = ByteBuffer.allocate(logits.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : logits) {
            rawLogits.putFloat(v);
        }

        ByteArrayOutputStream graph = new ByteArrayOutputStream();
        writeMsg(graph, 1, onnxNode("const_logits", "logits", "id_logits", "Identity"));
        writeMsg(graph, 1, onnxNode("input_ids", "_out_ids", "id_0", "Identity"));
        writeMsg(graph, 1, onnxNode("attention_mask", "_out_att", "id_1", "Identity"));
        writeMsg(graph, 1, onnxNode("words_mask", "_out_wm", "id_2", "Identity"));
        writeMsg(graph, 1, onnxNode("text_lengths", "_out_len", "id_3", "Identity"));
        writeMsg(graph, 1, onnxNode("span_idx", "_out_si", "id_4", "Identity"));
        writeMsg(graph, 1, onnxNode("span_mask", "_out_sm", "id_5", "Identity"));
        writeStr(graph, 2, "gliner_test_graph");
        writeMsg(graph, 5, onnxFloatTensor("const_logits", new long[]{1, maxWords, maxWidth, numClasses}, rawLogits.array()));

        writeMsg(graph, 11, onnxValueInfo("input_ids", 7, new long[]{-1, -1}));
        writeMsg(graph, 11, onnxValueInfo("attention_mask", 7, new long[]{-1, -1}));
        writeMsg(graph, 11, onnxValueInfo("words_mask", 7, new long[]{-1, -1}));
        writeMsg(graph, 11, onnxValueInfo("text_lengths", 7, new long[]{-1, -1}));
        writeMsg(graph, 11, onnxValueInfo("span_idx", 7, new long[]{-1, -1, -1}));
        writeMsg(graph, 11, onnxValueInfo("span_mask", 9, new long[]{-1, -1}));

        writeMsg(graph, 12, onnxValueInfo("logits", 1, new long[]{1, maxWords, maxWidth, numClasses}));
        writeMsg(graph, 12, onnxValueInfo("_out_ids", 7, new long[]{-1, -1}));
        writeMsg(graph, 12, onnxValueInfo("_out_att", 7, new long[]{-1, -1}));
        writeMsg(graph, 12, onnxValueInfo("_out_wm", 7, new long[]{-1, -1}));
        writeMsg(graph, 12, onnxValueInfo("_out_len", 7, new long[]{-1, -1}));
        writeMsg(graph, 12, onnxValueInfo("_out_si", 7, new long[]{-1, -1, -1}));
        writeMsg(graph, 12, onnxValueInfo("_out_sm", 9, new long[]{-1, -1}));

        ByteArrayOutputStream opset = new ByteArrayOutputStream();
        writeStr(opset, 1, "");
        writeVarintField(opset, 2, 13);

        ByteArrayOutputStream model = new ByteArrayOutputStream();
        writeVarintField(model, 1, 7);
        writeMsg(model, 7, graph.toByteArray());
        writeMsg(model, 8, opset.toByteArray());
        return model.toByteArray();
    }

    private static byte[] onnxNode(String input, String output, String name, String opType) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeStr(out, 1, input);
        writeStr(out, 2, output);
        writeStr(out, 3, name);
        writeStr(out, 4, opType);
        return out.toByteArray();
    }

    private static byte[] onnxFloatTensor(String name, long[] dims, byte[] rawBytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (long d : dims) {
            writeVarintField(out, 1, d);
        }
        writeVarintField(out, 2, 1);
        writeStr(out, 8, name);
        writeBytes(out, 9, rawBytes);
        return out.toByteArray();
    }

    private static byte[] onnxValueInfo(String name, int elemType, long[] dims) {
        ByteArrayOutputStream shape = new ByteArrayOutputStream();
        for (int i = 0; i < dims.length; i++) {
            ByteArrayOutputStream dim = new ByteArrayOutputStream();
            if (dims[i] > 0) {
                writeVarintField(dim, 1, dims[i]);
            } else {
                writeStr(dim, 2, "d" + i);
            }
            writeMsg(shape, 1, dim.toByteArray());
        }
        ByteArrayOutputStream tensorType = new ByteArrayOutputStream();
        writeVarintField(tensorType, 1, elemType);
        writeMsg(tensorType, 2, shape.toByteArray());

        ByteArrayOutputStream type = new ByteArrayOutputStream();
        writeMsg(type, 1, tensorType.toByteArray());

        ByteArrayOutputStream vi = new ByteArrayOutputStream();
        writeStr(vi, 1, name);
        writeMsg(vi, 2, type.toByteArray());
        return vi.toByteArray();
    }

    private static void writeVarintField(ByteArrayOutputStream out, int fieldNumber, long value) {
        writeVarint(out, ((long) fieldNumber << 3));
        writeVarint(out, value);
    }

    private static void writeStr(ByteArrayOutputStream out, int fieldNumber, String value) {
        writeBytes(out, fieldNumber, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeMsg(ByteArrayOutputStream out, int fieldNumber, byte[] value) {
        writeBytes(out, fieldNumber, value);
    }

    private static void writeBytes(ByteArrayOutputStream out, int fieldNumber, byte[] bytes) {
        writeVarint(out, ((long) fieldNumber << 3) | 2L);
        writeVarint(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static void writeVarint(ByteArrayOutputStream out, long value) {
        long v = value;
        while ((v & ~0x7FL) != 0L) {
            out.write((int) ((v & 0x7FL) | 0x80L));
            v >>>= 7;
        }
        out.write((int) v);
    }

    private static Path modelDir() {
        String configured = System.getenv("SPECTOR_GLINER_MODEL_DIR");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return Path.of("/tmp/gliner-small");
    }
}
