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
import com.spectrayan.spector.provider.embedding.TokenEmbeddingProvider;
import com.spectrayan.spector.provider.embedding.TokenEmbeddingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Dense-derived token embedding provider.
 * Simulates ColBERT multi-vector embeddings from any standard dense embedding provider.
 */
public class DenseDerivedTokenProvider implements TokenEmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(DenseDerivedTokenProvider.class);
    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[\\s\\p{Punct}]+");
    private static final int MAX_TOKENS = 128;

    private final EmbeddingProvider embeddingProvider;
    private final int tokenDimensions;
    private final String modelName;

    public DenseDerivedTokenProvider(EmbeddingProvider embeddingProvider, int tokenDimensions) {
        this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "embeddingProvider");
        this.tokenDimensions = tokenDimensions;
        this.modelName = "dense-derived-colbert/" + embeddingProvider.modelName();
        log.info("DenseDerivedTokenProvider initialized: model={}, tokenDimensions={}", modelName, tokenDimensions);
    }

    public DenseDerivedTokenProvider(EmbeddingProvider embeddingProvider) {
        this(embeddingProvider, 128);
    }

    @Override
    public TokenEmbeddingResult encode(String text) {
        if (text == null || text.isBlank()) {
            return new TokenEmbeddingResult(new float[0][0], new String[0], 0, modelName);
        }

        // Tokenize terms
        List<String> terms = tokenize(text);
        if (terms.isEmpty()) {
            return new TokenEmbeddingResult(new float[0][0], new String[0], 0, modelName);
        }

        // Batch embed terms
        List<EmbeddingResult> termResults = embeddingProvider.embedBatch(terms);

        // Project/slice terms to target token dimensions
        float[][] matrix = new float[terms.size()][tokenDimensions];
        for (int i = 0; i < terms.size(); i++) {
            float[] denseVector = termResults.get(i).vector();
            int copyLen = Math.min(tokenDimensions, denseVector.length);
            System.arraycopy(denseVector, 0, matrix[i], 0, copyLen);
            normalize(matrix[i]);
        }

        return new TokenEmbeddingResult(matrix, terms.toArray(new String[0]), terms.size(), modelName);
    }

    @Override
    public int tokenDimensions() {
        return tokenDimensions;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    public EmbeddingProvider embeddingProvider() {
        return embeddingProvider;
    }

    private List<String> tokenize(String text) {
        String[] parts = TOKEN_SPLITTER.split(text.toLowerCase());
        List<String> terms = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.length() >= 2) {
                terms.add(trimmed);
                if (terms.size() >= MAX_TOKENS) {
                    break;
                }
            }
        }
        return terms;
    }

    private void normalize(float[] v) {
        double sum = 0.0;
        for (float val : v) {
            sum += val * val;
        }
        if (sum > 0.0) {
            float norm = (float) Math.sqrt(sum);
            for (int i = 0; i < v.length; i++) {
                v[i] /= norm;
            }
        }
    }
}
