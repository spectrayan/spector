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
package com.spectrayan.spector.memory.affect;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;
import com.spectrayan.spector.config.properties.AffectProperties;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.core.similarity.CosineSimilarity;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;

/**
 * Valence and arousal from four pole centroids.
 *
 * <p>{@code valence = round(127 * tanh(g * (cos(m, C+) - cos(m, C-))))}, with a deadband
 * on the cosine difference. {@code arousal = round(127.5 + 127.5 * tanh(g * (cos(m, C_hi) - cos(m, C_lo))))}.
 * Centroids are built once, at construction.</p>
 */
public final class AnchorAffectAppraiser implements AffectAppraiser {

    static final String BUNDLED_RESOURCE = "/affect-anchors.yaml";
    private static final String[] POLES = {"positive", "negative", "high", "low"};

    private final float deadband;
    private final float gain;
    private final float[] positive;
    private final float[] negative;
    private final float[] high;
    private final float[] low;

    /**
     * Builds centroids when {@code spector.memory.affect.appraiser=soul-anchors}.
     *
     * @param properties memory configuration
     * @param embedder   the same embedder used for memories
     * @return the appraiser, or {@code null} when anchors are off
     */
    public static AffectAppraiser create(MemoryProperties properties, EmbeddingProvider embedder) {
        AffectProperties affect = properties != null ? properties.getAffect() : null;
        if (affect == null || affect.getAppraiser() != AffectProperties.Mode.SOUL_ANCHORS) {
            return null;
        }
        return new AnchorAffectAppraiser(affect, embedder);
    }

    /**
     * Loads anchors and embeds every phrase.
     *
     * @param affect   anchor settings; appraiser mode is not re-checked
     * @param embedder memory embedder
     */
    public AnchorAffectAppraiser(AffectProperties affect, EmbeddingProvider embedder) {
        if (embedder == null) {
            throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, "spector.memory.affect.appraiser",
                    "soul-anchors requires an embedding provider");
        }
        this.deadband = Math.max(0f, affect.getDeadband());
        this.gain = affect.getGain();
        Map<String, List<String>> poles = load(affect.getAnchorsPath());
        int expected = embedder.dimensions();
        this.positive = centroid("positive", poles.get("positive"), embedder, expected);
        this.negative = centroid("negative", poles.get("negative"), embedder, expected);
        this.high = centroid("high", poles.get("high"), embedder, expected);
        this.low = centroid("low", poles.get("low"), embedder, expected);
    }

    @Override
    public Affect appraise(float[] vector) {
        if (vector == null || vector.length != positive.length) {
            throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, "spector.memory.affect.appraiser",
                    "memory vector length " + (vector == null ? 0 : vector.length)
                            + " does not match anchor dimension " + positive.length);
        }
        double valenceDelta = CosineSimilarity.computeSafe(vector, positive)
                - CosineSimilarity.computeSafe(vector, negative);
        byte valence = 0;
        if (Math.abs(valenceDelta) >= deadband) {
            double raw = 127.0 * Math.tanh(gain * valenceDelta);
            int rounded = (int) Math.round(raw);
            if (rounded > 127) {
                rounded = 127;
            } else if (rounded < -128) {
                rounded = -128;
            }
            valence = (byte) rounded;
        }
        double arousalDelta = CosineSimilarity.computeSafe(vector, high)
                - CosineSimilarity.computeSafe(vector, low);
        double arousalRaw = 127.5 + 127.5 * Math.tanh(gain * arousalDelta);
        int arousal = (int) Math.round(arousalRaw);
        if (arousal > 255) {
            arousal = 255;
        } else if (arousal < 0) {
            arousal = 0;
        }
        return new Affect(valence, (byte) arousal);
    }

    private static float[] centroid(String pole, List<String> phrases, EmbeddingProvider embedder, int expected) {
        if (phrases == null || phrases.isEmpty()) {
            throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, "spector.memory.affect.anchors-path",
                    "missing pole '" + pole + "'");
        }
        float[] sum = null;
        for (String phrase : phrases) {
            float[] vector = embedder.embed(phrase).vector();
            if (vector == null || vector.length == 0) {
                throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, "spector.memory.affect.anchors-path",
                        "pole '" + pole + "' produced an empty embedding");
            }
            if (sum == null) {
                if (expected > 0 && vector.length != expected) {
                    throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, "spector.memory.affect.appraiser",
                            "anchor dimension " + vector.length + " does not match embedding provider dimension "
                                    + expected);
                }
                sum = new float[vector.length];
            } else if (vector.length != sum.length) {
                throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, "spector.memory.affect.appraiser",
                        "pole '" + pole + "' dimension " + vector.length + " does not match " + sum.length);
            }
            float[] unit = unit(vector);
            for (int i = 0; i < unit.length; i++) {
                sum[i] += unit[i];
            }
        }
        for (int i = 0; i < sum.length; i++) {
            sum[i] /= phrases.size();
        }
        return unit(sum);
    }

    private static float[] unit(float[] vector) {
        double norm = 0;
        for (float v : vector) {
            norm += (double) v * v;
        }
        norm = Math.sqrt(norm);
        if (norm == 0.0 || Double.isNaN(norm)) {
            throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, "spector.memory.affect.anchors-path",
                    "an anchor embedding has zero length");
        }
        float[] out = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            out[i] = (float) (vector[i] / norm);
        }
        return out;
    }

    private static Map<String, List<String>> load(String anchorsPath) {
        String source = anchorsPath == null || anchorsPath.isBlank() ? BUNDLED_RESOURCE : anchorsPath;
        Object loaded;
        try (Reader reader = reader(anchorsPath)) {
            loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
        } catch (IOException e) {
            throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, "spector.memory.affect.anchors-path",
                    source + " could not be read");
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, "spector.memory.affect.anchors-path",
                    source + " is not a mapping of poles");
        }
        java.util.HashMap<String, List<String>> poles = new java.util.HashMap<>();
        for (String pole : POLES) {
            Object value = map.get(pole);
            if (!(value instanceof List<?> list) || list.isEmpty()) {
                throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, "spector.memory.affect.anchors-path",
                        source + " is missing pole '" + pole + "'");
            }
            List<String> phrases = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    phrases.add(item.toString());
                }
            }
            if (phrases.isEmpty()) {
                throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, "spector.memory.affect.anchors-path",
                        source + " is missing pole '" + pole + "'");
            }
            poles.put(pole, phrases);
        }
        return poles;
    }

    private static Reader reader(String anchorsPath) throws IOException {
        if (anchorsPath != null && !anchorsPath.isBlank()) {
            Path path = Path.of(anchorsPath);
            if (!Files.isRegularFile(path)) {
                throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, "spector.memory.affect.anchors-path",
                        path + " does not exist");
            }
            return Files.newBufferedReader(path, StandardCharsets.UTF_8);
        }
        InputStream in = AnchorAffectAppraiser.class.getResourceAsStream(BUNDLED_RESOURCE);
        if (in == null) {
            throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, "spector.memory.affect.anchors-path",
                    BUNDLED_RESOURCE + " is not on the classpath");
        }
        return new java.io.InputStreamReader(in, StandardCharsets.UTF_8);
    }
}
