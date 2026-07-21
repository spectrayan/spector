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

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import org.mockito.Mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;

/**
 * Unit tests for {@link MemoryAccessObject}.
 *
 * <p>Post multi-user rework: the DAO is stateless with respect to memory routing.
 * The caller resolves the target {@link SpectorMemory} on the request thread and passes
 * it to each data-access method. These tests exercise both the "live" path (a resolved
 * memory is supplied) and the "stub" path (a {@code null} memory is supplied).</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryAccessObject — Unit Tests")
class MemoryAccessObjectTest {

    @Mock SpectorMemory mockMemory;

    MemoryAccessObject mao;

    @BeforeEach
    void setUp() {
        mao = new MemoryAccessObject();
    }

    @Test
    @DisplayName("isAvailable — true when a resolved SpectorMemory is supplied")
    void isAvailable_true_whenMemoryPresent() {
        assertThat(mao.isAvailable(mockMemory)).isTrue();
    }

    @Test
    @DisplayName("isAvailable — false when memory is null (stub mode)")
    void isAvailable_false_inStubMode() {
        assertThat(mao.isAvailable(null)).isFalse();
    }

    @Test
    @DisplayName("remember — live mode calls memory.remember and returns id")
    @SuppressWarnings("unchecked")
    void remember_liveMode_callsRemember() {
        var futureMem = CompletableFuture.completedFuture((Void) null);
        doReturn(futureMem).when(mockMemory)
                .remember(eq("mem-xyz"), eq("knowledge about HNSW index"),
                        eq(MemoryType.SEMANTIC), eq(MemorySource.USER_STATED), nullable(IngestionHints.class), any(String[].class));

        var result = mao.remember(mockMemory, "mem-xyz", "knowledge about HNSW index",
                MemoryType.SEMANTIC, MemorySource.USER_STATED, null, new String[]{"index", "hnsw"});

        assertThat(result).isEqualTo("mem-xyz");
    }

    @Test
    @DisplayName("forget — live mode calls memory.forget")
    void forget_liveMode_delegates() {
        mao.forget(mockMemory, "mem-1");
        verify(mockMemory).forget("mem-1");
    }

    @Test
    @DisplayName("reinforce — live mode calls memory.reinforce with byte valence")
    void reinforce_liveMode_delegates() {
        mao.reinforce(mockMemory, "mem-2", 100);
        verify(mockMemory).reinforce("mem-2", (byte) 100);
    }

    @Test
    @DisplayName("suppress with reason — live mode calls memory.suppress(id, reason)")
    void suppress_withReason_callsOverload() {
        mao.suppress(mockMemory, "mem-3", "outdated");
        verify(mockMemory).suppress("mem-3", "outdated");
    }

    @Test
    @DisplayName("unsuppress — live mode delegates")
    void unsuppress_liveMode_delegates() {
        mao.unsuppress(mockMemory, "mem-3");
        verify(mockMemory).unsuppress("mem-3");
    }

    @Test
    @DisplayName("markResolved — live mode delegates")
    void resolve_liveMode_delegates() {
        mao.markResolved(mockMemory, "mem-4");
        verify(mockMemory).markResolved("mem-4");
    }

    @Test
    @DisplayName("recall — live mode calls memory.recall")
    void recall_liveMode_callsRecall() {
        var cogResult = mock(CognitiveResult.class);
        when(mockMemory.recall("Java concurrency")).thenReturn(List.of(cogResult));

        var results = mao.recall(mockMemory, "Java concurrency");

        assertThat(results).hasSize(1);
        assertThat(results.get(0)).isSameAs(cogResult);
    }

    @Test
    @DisplayName("reflect — live mode calls memory.reflect and returns report")
    void reflect_liveMode_returnsReport() {
        var report = mock(ReflectReport.class);
        when(mockMemory.reflect()).thenReturn(report);

        var result = mao.reflect(mockMemory);

        assertThat(result).isSameAs(report);
    }

    @Test
    @DisplayName("getStatus — live mode returns real counts")
    void getStatus_liveMode_realCounts() {
        var mockAdmin = mock(com.spectrayan.spector.memory.SpectorMemoryAdmin.class);
        var graphFacade = new com.spectrayan.spector.memory.graph.CognitiveGraphFacade(
                null, null, null, null,
                new com.spectrayan.spector.memory.index.MemoryIndex());
        when(mockAdmin.graph()).thenReturn(graphFacade);
        when(mockMemory.admin()).thenReturn(mockAdmin);
        when(mockMemory.totalMemories()).thenReturn(50);
        when(mockMemory.memoryCount(MemoryType.WORKING)).thenReturn(5);
        when(mockMemory.memoryCount(MemoryType.EPISODIC)).thenReturn(20);
        when(mockMemory.memoryCount(MemoryType.SEMANTIC)).thenReturn(20);
        when(mockMemory.memoryCount(MemoryType.PROCEDURAL)).thenReturn(5);

        var result = mao.getStatus(mockMemory);

        assertThat(result.totalMemories()).isEqualTo(50);
        assertThat(result.tierCounts()).containsEntry("SEMANTIC", 20);
    }
}
