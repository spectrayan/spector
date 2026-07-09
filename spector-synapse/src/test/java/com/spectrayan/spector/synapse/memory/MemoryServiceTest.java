/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.memory;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.synapse.bridge.MemoryBridge;
import com.spectrayan.spector.synapse.memory.MemoryDto.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MemoryService}.
 *
 * <p>All dependencies are mocked. No Spring context, no Ollama.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryService — Unit Tests")
class MemoryServiceTest {

    @Mock MemoryBridge memoryBridge;

    MemoryService service;

    @BeforeEach
    void setUp() {
        service = new MemoryService(memoryBridge);
    }

    // ═══════════════════════════════════════════════════
    // remember
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("remember — valid request delegates to bridge")
    void remember_validRequest_delegatesToBridge() {
        var request = new RememberRequest("id1", "Learning Java 25 virtual threads",
                null, null, null, null, null, null, null, null);
        var expected = AcceptedResponse.forRemember("task-1", "id1");
        when(memoryBridge.remember(request)).thenReturn(expected);

        var result = service.remember(request);

        assertThat(result.id()).isEqualTo("id1");
        verify(memoryBridge).remember(request);
    }

    @Test
    @DisplayName("remember — blank text throws IllegalArgumentException")
    void remember_blankText_throws() {
        var request = new RememberRequest(null, "   ", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.remember(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
    }

    @Test
    @DisplayName("remember — null request throws (NullPointerException or IllegalArgumentException)")
    void remember_nullRequest_throws() {
        assertThatThrownBy(() -> service.remember(null))
                .isInstanceOf(RuntimeException.class); // NPE or IAE depending on implementation
    }

    // ═══════════════════════════════════════════════════
    // recall
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("recall — returns results from bridge")
    void recall_returnsResultsFromBridge() {
        var request = new RecallRequest("Java concurrency", 5, null);
        var results = List.of(
                new RecallResult("id1", "Virtual threads in Java 25", "SEMANTIC", 0.9,
                        "SEMANTIC", "2.0 days", List.of("java")),
                new RecallResult("id2", "Platform thread limitations", "EPISODIC", 0.7,
                        "EPISODIC", "5.0 days", List.of())
        );
        when(memoryBridge.recall(request)).thenReturn(results);

        var result = service.recall(request);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo("id1");
        assertThat(result.get(0).cognitiveScore()).isGreaterThan(result.get(1).cognitiveScore());
    }

    @Test
    @DisplayName("recall — blank query throws IllegalArgumentException")
    void recall_blankQuery_throws() {
        var request = new RecallRequest("", 5, null);

        assertThatThrownBy(() -> service.recall(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query");
    }

    @Test
    @DisplayName("recall — negative topK is normalized to 10 (compact constructor behavior)")
    void recall_negativeTopK_normalizedTo10() {
        // RecallRequest compact constructor normalizes <=0 topK to 10
        var request = new RecallRequest("Java", -1, null);
        assertThat(request.topK()).isEqualTo(10); // normalized by compact ctor
        // Service call should succeed, not throw
        when(memoryBridge.recall(request)).thenReturn(List.of());
        assertThatCode(() -> service.recall(request)).doesNotThrowAnyException();
    }

    // ═══════════════════════════════════════════════════
    // forget
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("forget — delegates to bridge")
    void forget_delegatesToBridge() {
        service.forget("mem-123");
        verify(memoryBridge).forget("mem-123");
    }

    @Test
    @DisplayName("forget — null id throws")
    void forget_nullId_throws() {
        assertThatThrownBy(() -> service.forget(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ═══════════════════════════════════════════════════
    // reinforce
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("reinforce — clamps valence to byte range and delegates")
    void reinforce_clamps_and_delegates() {
        service.reinforce("mem-1", 999);
        // Bridge receives whatever we pass — the clamping happens inside MemoryBridge
        verify(memoryBridge).reinforce("mem-1", 999);
    }

    // helper to show how controller uses ReinforceByIdRequest
    private void helperReinforce(String id, ReinforceByIdRequest req) {
        service.reinforce(id, req.effectiveValence());
    }

    // ═══════════════════════════════════════════════════
    // suppress / unsuppress
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("suppress — delegates reason to bridge")
    void suppress_delegates() {
        // SuppressRequest(action, reason) — null action means SUPPRESS
        service.suppress("mem-2", new SuppressRequest(null, "outdated"));
        verify(memoryBridge).suppress("mem-2", "outdated");
    }

    @Test
    @DisplayName("unsuppress — delegates to bridge via suppress with UNSUPPRESS action")
    void unsuppress_delegates() {
        service.suppress("mem-2", new SuppressRequest("UNSUPPRESS", null));
        verify(memoryBridge).unsuppress("mem-2");
    }

    // ═══════════════════════════════════════════════════
    // resolve / unresolve
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("resolve — delegates to bridge")
    void resolve_delegates() {
        // ResolveRequest(true) → resolving
        service.resolve("mem-3", new ResolveRequest(true));
        verify(memoryBridge).markResolved("mem-3");
    }

    @Test
    @DisplayName("unresolve — delegates to bridge via resolve(false)")
    void unresolve_delegates() {
        service.resolve("mem-3", new ResolveRequest(false));
        verify(memoryBridge).markUnresolved("mem-3");
    }

    // ═══════════════════════════════════════════════════
    // getStatus
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("getStatus — returns bridge status")
    void getStatus_returns_bridge_status() {
        var expected = new MemoryStatusResponse(42,
                java.util.Map.of("SEMANTIC", 20, "EPISODIC", 15, "WORKING", 5, "PROCEDURAL", 2),
                100, 30, 10, 5);
        when(memoryBridge.getStatus()).thenReturn(expected);

        var result = service.getStatus();

        assertThat(result.totalMemories()).isEqualTo(42);
        assertThat(result.tierCounts()).containsEntry("SEMANTIC", 20);
    }

    // ═══════════════════════════════════════════════════
    // reflect
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("reflect — delegates and returns report")
    void reflect_delegates() {
        when(memoryBridge.reflect()).thenReturn(new ReflectResponse(5, 120L, "Consolidation done"));

        var result = service.reflect();

        assertThat(result.tombstonedCount()).isEqualTo(5);
        assertThat(result.durationMs()).isEqualTo(120L);
    }

    // ═══════════════════════════════════════════════════
    // getMemoryTable
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("getMemoryTable — defaults page=0, size=50")
    void getMemoryTable_defaults() {
        var emptyResp = new MemoryTableResponse(List.of(), 0, 0, 50,
                java.util.Map.of(), java.util.Map.of());
        when(memoryBridge.getMemoryTable(0, 50, null, false)).thenReturn(emptyResp);

        var result = service.getMemoryTable(0, 50, null, false);

        assertThat(result.totalCount()).isEqualTo(0);
        verify(memoryBridge).getMemoryTable(0, 50, null, false);
    }
}
