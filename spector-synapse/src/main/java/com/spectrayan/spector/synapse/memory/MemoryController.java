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
package com.spectrayan.spector.synapse.memory;

import com.spectrayan.spector.synapse.memory.MemoryDto.ErrorResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.PageResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallResult;
import com.spectrayan.spector.synapse.memory.MemoryDto.ReinforceRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.ReinforceResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.SearchRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.SearchResult;
import com.spectrayan.spector.synapse.memory.MemoryDto.StatsResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.StoreRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.StoreResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Memory REST API controller.
 *
 * <p>Exposes CRUD, search, recall, and reinforcement endpoints for Spector's
 * cognitive memory system.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /api/v1/memory} — Store a new memory</li>
 *   <li>{@code GET /api/v1/memory/{id}} — Get memory by ID</li>
 *   <li>{@code GET /api/v1/memory} — List memories (paginated)</li>
 *   <li>{@code DELETE /api/v1/memory/{id}} — Delete memory</li>
 *   <li>{@code POST /api/v1/memory/search} — Semantic search</li>
 *   <li>{@code POST /api/v1/memory/recall} — Cognitive recall</li>
 *   <li>{@code POST /api/v1/memory/reinforce} — Reinforce memory</li>
 *   <li>{@code GET /api/v1/memory/stats} — Memory statistics</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/memory")
public class MemoryController {

    private static final Logger log = LoggerFactory.getLogger(MemoryController.class);

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    /**
     * Store a new memory.
     */
    @PostMapping
    public ResponseEntity<StoreResponse> store(@RequestBody StoreRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        StoreResponse response = memoryService.store(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get a memory by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<MemoryResponse> get(@PathVariable String id) {
        MemoryResponse response = memoryService.get(id);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    /**
     * List memories with pagination and optional tier filter.
     */
    @GetMapping
    public ResponseEntity<PageResponse<MemoryResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String tier) {
        PageResponse<MemoryResponse> response = memoryService.list(page, pageSize, tier);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a memory by ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        boolean deleted = memoryService.delete(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /**
     * Semantic search across memories.
     */
    @PostMapping("/search")
    public ResponseEntity<List<SearchResult>> search(@RequestBody SearchRequest request) {
        if (request.query() == null || request.query().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        List<SearchResult> results = memoryService.search(request);
        return ResponseEntity.ok(results);
    }

    /**
     * Cognitive recall — retrieves memories using the full cognitive scoring pipeline.
     */
    @PostMapping("/recall")
    public ResponseEntity<List<RecallResult>> recall(@RequestBody RecallRequest request) {
        if (request.query() == null || request.query().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        List<RecallResult> results = memoryService.recall(request);
        return ResponseEntity.ok(results);
    }

    /**
     * Reinforce a memory — strengthens it and prevents decay.
     */
    @PostMapping("/reinforce")
    public ResponseEntity<ReinforceResponse> reinforce(@RequestBody ReinforceRequest request) {
        if (request.id() == null || request.id().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        ReinforceResponse response = memoryService.reinforce(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get memory statistics — total count, tier distribution, storage usage.
     */
    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> stats() {
        StatsResponse response = memoryService.stats();
        return ResponseEntity.ok(response);
    }
}
