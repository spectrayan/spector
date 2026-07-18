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
package org.springframework.ai.vectorstore.spector;

import com.spectrayan.spector.client.SpectorClient;
import com.spectrayan.spector.client.SpectorConnectionException;
import com.spectrayan.spector.client.model.IngestRequest;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoringMode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.commons.error.ErrorCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring AI {@link VectorStore} implementation backed by Spector.
 *
 * <p>Supports two modes of operation:
 * <ul>
 *   <li><b>Embedded</b> — uses a local {@link SpectorMemory} instance directly</li>
 *   <li><b>Remote</b> — communicates with a remote Spector instance via {@link SpectorClient}</li>
 * </ul>
 *
 * <p>Since Spector is a vector-native engine, documents must have their embeddings
 * pre-computed and stored in metadata under the key {@code "embedding"} (as a {@code float[]}).
 * The {@link #similaritySearch(SearchRequest)} method performs similarity search using the query
 * text, routing it to Spector memory. For direct vector search, use
 * {@link #similaritySearch(float[], int, double, Filter.Expression)}.</p>
 */
public class SpectorVectorStore implements VectorStore {

    private static final Logger LOG = LoggerFactory.getLogger(SpectorVectorStore.class);

    /** Metadata key used to store the document embedding vector. */
    public static final String EMBEDDING_METADATA_KEY = "embedding";

    private final SpectorMemory memory;
    private final SpectorClient client;
    private final SpectorFilterExpressionConverter filterConverter;

    /**
     * Creates a SpectorVectorStore backed by an embedded SpectorMemory.
     */
    public SpectorVectorStore(SpectorMemory memory) {
        this.memory = memory;
        this.client = null;
        this.filterConverter = new SpectorFilterExpressionConverter();
    }

    /**
     * Creates a SpectorVectorStore backed by a remote SpectorClient.
     */
    public SpectorVectorStore(SpectorClient client) {
        this.memory = null;
        this.client = client;
        this.filterConverter = new SpectorFilterExpressionConverter();
    }

    @Override
    public void add(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        for (Document document : documents) {
            String id = document.getId();
            String content = document.getText() != null ? document.getText() : "";
            float[] embedding = extractEmbedding(document);

            if (memory != null) {
                memory.target().ingest(id, content, embedding);
            } else {
                try {
                    IngestRequest request = new IngestRequest(id, content, embedding);
                    client.ingest(request);
                } catch (SpectorConnectionException e) {
                    throw new SpectorVectorStoreException(
                            "Failed to connect to remote Spector instance: " + e.getMessage(), e);
                }
            }
        }
        LOG.debug("Added {} documents to SpectorVectorStore", documents.size());
    }

    @Override
    public void delete(List<String> idList) {
        if (idList == null || idList.isEmpty()) {
            return;
        }

        for (String id : idList) {
            if (memory != null) {
                memory.forget(id);
            } else {
                try {
                    client.delete(id);
                } catch (SpectorConnectionException e) {
                    throw new SpectorVectorStoreException(
                            "Failed to connect to remote Spector instance: " + e.getMessage(), e);
                }
            }
        }
        LOG.debug("Deleted {} documents from SpectorVectorStore", idList.size());
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "SpectorVectorStore", "filter-based deletion is not yet supported");
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        String queryText = request.getQuery();
        int topK = request.getTopK();
        Filter.Expression filterExpression = request.getFilterExpression();
        double threshold = request.getSimilarityThreshold();

        if (queryText == null || queryText.isBlank()) {
            return Collections.emptyList();
        }

        List<Document> results;
        if (memory != null) {
            var options = RecallOptions.builder()
                    .topK(topK)
                    .scoringMode(ScoringMode.SIMILARITY)
                    .build();
            var recallResults = memory.recall(queryText, options);
            results = new ArrayList<>();
            for (var r : recallResults) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("score", (double) r.score());
                metadata.put("distance", (double) r.score());
                Document doc = Document.builder()
                        .id(r.id())
                        .text(r.text())
                        .metadata(metadata)
                        .score((double) r.score())
                        .build();
                results.add(doc);
            }
            if (filterExpression != null) {
                results = applyFilter(results, filterExpression);
            }
        } else {
            LOG.debug("Text-based similarity search not supported without embedding provider; query='{}'", queryText);
            return Collections.emptyList();
        }

        // Apply similarity threshold if configured
        if (threshold > 0) {
            results = results.stream()
                    .filter(doc -> {
                        Double score = doc.getScore();
                        return score != null && score >= threshold;
                    })
                    .toList();
        }

        return results;
    }

    /**
     * Performs a direct vector similarity search using a pre-computed query embedding.
     * This bypasses the need for an embedding provider.
     *
     * @param queryEmbedding the query vector
     * @param topK           maximum number of results
     * @param threshold      minimum similarity score (0.0 accepts all)
     * @param filterExpression optional metadata filter expression
     * @return matching documents ordered by descending similarity
     */
    public List<Document> similaritySearch(float[] queryEmbedding, int topK, double threshold,
                                           Filter.Expression filterExpression) {
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            return Collections.emptyList();
        }

        List<Document> results;

        if (memory != null) {
            var options = RecallOptions.builder()
                    .topK(topK)
                    .scoringMode(ScoringMode.SIMILARITY)
                    .build();
            var recallResults = memory.admin().recallPipeline().recall(queryEmbedding, options);
            results = new ArrayList<>();
            for (var r : recallResults) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("score", (double) r.score());
                metadata.put("distance", (double) r.score());
                Document doc = Document.builder()
                        .id(r.id())
                        .text(r.text())
                        .metadata(metadata)
                        .score((double) r.score())
                        .build();
                results.add(doc);
            }
            if (filterExpression != null) {
                results = applyFilter(results, filterExpression);
            }
        } else {
            try {
                var searchRequest = com.spectrayan.spector.client.model.SearchRequest.vector(queryEmbedding, topK);
                var searchResponse = client.search(searchRequest);
                results = mapClientResults(searchResponse, filterExpression);
            } catch (SpectorConnectionException e) {
                throw new SpectorVectorStoreException(
                        "Failed to connect to remote Spector instance: " + e.getMessage(), e);
            }
        }

        // Apply similarity threshold if configured
        if (threshold > 0) {
            results = results.stream()
                    .filter(doc -> {
                        Double score = doc.getScore();
                        return score != null && score >= threshold;
                    })
                    .toList();
        }

        return results;
    }

    // ─── Private Helpers ───

    private List<Document> mapClientResults(
            com.spectrayan.spector.client.model.SearchResponse response,
            Filter.Expression filterExpression) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return Collections.emptyList();
        }

        List<Document> documents = new ArrayList<>();
        for (var result : response.getResults()) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("score", (double) result.getScore());
            metadata.put("distance", (double) result.getScore());

            Document doc = Document.builder()
                    .id(result.getId())
                    .text("")
                    .metadata(metadata)
                    .score((double) result.getScore())
                    .build();
            documents.add(doc);
        }

        // Apply filter in memory if expression is present
        if (filterExpression != null) {
            documents = applyFilter(documents, filterExpression);
        }

        return documents;
    }

    private List<Document> applyFilter(List<Document> documents, Filter.Expression expression) {
        return documents.stream()
                .filter(doc -> SpectorFilterEvaluator.evaluate(expression, doc.getMetadata()))
                .toList();
    }

    /**
     * Extracts the embedding from a document's metadata.
     * The embedding should be stored under the {@link #EMBEDDING_METADATA_KEY} key
     * as either a {@code float[]} or a {@code List<Double>}.
     */
    @SuppressWarnings("unchecked")
    private float[] extractEmbedding(Document document) {
        Object embedding = document.getMetadata().get(EMBEDDING_METADATA_KEY);
        if (embedding instanceof float[] floatArray) {
            return floatArray;
        }
        if (embedding instanceof List<?> list) {
            float[] result = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof Number num) {
                    result[i] = num.floatValue();
                }
            }
            return result;
        }
        return new float[0];
    }
}
