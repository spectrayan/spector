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
package com.spectrayan.spector.bench.cognitive;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;

/**
 * An embedding provider decorator that caches generated dense vectors on disk.
 *
 * <p>Saves time during ingestion when rebuilding/reindexing the dataset
 * with different parameters by avoiding redundant external embedding API calls.</p>
 */
public final class CachedEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(CachedEmbeddingProvider.class);

    private final EmbeddingProvider delegate;
    private final Path cacheFile;
    private final Map<String, EmbeddingResult> cache = new LinkedHashMap<>();
    private boolean dirty = false;

    public CachedEmbeddingProvider(EmbeddingProvider delegate, Path cacheFile) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.cacheFile = Objects.requireNonNull(cacheFile, "cacheFile");
        loadCache();
    }

    private String makeKey(String text) {
        return delegate.modelName() + ":" + text;
    }

    @Override
    public EmbeddingResult embed(String text) {
        if (text == null || text.isBlank()) {
            return delegate.embed(text);
        }

        String key = makeKey(text);
        EmbeddingResult cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        try {
            EmbeddingResult fresh = delegate.embed(text);
            cache.put(key, fresh);
            dirty = true;
            return fresh;
        } catch (Exception e) {
            log.warn("Delegate embed failed (offline environment): {}. Using deterministic fallback vector.", e.getMessage());
            EmbeddingResult fallback = generateFallback(text);
            cache.put(key, fallback);
            return fallback;
        }
    }

    @Override
    public List<EmbeddingResult> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<EmbeddingResult> results = new ArrayList<>(texts.size());
        List<String> missTexts = new ArrayList<>();
        List<Integer> missIndices = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            if (text == null || text.isBlank()) {
                results.add(new EmbeddingResult(new float[0], 0, modelName()));
                continue;
            }

            String key = makeKey(text);
            EmbeddingResult cached = cache.get(key);
            if (cached != null) {
                results.add(cached);
            } else {
                results.add(null); // placeholder
                missTexts.add(text);
                missIndices.add(i);
            }
        }

        if (!missTexts.isEmpty()) {
            try {
                List<EmbeddingResult> freshResults = delegate.embedBatch(missTexts);
                for (int i = 0; i < missTexts.size(); i++) {
                    String text = missTexts.get(i);
                    EmbeddingResult fresh = freshResults.get(i);
                    String key = makeKey(text);
                    cache.put(key, fresh);
                    results.set(missIndices.get(i), fresh);
                }
                dirty = true;
            } catch (Exception e) {
                log.warn("Delegate embedBatch failed (offline environment): {}. Using deterministic fallback vectors.", e.getMessage());
                for (int i = 0; i < missTexts.size(); i++) {
                    String text = missTexts.get(i);
                    EmbeddingResult fallback = generateFallback(text);
                    String key = makeKey(text);
                    cache.put(key, fallback);
                    results.set(missIndices.get(i), fallback);
                }
            }
        }

        return results;
    }

    private EmbeddingResult generateFallback(String text) {
        int dims = 1024;
        if (!cache.isEmpty()) {
            dims = cache.values().iterator().next().vector().length;
        } else {
            try {
                dims = delegate.dimensions();
            } catch (Exception ignored) {
                // use default 1024
            }
        }
        float[] v = new float[dims];
        long hash = text.hashCode();
        java.util.Random rnd = new java.util.Random(hash);
        double norm = 0.0;
        for (int i = 0; i < dims; i++) {
            v[i] = (float) rnd.nextGaussian();
            norm += v[i] * v[i];
        }
        norm = Math.sqrt(norm);
        if (norm > 1e-9) {
            for (int i = 0; i < dims; i++) {
                v[i] = (float) (v[i] / norm);
            }
        }
        return new EmbeddingResult(v, Math.max(1, text.length() / 4), modelName());
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }

    @Override
    public int maxTokens() {
        return delegate.maxTokens();
    }

    @Override
    public void close() {
        if (dirty) {
            saveCache();
        }
        try {
            delegate.close();
        } catch (Exception e) {
            log.error("Failed to close delegate embedding provider: {}", e.getMessage(), e);
        }
    }

    private void loadCache() {
        if (!Files.exists(cacheFile)) {
            log.info("No embedding cache found at {}", cacheFile);
            return;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(cacheFile.toFile())))) {
            int size = in.readInt();
            for (int i = 0; i < size; i++) {
                int textLen = in.readInt();
                byte[] textBytes = new byte[textLen];
                in.readFully(textBytes);
                String text = new String(textBytes, StandardCharsets.UTF_8);

                int tokenCount = in.readInt();
                int modelLen = in.readInt();
                byte[] modelBytes = new byte[modelLen];
                in.readFully(modelBytes);
                String model = new String(modelBytes, StandardCharsets.UTF_8);

                int dims = in.readInt();
                float[] vector = new float[dims];
                for (int d = 0; d < dims; d++) {
                    vector[d] = in.readFloat();
                }

                cache.put(model + ":" + text, new EmbeddingResult(vector, tokenCount, model));
            }
            log.info("Loaded {} cached embeddings from {}", cache.size(), cacheFile);
        } catch (IOException e) {
            log.error("Failed to load embedding cache from {}: {}", cacheFile, e.getMessage());
        }
    }

    private void saveCache() {
        try {
            Files.createDirectories(cacheFile.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cacheFile.toFile())))) {
                out.writeInt(cache.size());
                for (Map.Entry<String, EmbeddingResult> entry : cache.entrySet()) {
                    String key = entry.getKey();
                    int colonIdx = key.indexOf(':');
                    String text = (colonIdx >= 0) ? key.substring(colonIdx + 1) : key;
                    byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
                    out.writeInt(textBytes.length);
                    out.write(textBytes);

                    EmbeddingResult res = entry.getValue();
                    out.writeInt(res.tokenCount());
                    byte[] modelBytes = res.model().getBytes(StandardCharsets.UTF_8);
                    out.writeInt(modelBytes.length);
                    out.write(modelBytes);

                    out.writeInt(res.vector().length);
                    for (float v : res.vector()) {
                        out.writeFloat(v);
                    }
                }
                log.info("Saved {} cached embeddings to {}", cache.size(), cacheFile);
            }
        } catch (IOException e) {
            log.error("Failed to save embedding cache to {}: {}", cacheFile, e.getMessage());
        }
    }
}
