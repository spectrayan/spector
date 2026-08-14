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
package com.spectrayan.spector.provider.embedding.generic;

import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.provider.embedding.SparseEmbeddingProvider;
import com.spectrayan.spector.provider.embedding.SparseEmbeddingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Dense-derived sparse embedding provider.
 * Simulates sparse term weights (SPLADE) from any standard dense embedding provider.
 */
public class DenseDerivedSparseProvider implements SparseEmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(DenseDerivedSparseProvider.class);
    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[\\s\\p{Punct}]+");
    private static final int MIN_TERM_LENGTH = 2;
    private static final int MAX_TERMS = 200;

    private final EmbeddingProvider embeddingProvider;
    private final float weightThreshold;
    private final String modelName;
    private final Map<String, float[]> termVectorCache = new java.util.concurrent.ConcurrentHashMap<>();

    public DenseDerivedSparseProvider(EmbeddingProvider embeddingProvider, float weightThreshold) {
        this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "embeddingProvider");
        this.weightThreshold = weightThreshold;
        this.modelName = "dense-derived-splade/" + embeddingProvider.modelName();
        log.info("DenseDerivedSparseProvider initialized: model={}, threshold={}", modelName, weightThreshold);
    }

    public DenseDerivedSparseProvider(EmbeddingProvider embeddingProvider) {
        this(embeddingProvider, 0.1f);
    }

    @Override
    public SparseEmbeddingResult encode(String text) {
        if (text == null || text.isBlank()) {
            return new SparseEmbeddingResult(Map.of(), 0, modelName);
        }

        // 1. Generate full dense vector (D)
        EmbeddingResult docResult = embeddingProvider.embed(text);
        float[] docVector = docResult.vector();

        // 2. Tokenize into unique candidate terms
        List<String> terms = tokenize(text);
        if (terms.isEmpty()) {
            return new SparseEmbeddingResult(Map.of(), docResult.tokenCount(), modelName);
        }

        // 3. Resolve terms from cache, batch-embed only cache misses
        List<String> missingTerms = new ArrayList<>();
        for (String term : terms) {
            if (!termVectorCache.containsKey(term)) {
                missingTerms.add(term);
            }
        }
        if (!missingTerms.isEmpty()) {
            List<EmbeddingResult> missingResults = embeddingProvider.embedBatch(missingTerms);
            for (int i = 0; i < missingTerms.size(); i++) {
                termVectorCache.put(missingTerms.get(i), missingResults.get(i).vector());
            }
        }

        // 4. Calculate cosine similarity weights
        Map<String, Float> weights = new HashMap<>();
        for (String term : terms) {
            float[] termVector = termVectorCache.get(term);
            if (termVector != null) {
                float cosine = cosineSimilarity(termVector, docVector);
                float sim = Math.max(0.0f, cosine);
                if (sim >= weightThreshold) {
                    weights.put(term, sim);
                }
            }
        }

        return new SparseEmbeddingResult(weights, docResult.tokenCount(), modelName);
    }

    @Override
    public List<SparseEmbeddingResult> encodeBatch(List<String> texts) {
        Objects.requireNonNull(texts, "texts must not be null");
        List<SparseEmbeddingResult> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(encode(text));
        }
        return results;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public int vocabularySize() {
        return 50_000;
    }

    public EmbeddingProvider embeddingProvider() {
        return embeddingProvider;
    }

    private List<String> tokenize(String text) {
        String[] parts = TOKEN_SPLITTER.split(text.toLowerCase());
        Set<String> uniqueTerms = new LinkedHashSet<>();
        for (String part : parts) {
            if (part.length() >= MIN_TERM_LENGTH) {
                uniqueTerms.add(part);
                if (uniqueTerms.size() >= MAX_TERMS) {
                    break;
                }
            }
        }
        return new ArrayList<>(uniqueTerms);
    }

    private float cosineSimilarity(float[] v1, float[] v2) {
        if (v1.length != v2.length) return 0.0f;
        double dot = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }
        return norm1 > 0 && norm2 > 0 ? (float) (dot / (Math.sqrt(norm1) * Math.sqrt(norm2))) : 0.0f;
    }
}
