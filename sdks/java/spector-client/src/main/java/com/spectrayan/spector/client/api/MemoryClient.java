/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.client.api;

import com.spectrayan.spector.client.exception.MemoryNotFoundException;
import com.spectrayan.spector.client.exception.SpectorExceptionHandler;
import com.spectrayan.spector.client.generated.api.MemoryApi;
import com.spectrayan.spector.client.generated.invoker.ApiClient;
import com.spectrayan.spector.client.generated.model.AcceptedResponse;
import com.spectrayan.spector.client.generated.model.BrowseRequest;
import com.spectrayan.spector.client.generated.model.BrowseResult;
import com.spectrayan.spector.client.generated.model.CompactionResult;
import com.spectrayan.spector.client.generated.model.MemoryStats;
import com.spectrayan.spector.client.generated.model.MemoryStatusResponse;
import com.spectrayan.spector.client.generated.model.MemoryTableResponse;
import com.spectrayan.spector.client.generated.model.MemoryTableRow;
import com.spectrayan.spector.client.generated.model.MemoryVectorResponse;
import com.spectrayan.spector.client.generated.model.RecallRequest;
import com.spectrayan.spector.client.generated.model.RecallResult;
import com.spectrayan.spector.client.generated.model.ReinforceByIdRequest;
import com.spectrayan.spector.client.generated.model.RememberRequest;
import com.spectrayan.spector.client.generated.model.ResolveRequest;
import com.spectrayan.spector.client.generated.model.SearchRequest;
import com.spectrayan.spector.client.generated.model.SearchResult;
import com.spectrayan.spector.client.generated.model.StoreRequest;
import com.spectrayan.spector.client.generated.model.StoreResponse;
import com.spectrayan.spector.client.generated.model.SuppressRequest;
import com.spectrayan.spector.client.generated.model.UpdateMemoryRequest;
import com.spectrayan.spector.client.generated.model.VacuumRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Ergonomic client interface for Spector Cognitive Memory operations.
 *
 * <p>Wraps the generated {@link MemoryApi} and converts low-level HTTP responses and
 * exceptions into domain-friendly types and un-checked exceptions.</p>
 */
public class MemoryClient {

    private final MemoryApi memoryApi;

    public MemoryClient(ApiClient apiClient) {
        this(new MemoryApi(Objects.requireNonNull(apiClient, "apiClient must not be null")));
    }

    public MemoryClient(MemoryApi memoryApi) {
        this.memoryApi = Objects.requireNonNull(memoryApi, "memoryApi must not be null");
    }

    /**
     * Store a cognitive memory synchronously (standard store).
     *
     * @param request the store request specification
     * @return the store response with the assigned ID and status
     */
    public StoreResponse store(StoreRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return SpectorExceptionHandler.execute(() -> memoryApi.storeMemory(request));
    }

    /**
     * Store a cognitive memory synchronously with simple parameters.
     *
     * @param text the text content of the memory
     * @param tags optional list of categorical tags
     * @return the store response
     */
    public StoreResponse store(String text, List<String> tags) {
        StoreRequest request = new StoreRequest().text(text).tags(tags != null ? tags : Collections.emptyList());
        return store(request);
    }

    /**
     * Remember a memory asynchronously with cognitive scoring hints.
     *
     * @param request the remember request specification
     * @return 202 Accepted response
     */
    public AcceptedResponse remember(RememberRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return SpectorExceptionHandler.execute(() -> memoryApi.rememberMemory(request));
    }

    /**
     * Remember a memory asynchronously with simple parameters.
     *
     * @param text the text content of the memory
     * @param tier the biological memory tier (WORKING, EPISODIC, SEMANTIC, PROCEDURAL)
     * @param tags optional list of categorical tags
     * @return 202 Accepted response
     */
    public AcceptedResponse remember(String text, String tier, List<String> tags) {
        String tagString = (tags != null && !tags.isEmpty()) ? String.join(",", tags) : null;
        RememberRequest request = new RememberRequest()
                .text(text)
                .tier(tier)
                .tags(tagString);
        return remember(request);
    }

    /**
     * Recall memories using fused cognitive scoring (vector + Hebbian + temporal).
     *
     * @param request the recall request specification
     * @return list of recall results ordered by cognitive score
     */
    public List<RecallResult> recall(RecallRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return SpectorExceptionHandler.execute(() -> memoryApi.recallMemories(request));
    }

    /**
     * Recall memories using query string and result limit.
     *
     * @param query the search query
     * @param topK  maximum number of memories to return
     * @return list of recall results
     */
    public List<RecallResult> recall(String query, int topK) {
        RecallRequest request = new RecallRequest().query(query).topK(topK);
        return recall(request);
    }

    /**
     * Semantic similarity search (pure vector search).
     *
     * @param request search request specification
     * @return list of search results
     */
    public List<SearchResult> search(SearchRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return SpectorExceptionHandler.execute(() -> memoryApi.searchMemories(request));
    }

    /**
     * Semantic similarity search using query string and result limit.
     *
     * @param query search query
     * @param topK  maximum results
     * @return list of search results
     */
    public List<SearchResult> search(String query, int topK) {
        SearchRequest request = new SearchRequest().query(query).topK(topK);
        return search(request);
    }

    /**
     * Retrieve a memory by ID. Throws {@link MemoryNotFoundException} if not found.
     *
     * @param id memory ID
     * @return the memory record
     * @throws MemoryNotFoundException if the memory does not exist
     */
    public MemoryTableRow get(String id) {
        Objects.requireNonNull(id, "id must not be null");
        return SpectorExceptionHandler.execute(() -> memoryApi.getMemoryById(id), id);
    }

    /**
     * Find a memory by ID, returning {@link Optional#empty()} if not found.
     *
     * @param id memory ID
     * @return optional containing the memory record if found
     */
    public Optional<MemoryTableRow> find(String id) {
        try {
            return Optional.ofNullable(get(id));
        } catch (MemoryNotFoundException e) {
            return Optional.empty();
        }
    }

    /**
     * Update an existing memory's content or tags.
     *
     * @param id      memory ID
     * @param request update specification
     */
    public void update(String id, UpdateMemoryRequest request) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(request, "request must not be null");
        SpectorExceptionHandler.executeVoid(() -> memoryApi.updateMemory(id, request), id);
    }

    /**
     * Tombstone (forget) a memory by ID.
     *
     * @param id memory ID
     */
    public void forget(String id) {
        Objects.requireNonNull(id, "id must not be null");
        SpectorExceptionHandler.executeVoid(() -> memoryApi.forgetMemory(id), id);
    }

    /**
     * Reinforce a memory via Long-Term Potentiation (LTP).
     *
     * @param id      memory ID
     * @param valence reinforcement valence (-1 for negative, +1 for positive)
     */
    public void reinforce(String id, int valence) {
        Objects.requireNonNull(id, "id must not be null");
        ReinforceByIdRequest request = new ReinforceByIdRequest().valence(valence);
        SpectorExceptionHandler.executeVoid(() -> memoryApi.reinforceMemory(id, request), id);
    }

    /**
     * Suppress a memory from recall results.
     *
     * @param id     memory ID
     * @param reason reason for suppression
     */
    public void suppress(String id, String reason) {
        Objects.requireNonNull(id, "id must not be null");
        SuppressRequest request = new SuppressRequest().action("suppress").reason(reason);
        SpectorExceptionHandler.executeVoid(() -> memoryApi.suppressMemory(id, request), id);
    }

    /**
     * Unsuppress a memory, restoring it to recall consideration.
     *
     * @param id memory ID
     */
    public void unsuppress(String id) {
        Objects.requireNonNull(id, "id must not be null");
        SuppressRequest request = new SuppressRequest().action("unsuppress");
        SpectorExceptionHandler.executeVoid(() -> memoryApi.suppressMemory(id, request), id);
    }

    /**
     * Resolve a memory (Zeigarnik closure).
     *
     * @param id memory ID
     */
    public void resolve(String id) {
        Objects.requireNonNull(id, "id must not be null");
        ResolveRequest request = new ResolveRequest().resolved(true);
        SpectorExceptionHandler.executeVoid(() -> memoryApi.resolveMemory(id, request), id);
    }

    /**
     * Unresolve a memory (reopen active tension).
     *
     * @param id memory ID
     */
    public void unresolve(String id) {
        Objects.requireNonNull(id, "id must not be null");
        ResolveRequest request = new ResolveRequest().resolved(false);
        SpectorExceptionHandler.executeVoid(() -> memoryApi.resolveMemory(id, request), id);
    }

    /**
     * Retrieve real-time memory status, tier counts, and graph node statistics.
     *
     * @return memory status response
     */
    public MemoryStatusResponse status() {
        return SpectorExceptionHandler.execute(memoryApi::getMemoryStatus);
    }

    /**
     * Retrieve health statistics.
     *
     * @return memory health stats
     */
    public MemoryStats stats() {
        return SpectorExceptionHandler.execute(memoryApi::getMemoryStats);
    }

    /**
     * Browse memories using tag-based exact matching (inverted tag index).
     *
     * @param request browse request
     * @return list of browse results
     */
    public List<BrowseResult> browse(BrowseRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return SpectorExceptionHandler.execute(() -> memoryApi.browseMemories(request));
    }

    /**
     * Browse memories matching given tags.
     *
     * @param tags tags to match
     * @return list of browse results
     */
    public List<BrowseResult> browse(List<String> tags) {
        BrowseRequest request = new BrowseRequest().tags(tags != null ? tags : Collections.emptyList());
        return browse(request);
    }

    /**
     * Get paginated memory table records.
     *
     * @param page       zero-based page index
     * @param pageSize   number of records per page
     * @param tier       optional tier filter
     * @param tombstoned whether to include tombstoned records
     * @return paginated memory table response
     */
    public MemoryTableResponse table(int page, int pageSize, String tier, boolean tombstoned) {
        return SpectorExceptionHandler.execute(() -> memoryApi.getMemoryTable(page, pageSize, tier, tombstoned));
    }

    /**
     * Retrieve INT8 quantized embedding vector for a memory.
     *
     * @param id memory ID
     * @return vector response
     */
    public MemoryVectorResponse vector(String id) {
        Objects.requireNonNull(id, "id must not be null");
        return SpectorExceptionHandler.execute(() -> memoryApi.getMemoryVector(id), id);
    }

    /**
     * Trigger manual memory consolidation.
     */
    public void consolidate() {
        SpectorExceptionHandler.executeVoid(memoryApi::consolidateMemories);
    }

    /**
     * Trigger vacuum compaction for a tier.
     *
     * @param tier target tier (or null for all tiers)
     * @return compaction result
     */
    public CompactionResult vacuum(String tier) {
        VacuumRequest request = new VacuumRequest().tier(tier);
        return SpectorExceptionHandler.execute(() -> memoryApi.vacuumMemories(request));
    }

    /**
     * Access the low-level generated {@link MemoryApi} instance.
     *
     * @return the underlying OpenAPI-generated MemoryApi
     */
    public MemoryApi raw() {
        return memoryApi;
    }
}
