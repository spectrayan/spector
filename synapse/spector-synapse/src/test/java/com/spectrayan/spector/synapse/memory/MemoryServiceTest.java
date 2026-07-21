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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.id.TsidGenerator;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.synapse.memory.MemoryDto.*;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryGraphResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryStatusResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryTableResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.RememberRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.ResolveRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.SuppressRequest;
import com.spectrayan.spector.synapse.platform.events.EventPublisher;

/**
 * Unit tests for {@link MemoryService}.
 *
 * <p>Post multi-user rework: {@link MemoryService} resolves the target {@code SpectorMemory}
 * on the request thread and passes it to each {@link MemoryAccessObject} data-access call.
 * In these unit tests the service is constructed without a {@code UserMemoryRegistry} or
 * memory provider, so the resolved memory is {@code null}; the DAO memory argument is matched
 * with {@code any()} while the remaining arguments preserve the original assertions.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryService — Unit Tests")
class MemoryServiceTest {

    @Mock MemoryAccessObject mao;
    @Mock EventPublisher eventPublisher;
    @Mock TsidGenerator tsid;

    MemoryService service;

    @BeforeEach
    void setUp() {
        service = new MemoryService(mao, eventPublisher, tsid);
    }

    // ═══════════════════════════════════════════════════
    // remember
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("remember — valid request triggers async ingestion")
    void remember_validRequest_triggersAsync() {
        var request = new RememberRequest("id1", "Learning Java 25 virtual threads",
                "SEMANTIC", "USER_STATED", null, null, null, null, null, null);
        when(mao.isAvailable(any())).thenReturn(true);
        when(tsid.generate()).thenReturn("task-1");

        var result = service.remember(request);

        assertThat(result.id()).isEqualTo("id1");
        assertThat(result.taskId()).isEqualTo("task-1");
    }

    @Test
    @DisplayName("remember — blank text throws IllegalArgumentException")
    void remember_blankText_throws() {
        var request = new RememberRequest(null, "   ", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.remember(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
    }

    // ═══════════════════════════════════════════════════
    // recall
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("recall — returns mapped results from DAO")
    void recall_returnsMappedResults() {
        var request = new RecallRequest("Java concurrency", 5, null);
        var results = List.of(
                new CognitiveResult("id1", "Virtual threads in Java 25", 0.9f, 2.0f, 0.0f, 0, (byte)0, MemoryType.SEMANTIC, MemorySource.OBSERVED, new String[]{"java"}, 1.0f, 1.0f),
                new CognitiveResult("id2", "Platform thread limitations", 0.7f, 5.0f, 0.0f, 0, (byte)0, MemoryType.EPISODIC, MemorySource.OBSERVED, new String[0], 1.0f, 1.0f)
        );
        when(mao.recall(any(), eq("Java concurrency"))).thenReturn(results);

        var result = service.recall(request);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo("id1");
        assertThat(result.get(0).cognitiveScore()).isCloseTo(0.9, within(0.0001));
        verify(mao).recall(any(), eq("Java concurrency"));
        verify(eventPublisher).broadcast(eq("cortex.query.trace"), any());
    }

    @Test
    @DisplayName("recall — blank query throws IllegalArgumentException")
    void recall_blankQuery_throws() {
        var request = new RecallRequest("", 5, null);

        assertThatThrownBy(() -> service.recall(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query");
    }

    // ═══════════════════════════════════════════════════
    // forget
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("forget — delegates to DAO")
    void forget_delegatesToDAO() {
        service.forget("mem-123");
        verify(mao).forget(any(), eq("mem-123"));
        verify(eventPublisher).memoryEvent("deleted", "mem-123", "Tombstoned memory");
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
    @DisplayName("reinforce — delegates to DAO")
    void reinforce_delegates() {
        service.reinforce("mem-1", 100);
        verify(mao).reinforce(any(), eq("mem-1"), eq(100));
    }

    // ═══════════════════════════════════════════════════
    // suppress / unsuppress
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("suppress — delegates reason to DAO")
    void suppress_delegates() {
        service.suppress("mem-2", new SuppressRequest(null, "outdated"));
        verify(mao).suppress(any(), eq("mem-2"), eq("outdated"));
    }

    @Test
    @DisplayName("unsuppress — delegates to DAO via suppress with UNSUPPRESS action")
    void unsuppress_delegates() {
        service.suppress("mem-2", new SuppressRequest("UNSUPPRESS", null));
        verify(mao).unsuppress(any(), eq("mem-2"));
    }

    // ═══════════════════════════════════════════════════
    // resolve / unresolve
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("resolve — delegates to DAO")
    void resolve_delegates() {
        service.resolve("mem-3", new ResolveRequest(true));
        verify(mao).markResolved(any(), eq("mem-3"));
    }

    @Test
    @DisplayName("unresolve — delegates to DAO via resolve(false)")
    void unresolve_delegates() {
        service.resolve("mem-3", new ResolveRequest(false));
        verify(mao).markUnresolved(any(), eq("mem-3"));
    }

    // ═══════════════════════════════════════════════════
    // getStatus
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("getStatus — returns DAO status")
    void getStatus_returns_status() {
        var expected = new MemoryStatusResponse(42,
                Map.of("SEMANTIC", 20, "EPISODIC", 15, "WORKING", 5, "PROCEDURAL", 2),
                100, 30, 10, 5);
        when(mao.getStatus(any())).thenReturn(expected);

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
        var mockReport = mock(ReflectReport.class);
        when(mockReport.tombstonedCount()).thenReturn(5);
        when(mockReport.consolidatedCount()).thenReturn(2);
        when(mockReport.temporalPrunedCount()).thenReturn(1);
        when(mockReport.duration()).thenReturn(Duration.ofMillis(120L));
        when(mao.reflect(any())).thenReturn(mockReport);

        var result = service.reflect();

        assertThat(result.tombstonedCount()).isEqualTo(5);
        assertThat(result.durationMs()).isEqualTo(120L);
        verify(eventPublisher).broadcast(eq("cortex.reflect.cycle"), any());
    }

    // ═══════════════════════════════════════════════════
    // getMemoryTable
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("getMemoryTable — delegates to DAO")
    void getMemoryTable_delegates() {
        var emptyResp = new MemoryTableResponse(List.of(), 0, 0, 50,
                Map.of(), Map.of());
        when(mao.getMemoryTable(any(), eq(0), eq(50), isNull(), eq(false))).thenReturn(emptyResp);

        var result = service.getMemoryTable(0, 50, null, false);

        assertThat(result.totalCount()).isEqualTo(0);
        verify(mao).getMemoryTable(any(), eq(0), eq(50), isNull(), eq(false));
    }

    // ═══════════════════════════════════════════════════
    // GRAPH API
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("getGraphOverview — delegates to DAO with capped maxNodes")
    void getGraphOverview_delegatesToDAO() {
        var expected = MemoryGraphResponse.empty(null);
        when(mao.getGraphOverview(any(), eq(100))).thenReturn(expected);

        var result = service.getGraphOverview(100);

        assertThat(result).isEqualTo(expected);
        verify(mao).getGraphOverview(any(), eq(100));
    }

    @Test
    @DisplayName("getMemoryGraph — delegates to DAO with id + depth")
    void getMemoryGraph_delegatesToDAO() {
        var expected = MemoryGraphResponse.empty("mem-1");
        when(mao.getMemoryGraph(any(), eq("mem-1"), eq(2))).thenReturn(expected);

        var result = service.getMemoryGraph("mem-1", 2);

        assertThat(result).isEqualTo(expected);
        verify(mao).getMemoryGraph(any(), eq("mem-1"), eq(2));
    }

    @Test
    @DisplayName("getTopologyStats — delegates to DAO")
    void getTopologyStats_delegatesToDAO() {
        var expected = MemoryDto.TopologyStatsResponse.empty();
        when(mao.getTopologyStats(any())).thenReturn(expected);

        var result = service.getTopologyStats();

        assertThat(result).isEqualTo(expected);
        verify(mao).getTopologyStats(any());
    }
}
