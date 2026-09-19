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
package com.spectrayan.spector.synapse.agent.chat.infrastructure;

import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.PrimedMemory;
import com.spectrayan.spector.synapse.agent.chat.policy.MemoryTagPolicy;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallResult;
import com.spectrayan.spector.synapse.memory.MemoryDto.RememberRequest;
import com.spectrayan.spector.synapse.memory.MemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Empirical Challenger Test Suite for {@link SpectorSalientMemoryAdapter}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>primeContext invokes RecallMode.OBSERVE (suppresses Hebbian LTP and decay side-effects)</li>
 *   <li>primeContext induces ZERO mutations or writes on cognitive memory engrams</li>
 *   <li>Defensive filtering purges legacy contaminated tags (session:*, role:*, type:turn)</li>
 *   <li>Resilience on engine unavailability, blank queries, and backend exceptions</li>
 *   <li>ingestSalientMemory enforces MemoryTagPolicy and sets explicit provenance</li>
 * </ul>
 * </p>
 */
@DisplayName("SpectorSalientMemoryAdapter Empirical Challenger Tests")
class SpectorSalientMemoryAdapterTest {

    private MemoryService memoryService;
    private MemoryTagPolicy tagPolicy;
    private SpectorSalientMemoryAdapter adapter;

    @BeforeEach
    void setUp() {
        memoryService = mock(MemoryService.class);
        tagPolicy = new MemoryTagPolicy();
        adapter = new SpectorSalientMemoryAdapter(memoryService, tagPolicy);

        when(memoryService.isEngineAvailable()).thenReturn(true);
    }

    @Test
    @DisplayName("primeContext strictly passes RecallMode.OBSERVE to MemoryService")
    void testPrimeContextUsesObserveMode() {
        when(memoryService.recall(any(RecallRequest.class))).thenReturn(List.of());

        adapter.primeContext("relocation plans", 5);

        ArgumentCaptor<RecallRequest> captor = ArgumentCaptor.forClass(RecallRequest.class);
        verify(memoryService).recall(captor.capture());

        RecallRequest captured = captor.getValue();
        assertThat(captured.query()).isEqualTo("relocation plans");
        assertThat(captured.topK()).isEqualTo(5);
        assertThat(captured.recallMode()).isEqualTo(RecallMode.OBSERVE.name());
    }

    @Test
    @DisplayName("EMPIRICAL PROOF: primeContext triggers ZERO mutations on memory engrams")
    void testPrimeContextZeroMutations() {
        var recallResult = new RecallResult(
                "mem-01",
                "User plans to move to Austin",
                "EPISODIC",
                0.92,
                "EPISODIC",
                "1 day ago",
                List.of("preference:city", "project:spector")
        );
        when(memoryService.recall(any(RecallRequest.class))).thenReturn(List.of(recallResult));

        // Invoke primeContext multiple times
        List<PrimedMemory> primed1 = adapter.primeContext("Austin", 5);
        List<PrimedMemory> primed2 = adapter.primeContext("Austin", 5);

        assertThat(primed1).hasSize(1);
        assertThat(primed2).hasSize(1);
        assertThat(primed1.get(0).text()).isEqualTo("User plans to move to Austin");

        // Verify that memoryService.remember, forget, or reinforce were NEVER called
        verify(memoryService, never()).remember(any(RememberRequest.class));
        verify(memoryService, never()).forget(org.mockito.ArgumentMatchers.anyString());
        verify(memoryService, never()).reinforce(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
        verify(memoryService, times(2)).recall(any(RecallRequest.class));
    }

    @Test
    @DisplayName("primeContext defensively filters out memories bearing contaminated tags")
    void testPrimeContextFiltersContaminatedTags() {
        var cleanResult = new RecallResult(
                "mem-clean",
                "User loves dark mode",
                "EPISODIC",
                0.95,
                "EPISODIC",
                "just now",
                List.of("preference:dark_mode")
        );

        var contaminatedResult1 = new RecallResult(
                "mem-bad-1",
                "User: hello\nAssistant: hi",
                "EPISODIC",
                0.90,
                "EPISODIC",
                "10 mins ago",
                List.of("session:01J98ABCDF12345", "preference:theme")
        );

        var contaminatedResult2 = new RecallResult(
                "mem-bad-2",
                "Operational turn record",
                "EPISODIC",
                0.85,
                "EPISODIC",
                "1 hour ago",
                List.of("type:turn", "role:user")
        );

        var contaminatedResult3 = new RecallResult(
                "mem-bad-3",
                "Model response data",
                "EPISODIC",
                0.80,
                "EPISODIC",
                "2 hours ago",
                List.of("model:qwen-2.5")
        );

        when(memoryService.recall(any(RecallRequest.class)))
                .thenReturn(List.of(cleanResult, contaminatedResult1, contaminatedResult2, contaminatedResult3));

        List<PrimedMemory> primed = adapter.primeContext("theme preference", 10);

        // Only the clean result should survive! Contaminated ones are dropped.
        assertThat(primed).hasSize(1);
        assertThat(primed.get(0).text()).isEqualTo("User loves dark mode");
        assertThat(primed.get(0).tags()).containsExactly("preference:dark_mode");
    }

    @Test
    @DisplayName("primeContext handles limit bounds (clamps to max 20, defaults to 5)")
    void testPrimeContextLimitClamping() {
        when(memoryService.recall(any(RecallRequest.class))).thenReturn(List.of());

        // Oversized limit: 100 clamped to 20
        adapter.primeContext("query", 100);
        ArgumentCaptor<RecallRequest> captor1 = ArgumentCaptor.forClass(RecallRequest.class);
        verify(memoryService).recall(captor1.capture());
        assertThat(captor1.getValue().topK()).isEqualTo(20);

        // Non-positive limit: 0 clamped to 5
        adapter.primeContext("query", 0);
        ArgumentCaptor<RecallRequest> captor2 = ArgumentCaptor.forClass(RecallRequest.class);
        verify(memoryService, times(2)).recall(captor2.capture());
        assertThat(captor2.getValue().topK()).isEqualTo(5);
    }

    @Test
    @DisplayName("primeContext returns empty list when engine is unavailable or query is blank")
    void testPrimeContextEngineUnavailableOrBlank() {
        when(memoryService.isEngineAvailable()).thenReturn(false);
        assertThat(adapter.primeContext("valid query", 5)).isEmpty();

        when(memoryService.isEngineAvailable()).thenReturn(true);
        assertThat(adapter.primeContext("", 5)).isEmpty();
        assertThat(adapter.primeContext("   ", 5)).isEmpty();
        assertThat(adapter.primeContext(null, 5)).isEmpty();

        verify(memoryService, never()).recall(any());
    }

    @Test
    @DisplayName("primeContext handles MemoryService backend exceptions gracefully")
    void testPrimeContextGracefulDegradationOnException() {
        when(memoryService.recall(any(RecallRequest.class)))
                .thenThrow(new RuntimeException("SIMD vector engine timeout"));

        List<PrimedMemory> result = adapter.primeContext("failing query", 5);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("ingestSalientMemory enforces MemoryTagPolicy and rejects prohibited tags")
    void testIngestSalientMemoryRejectsProhibitedTags() {
        assertThatThrownBy(() -> adapter.ingestSalientMemory(
                "Fact text",
                MemoryType.EPISODIC,
                MemorySource.USER_STATED,
                List.of("session:01J98ABC")
        )).isInstanceOf(SpectorValidationException.class);

        verify(memoryService, never()).remember(any());
    }

    @Test
    @DisplayName("ingestSalientMemory enforces MemoryTagPolicy and rejects CoT in content")
    void testIngestSalientMemoryRejectsCot() {
        assertThatThrownBy(() -> adapter.ingestSalientMemory(
                "<think>Secret thinking</think> User lives in Denver",
                MemoryType.EPISODIC,
                MemorySource.USER_STATED,
                List.of("preference:location")
        )).isInstanceOf(SpectorValidationException.class);

        verify(memoryService, never()).remember(any());
    }

    @Test
    @DisplayName("ingestSalientMemory delegates clean memories to MemoryService with proper provenance")
    void testIngestSalientMemorySuccess() {
        adapter.ingestSalientMemory(
                "User lives in Denver",
                MemoryType.EPISODIC,
                MemorySource.USER_STATED,
                List.of("preference:location", "project:spector")
        );

        ArgumentCaptor<RememberRequest> captor = ArgumentCaptor.forClass(RememberRequest.class);
        verify(memoryService).remember(captor.capture());

        RememberRequest req = captor.getValue();
        assertThat(req.text()).isEqualTo("User lives in Denver");
        assertThat(req.tier()).isEqualTo("EPISODIC");
        assertThat(req.source()).isEqualTo("USER_STATED");
        assertThat(req.tags()).isEqualTo("preference:location,project:spector");
    }
}
