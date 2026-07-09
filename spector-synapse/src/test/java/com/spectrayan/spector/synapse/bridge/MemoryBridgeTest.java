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
package com.spectrayan.spector.synapse.bridge;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.synapse.memory.MemoryDto.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MemoryBridge}.
 *
 * <p>Tests both <b>stub mode</b> (no SpectorMemory bean) and
 * <b>live mode</b> (SpectorMemory mocked) to verify the MAO's
 * error-boundary, delegation, and graceful degradation behaviour.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryBridge — Unit Tests")
class MemoryBridgeTest {

    @Mock SpectorMemory mockMemory;
    @Mock ObjectProvider<SpectorMemory> presentProvider;
    @Mock ObjectProvider<SpectorMemory> absentProvider;

    MemoryBridge bridgeWithMemory;
    MemoryBridge bridgeStubMode;

    @BeforeEach
    void setUp() {
        when(presentProvider.getIfAvailable()).thenReturn(mockMemory);
        when(absentProvider.getIfAvailable()).thenReturn(null);

        bridgeWithMemory = new MemoryBridge(presentProvider);
        bridgeStubMode   = new MemoryBridge(absentProvider);
    }

    // ═══════════════════════════════════════════════════
    // Availability
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("isAvailable — true when SpectorMemory bean is present")
    void isAvailable_true_whenBeanPresent() {
        assertThat(bridgeWithMemory.isAvailable()).isTrue();
        assertThat(bridgeWithMemory.engine()).isSameAs(mockMemory);
    }

    @Test
    @DisplayName("isAvailable — false when SpectorMemory bean is absent (stub mode)")
    void isAvailable_false_inStubMode() {
        assertThat(bridgeStubMode.isAvailable()).isFalse();
        assertThat(bridgeStubMode.engine()).isNull();
    }

    // ═══════════════════════════════════════════════════
    // store — stub mode
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("store — stub mode returns stub StoreResponse")
    void store_stubMode_returnsStubResponse() {
        var request = new StoreRequest("test content", List.of("tag1"), null, null);
        var result = bridgeStubMode.store(request);

        assertThat(result.id()).startsWith("stub-");
        assertThat(result.text()).isEqualTo("test content");
        assertThat(result.message()).contains("stub mode");
        verifyNoInteractions(mockMemory);
    }

    // ═══════════════════════════════════════════════════
    // store — live mode
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("store — live mode calls memory.remember and returns id")
    @SuppressWarnings("unchecked")
    void store_liveMode_callsRemember() {
        // The 3-arg (text, type, source, tags) overload returns CompletableFuture<String>
        var futureMem = CompletableFuture.completedFuture("mem-xyz");
        // Use doReturn to bypass overload resolution inference issues
        doReturn(futureMem).when(mockMemory)
                .remember(eq("knowledge about HNSW index"),
                        eq(MemoryType.SEMANTIC), eq(MemorySource.USER_STATED), any(String[].class));

        var request = new StoreRequest("knowledge about HNSW index", List.of("index", "hnsw"), null, null);
        var result = bridgeWithMemory.store(request);

        assertThat(result.id()).isEqualTo("mem-xyz");
    }

    // ═══════════════════════════════════════════════════
    // remember — stub mode
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("remember — stub mode returns AcceptedResponse without calling engine")
    void remember_stubMode_noEngineCall() {
        var req = new RememberRequest("id-1", "Some content", null, null, null, null, null, null, null, null);
        var result = bridgeStubMode.remember(req);

        assertThat(result.id()).isEqualTo("id-1");
        assertThat(result.taskId()).isNotBlank();
        verifyNoInteractions(mockMemory);
    }

    // ═══════════════════════════════════════════════════
    // forget — stub mode no-ops
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("forget — stub mode is a no-op")
    void forget_stubMode_noOp() {
        assertThatCode(() -> bridgeStubMode.forget("any-id"))
                .doesNotThrowAnyException();
        verifyNoInteractions(mockMemory);
    }

    // ═══════════════════════════════════════════════════
    // forget — live mode delegates
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("forget — live mode calls memory.forget")
    void forget_liveMode_delegates() {
        bridgeWithMemory.forget("mem-1");
        verify(mockMemory).forget("mem-1");
    }

    // ═══════════════════════════════════════════════════
    // reinforce
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("reinforce — stub mode no-op")
    void reinforce_stubMode_noOp() {
        assertThatCode(() -> bridgeStubMode.reinforce("id", 50))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("reinforce — live mode calls memory.reinforce with byte valence")
    void reinforce_liveMode_delegates() {
        bridgeWithMemory.reinforce("mem-2", 100);
        verify(mockMemory).reinforce("mem-2", (byte) 100);
    }

    // ═══════════════════════════════════════════════════
    // suppress / unsuppress
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("suppress with reason — live mode calls memory.suppress(id, reason)")
    void suppress_withReason_callsOverload() {
        bridgeWithMemory.suppress("mem-3", "outdated");
        verify(mockMemory).suppress("mem-3", "outdated");
    }

    @Test
    @DisplayName("suppress without reason — live mode calls memory.suppress(id)")
    void suppress_noReason_callsSimple() {
        bridgeWithMemory.suppress("mem-3", null);
        verify(mockMemory).suppress("mem-3");
    }

    @Test
    @DisplayName("unsuppress — live mode delegates")
    void unsuppress_liveMode_delegates() {
        bridgeWithMemory.unsuppress("mem-3");
        verify(mockMemory).unsuppress("mem-3");
    }

    // ═══════════════════════════════════════════════════
    // resolve
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("markResolved — live mode delegates")
    void resolve_liveMode_delegates() {
        bridgeWithMemory.markResolved("mem-4");
        verify(mockMemory).markResolved("mem-4");
    }

    @Test
    @DisplayName("markUnresolved — live mode delegates")
    void unresolve_liveMode_delegates() {
        bridgeWithMemory.markUnresolved("mem-4");
        verify(mockMemory).markUnresolved("mem-4");
    }

    // ═══════════════════════════════════════════════════
    // recall
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("recall — stub mode returns empty list")
    void recall_stubMode_empty() {
        var result = bridgeStubMode.recall(new RecallRequest("Java", 5, null));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("recall — live mode maps CognitiveResult to RecallResult")
    void recall_liveMode_mapsCognitiveResult() {
        var cogResult = mock(CognitiveResult.class);
        when(cogResult.id()).thenReturn("id1");
        when(cogResult.text()).thenReturn("Virtual threads in Java 25");
        when(cogResult.memoryType()).thenReturn(MemoryType.SEMANTIC);
        when(cogResult.score()).thenReturn(0.92f);
        when(cogResult.ageDays()).thenReturn(1.5f);
        when(cogResult.synapticTags()).thenReturn(new String[]{"java"});
        when(mockMemory.recall("Java concurrency")).thenReturn(List.of(cogResult));

        var results = bridgeWithMemory.recall(new RecallRequest("Java concurrency", 10, null));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("id1");
        assertThat(results.get(0).text()).isEqualTo("Virtual threads in Java 25");
        assertThat(results.get(0).cognitiveScore()).isCloseTo(0.92, offset(0.001));
    }

    // ═══════════════════════════════════════════════════
    // reflect
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("reflect — stub mode returns zero counts")
    void reflect_stubMode_zeroReport() {
        var result = bridgeStubMode.reflect();
        assertThat(result.tombstonedCount()).isEqualTo(0);
        assertThat(result.message()).contains("not available");
    }

    @Test
    @DisplayName("reflect — live mode calls memory.reflect and maps report")
    void reflect_liveMode_mapsReport() {
        var report = mock(ReflectReport.class);
        when(report.tombstonedCount()).thenReturn(3);
        when(report.duration()).thenReturn(Duration.ofMillis(150));
        when(mockMemory.reflect()).thenReturn(report);

        var result = bridgeWithMemory.reflect();

        assertThat(result.tombstonedCount()).isEqualTo(3);
        assertThat(result.durationMs()).isEqualTo(150L);
    }

    // ═══════════════════════════════════════════════════
    // getStatus
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("getStatus — stub mode returns zeros")
    void getStatus_stubMode_zeros() {
        var result = bridgeStubMode.getStatus();
        assertThat(result.totalMemories()).isEqualTo(0);
        assertThat(result.tierCounts().values()).allSatisfy(v -> assertThat(v).isEqualTo(0));
    }

    @Test
    @DisplayName("getStatus — live mode returns real counts")
    void getStatus_liveMode_realCounts() {
        when(mockMemory.totalMemories()).thenReturn(50);
        when(mockMemory.memoryCount(MemoryType.WORKING)).thenReturn(5);
        when(mockMemory.memoryCount(MemoryType.EPISODIC)).thenReturn(20);
        when(mockMemory.memoryCount(MemoryType.SEMANTIC)).thenReturn(20);
        when(mockMemory.memoryCount(MemoryType.PROCEDURAL)).thenReturn(5);

        var result = bridgeWithMemory.getStatus();

        assertThat(result.totalMemories()).isEqualTo(50);
        assertThat(result.tierCounts()).containsEntry("SEMANTIC", 20);
    }

    // ═══════════════════════════════════════════════════
    // safeMemoryType / safeMemorySource helpers
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("vacuum — unknown tier name falls back to SEMANTIC gracefully")
    void vacuum_unknownTier_fallsBackToSemantic() {
        // The bridge calls memory.admin().vacuum(type) — mock admin() to return a mock admin
        var mockAdmin = mock(com.spectrayan.spector.memory.SpectorMemoryAdmin.class);
        when(mockMemory.admin()).thenReturn(mockAdmin);

        // Should not throw for unknown tier name (falls back to SEMANTIC)
        assertThatCode(() -> bridgeWithMemory.vacuum("INVALID_TIER"))
                .doesNotThrowAnyException();

        verify(mockAdmin).vacuum(MemoryType.SEMANTIC);
    }
}
