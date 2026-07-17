/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.test;

import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;

import java.util.Random;

/**
 * Deterministic fake embedding provider for unit and integration tests.
 *
 * <p>Generates 384-dimensional vectors from text hashes â€” same text always
 * produces the same vector, enabling deterministic similarity tests without
 * any network calls.</p>
 *
 * <h3>Properties</h3>
 * <ul>
 *   <li><b>Deterministic</b>: identical text â†’ identical vector</li>
 *   <li><b>Different texts â†’ different vectors</b>: hash seeding ensures distinct outputs</li>
 *   <li><b>Normalized</b>: L2 norm â‰ˆ 1.0 (Â±0.01)</li>
 *   <li><b>Thread-safe</b>: no shared mutable state</li>
 *   <li><b>Instant</b>: no I/O or network calls</li>
 * </ul>
 */
public final class FakeEmbeddingProvider implements EmbeddingProvider {

    private static final String MODEL_NAME = "fake-384";
    private static final int DIMS = 384;

    @Override
    public EmbeddingResult embed(String text) {
        float[] vector = hashToVector(text);
        return new EmbeddingResult(vector, text.split("\\s+").length, MODEL_NAME);
    }

    @Override
    public int dimensions() {
        return DIMS;
    }

    @Override
    public String modelName() {
        return MODEL_NAME;
    }

    /**
     * Converts text to a deterministic, L2-normalized 384-dim vector.
     *
     * <p>Uses the text's hash code as a seed for a {@link Random} generator,
     * then generates Gaussian values and normalizes to unit length.</p>
     */
    private static float[] hashToVector(String text) {
        Random rng = new Random(text.hashCode() * 31L + text.length());
        float[] vector = new float[DIMS];
        float sumSq = 0f;
        for (int i = 0; i < DIMS; i++) {
            vector[i] = (float) rng.nextGaussian();
            sumSq += vector[i] * vector[i];
        }
        // L2 normalize
        float norm = (float) Math.sqrt(sumSq);
        if (norm > 0f) {
            for (int i = 0; i < DIMS; i++) {
                vector[i] /= norm;
            }
        }
        return vector;
    }
}
