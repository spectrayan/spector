/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.spectrayan.spector.connector.e2e;

import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Deterministic embedding provider for integration tests.
 *
 * <p>Produces vectors derived from the SHA-256 hash of the input text,
 * so identical content always yields identical vectors. This enables
 * reliable search/recall assertions without needing an actual ML model.</p>
 *
 * <p>Not suitable for production — vectors have no semantic meaning.</p>
 */
public class StubEmbeddingProvider implements EmbeddingProvider {

    private final int dims;
    private final AtomicInteger callCount = new AtomicInteger();

    public StubEmbeddingProvider(int dimensions) {
        if (dimensions < 1) throw new IllegalArgumentException("dimensions must be positive");
        this.dims = dimensions;
    }

    @Override
    public EmbeddingResult embed(String text) {
        callCount.incrementAndGet();
        float[] vector = hashToVector(text);
        int tokenEstimate = text.split("\\s+").length;
        return new EmbeddingResult(vector, tokenEstimate, "stub-embedding-model");
    }

    @Override
    public List<EmbeddingResult> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).collect(Collectors.toList());
    }

    @Override
    public int dimensions() {
        return dims;
    }

    @Override
    public String modelName() {
        return "stub-embedding-model";
    }

    @Override
    public int maxTokens() {
        return 8192;
    }

    @Override
    public void close() {
        // no-op
    }

    /** Returns the total number of embed() calls made. */
    public int callCount() {
        return callCount.get();
    }

    /** Resets the call counter. */
    public void resetCallCount() {
        callCount.set(0);
    }

    /**
     * Converts text to a deterministic float vector via SHA-256 hash.
     *
     * <p>The hash bytes are cycled across the vector dimensions,
     * then L2-normalized so cosine similarity works correctly.</p>
     */
    private float[] hashToVector(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));

            float[] vector = new float[dims];
            for (int i = 0; i < dims; i++) {
                // Cycle through hash bytes, map to [-1, 1]
                byte b = hash[i % hash.length];
                vector[i] = b / 128.0f;
            }

            // L2 normalize for cosine similarity
            float norm = 0;
            for (float v : vector) norm += v * v;
            norm = (float) Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < dims; i++) vector[i] /= norm;
            }

            return vector;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
