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
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.ReflectReport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MemoryAccessObject}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryAccessObject — Unit Tests")
class MemoryAccessObjectTest {

    @Mock SpectorMemory mockMemory;
    @Mock ObjectProvider<SpectorMemory> presentProvider;
    @Mock ObjectProvider<SpectorMemory> absentProvider;

    MemoryAccessObject maoWithMemory;
    MemoryAccessObject maoStubMode;

    @BeforeEach
    void setUp() {
        when(presentProvider.getIfAvailable()).thenReturn(mockMemory);
        when(absentProvider.getIfAvailable()).thenReturn(null);

        maoWithMemory = new MemoryAccessObject(presentProvider);
        maoStubMode   = new MemoryAccessObject(absentProvider);
    }

    @Test
    @DisplayName("isAvailable — true when SpectorMemory bean is present")
    void isAvailable_true_whenBeanPresent() {
        assertThat(maoWithMemory.isAvailable()).isTrue();
        assertThat(maoWithMemory.engine()).isSameAs(mockMemory);
    }

    @Test
    @DisplayName("isAvailable — false when SpectorMemory bean is absent (stub mode)")
    void isAvailable_false_inStubMode() {
        assertThat(maoStubMode.isAvailable()).isFalse();
        assertThat(maoStubMode.engine()).isNull();
    }

    @Test
    @DisplayName("remember — live mode calls memory.remember and returns id")
    @SuppressWarnings("unchecked")
    void remember_liveMode_callsRemember() {
        var futureMem = CompletableFuture.completedFuture("mem-xyz");
        doReturn(futureMem).when(mockMemory)
                .remember(eq("mem-xyz"), eq("knowledge about HNSW index"),
                        eq(MemoryType.SEMANTIC), eq(MemorySource.USER_STATED), any(), any(String[].class));

        var result = maoWithMemory.remember("mem-xyz", "knowledge about HNSW index",
                MemoryType.SEMANTIC, MemorySource.USER_STATED, null, new String[]{"index", "hnsw"});

        assertThat(result).isEqualTo("mem-xyz");
    }

    @Test
    @DisplayName("forget — live mode calls memory.forget")
    void forget_liveMode_delegates() {
        maoWithMemory.forget("mem-1");
        verify(mockMemory).forget("mem-1");
    }

    @Test
    @DisplayName("reinforce — live mode calls memory.reinforce with byte valence")
    void reinforce_liveMode_delegates() {
        maoWithMemory.reinforce("mem-2", 100);
        verify(mockMemory).reinforce("mem-2", (byte) 100);
    }

    @Test
    @DisplayName("suppress with reason — live mode calls memory.suppress(id, reason)")
    void suppress_withReason_callsOverload() {
        maoWithMemory.suppress("mem-3", "outdated");
        verify(mockMemory).suppress("mem-3", "outdated");
    }

    @Test
    @DisplayName("unsuppress — live mode delegates")
    void unsuppress_liveMode_delegates() {
        maoWithMemory.unsuppress("mem-3");
        verify(mockMemory).unsuppress("mem-3");
    }

    @Test
    @DisplayName("markResolved — live mode delegates")
    void resolve_liveMode_delegates() {
        maoWithMemory.markResolved("mem-4");
        verify(mockMemory).markResolved("mem-4");
    }

    @Test
    @DisplayName("recall — live mode calls memory.recall")
    void recall_liveMode_callsRecall() {
        var cogResult = mock(CognitiveResult.class);
        when(mockMemory.recall("Java concurrency")).thenReturn(List.of(cogResult));

        var results = maoWithMemory.recall("Java concurrency");

        assertThat(results).hasSize(1);
        assertThat(results.get(0)).isSameAs(cogResult);
    }

    @Test
    @DisplayName("reflect — live mode calls memory.reflect and returns report")
    void reflect_liveMode_returnsReport() {
        var report = mock(ReflectReport.class);
        when(mockMemory.reflect()).thenReturn(report);

        var result = maoWithMemory.reflect();

        assertThat(result).isSameAs(report);
    }

    @Test
    @DisplayName("getStatus — live mode returns real counts")
    void getStatus_liveMode_realCounts() {
        when(mockMemory.totalMemories()).thenReturn(50);
        when(mockMemory.memoryCount(MemoryType.WORKING)).thenReturn(5);
        when(mockMemory.memoryCount(MemoryType.EPISODIC)).thenReturn(20);
        when(mockMemory.memoryCount(MemoryType.SEMANTIC)).thenReturn(20);
        when(mockMemory.memoryCount(MemoryType.PROCEDURAL)).thenReturn(5);

        var result = maoWithMemory.getStatus();

        assertThat(result.totalMemories()).isEqualTo(50);
        assertThat(result.tierCounts()).containsEntry("SEMANTIC", 20);
    }
}
