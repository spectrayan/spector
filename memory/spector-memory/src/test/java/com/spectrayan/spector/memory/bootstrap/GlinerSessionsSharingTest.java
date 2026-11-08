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
package com.spectrayan.spector.memory.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.spectrayan.spector.commons.error.SpectorMemoryException;
import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.config.model.TagExtractorMode;
import com.spectrayan.spector.config.properties.ExtractionProperties;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.config.properties.ProviderProperties;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.runtime.SpectorRuntime;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.provider.extraction.SpanExtractor;
import com.spectrayan.spector.provider.extraction.SpanMention;

/**
 * Verifies that namespaces configured with identical GLiNER extraction settings share one
 * underlying {@link SpanExtractor} session, isolate per-namespace close calls, and close
 * the shared extractor on runtime shutdown.
 */
class GlinerSessionsSharingTest {

    private static final List<String> LABELS = List.of("person", "organization", "location");

    @Test
    @DisplayName("multiple namespaces with identical GLiNER config share one underlying extractor and close cleanly on shutdown")
    void namespacesShareUnderlyingExtractorAndCloseOnShutdown(@TempDir Path dir) throws Exception {
        Path model = dir.resolve("model.onnx");
        Path tokenizer = dir.resolve("tokenizer.json");
        Path config = dir.resolve("gliner_config.json");
        writeTestGlinerFiles(model, tokenizer, config);

        SpectorProperties props = buildGlinerProperties(model, tokenizer, config);
        EmbeddingProvider embedder = new FixedEmbedder();

        SpanExtractor underlying1;
        try (SpectorRuntime runtime = SpectorRuntime.builder()
                .properties(props)
                .embeddingProvider(embedder)
                .build()) {
            SpectorMemory mem1 = runtime.attach("ns-1", b -> b.persistenceMode(MemoryPersistenceMode.IN_MEMORY));
            SpectorMemory mem2 = runtime.attach("ns-2", b -> b.persistenceMode(MemoryPersistenceMode.IN_MEMORY));

            SpanExtractor lease1 = spanExtractorOf(mem1);
            SpanExtractor lease2 = spanExtractorOf(mem2);
            assertThat(lease1).isNotNull();
            assertThat(lease2).isNotNull();

            underlying1 = unwrapUnderlying(lease1);
            SpanExtractor underlying2 = unwrapUnderlying(lease2);
            assertThat(underlying1)
                    .as("Both namespaces with identical GLiNER config must share the same underlying SpanExtractor")
                    .isSameAs(underlying2);

            List<SpanMention> mentions1 = lease1.extract("Alex works at Acme in Denver", LABELS);
            List<SpanMention> mentions2 = lease2.extract("Alex works at Acme in Denver", LABELS);
            assertThat(mentions1).extracting(SpanMention::text).containsExactly("Alex", "Acme", "Denver");
            assertThat(mentions2).isEqualTo(mentions1);

            // Closing ns-1 must close lease1 without closing the shared underlying extractor or invalidating ns-2
            mem1.close();
            assertThatThrownBy(() -> lease1.extract("Alex works at Acme in Denver", LABELS))
                    .isInstanceOf(SpectorMemoryException.class);
            assertThat(isUnderlyingClosed(underlying1))
                    .as("Closing one namespace must not close the shared underlying SpanExtractor")
                    .isFalse();
            assertThat(lease2.extract("Alex works at Acme in Denver", LABELS))
                    .isEqualTo(mentions1);

            // Closing ns-2 as well still keeps the pooled session reusable for subsequent namespaces until shutdown
            mem2.close();
            Files.deleteIfExists(model);
            try (SpectorMemory mem3 = runtime.attach("ns-3", b -> b.persistenceMode(MemoryPersistenceMode.IN_MEMORY))) {
                SpanExtractor lease3 = spanExtractorOf(mem3);
                assertThat(unwrapUnderlying(lease3)).isSameAs(underlying1);
                assertThat(lease3.extract("Alex works at Acme in Denver", LABELS)).isEqualTo(mentions1);
            }
        }

        assertThat(isUnderlyingClosed(underlying1))
                .as("Closing SpectorRuntime must close the shared underlying SpanExtractor when no active leases remain")
                .isTrue();
    }

    @Test
    @DisplayName("distinct GLiNER model configurations open distinct underlying extractors while identical configs share")
    void distinctConfigurationsMaintainIsolatedExtractors(@TempDir Path dir) throws Exception {
        Path model = dir.resolve("model.onnx");
        Path tokenizer = dir.resolve("tokenizer.json");
        Path config = dir.resolve("gliner_config.json");
        writeTestGlinerFiles(model, tokenizer, config);

        ExtractionProperties cfgA1 = buildGlinerProperties(model, tokenizer, config).provider().getExtraction();
        ExtractionProperties cfgA2 = buildGlinerProperties(model, tokenizer, config).provider().getExtraction();
        ExtractionProperties cfgB = buildGlinerProperties(model, tokenizer, config).provider().getExtraction();
        cfgB.setEntityThreshold(0.75f);

        try (SpanExtractor a1 = GlinerSessions.open(cfgA1);
             SpanExtractor a2 = GlinerSessions.open(cfgA2);
             SpanExtractor b = GlinerSessions.open(cfgB)) {
            assertThat(unwrapUnderlying(a1)).isSameAs(unwrapUnderlying(a2));
            assertThat(unwrapUnderlying(a1)).isNotSameAs(unwrapUnderlying(b));
        } finally {
            GlinerSessions.closeAll();
        }
    }

    private static SpectorProperties buildGlinerProperties(Path model, Path tokenizer, Path config) {
        MemoryProperties memory = new MemoryProperties()
                .setDimensions(4)
                .setWorkingCapacity(16)
                .setEpisodicPartitionCapacity(16)
                .setSemanticCapacity(16)
                .setProceduralCapacity(16)
                .setTagExtractor(TagExtractorMode.GLINER);
        memory.getEntity().setExtractionMode("gliner");

        ExtractionProperties extraction = new ExtractionProperties();
        extraction.setType("gliner");
        extraction.setModelPath(model.toString());
        extraction.setTokenizerPath(tokenizer.toString());
        extraction.setConfigPath(config.toString());
        extraction.setEntityThreshold(0.5f);
        extraction.setIntraOpThreads(1);
        extraction.setMaxWidth(12);
        extraction.setMaxTokens(384);
        extraction.setLabels("person,organization,location");

        ProviderProperties provider = new ProviderProperties();
        provider.setExtraction(extraction);

        return SpectorProperties.builder()
                .memory(memory)
                .provider(provider)
                .build();
    }

    private static SpanExtractor spanExtractorOf(SpectorMemory memory) throws Exception {
        assertThat(memory).isInstanceOf(DefaultSpectorMemory.class);
        Field field = DefaultSpectorMemory.class.getDeclaredField("spanExtractor");
        field.setAccessible(true);
        return (SpanExtractor) field.get(memory);
    }

    private static SpanExtractor unwrapUnderlying(SpanExtractor extractor) throws Exception {
        SpanExtractor current = extractor;
        while (current != null) {
            try {
                Field delegateField = current.getClass().getDeclaredField("delegate");
                delegateField.setAccessible(true);
                Object next = delegateField.get(current);
                if (next instanceof SpanExtractor spanNext && spanNext != current) {
                    current = spanNext;
                    continue;
                }
            } catch (NoSuchFieldException ignored) {
            }
            break;
        }
        return current;
    }

    private static boolean isUnderlyingClosed(SpanExtractor underlying) throws Exception {
        Field closedField = underlying.getClass().getDeclaredField("closed");
        closedField.setAccessible(true);
        return closedField.getBoolean(underlying);
    }

    private static void writeTestGlinerFiles(Path model, Path tokenizer, Path config) throws Exception {
        try (InputStream in = GlinerSessionsSharingTest.class.getResourceAsStream("/all-minilm-l6-v2-q-tokenizer.json")) {
            assertThat(in).as("bundled tokenizer resource").isNotNull();
            Files.write(tokenizer, in.readAllBytes());
        }
        Files.writeString(config, """
                {"model_type":"gliner","words_splitter_type":"whitespace","ent_token":"<<ENT>>","sep_token":"<<SEP>>","max_width":12,"max_len":384,"max_types":25}
                """);
        Files.write(model, buildMinimalGlinerOnnxModel(8, 12, 3));
    }

    private static byte[] buildMinimalGlinerOnnxModel(int maxWords, int maxWidth, int numClasses) {
        float[] logits = new float[maxWords * maxWidth * numClasses];
        Arrays.fill(logits, -4.0f);
        logits[(0 * maxWidth + 0) * numClasses + 0] = 4.0f;
        logits[(3 * maxWidth + 0) * numClasses + 1] = 4.0f;
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

    private static final class FixedEmbedder implements EmbeddingProvider {
        @Override
        public EmbeddingResult embed(String text) {
            return new EmbeddingResult(new float[]{1f, 0f, 0f, 0f}, 1, "fixed");
        }

        @Override
        public int dimensions() {
            return 4;
        }

        @Override
        public String modelName() {
            return "fixed";
        }
    }
}
