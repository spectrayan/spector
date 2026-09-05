/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.pathway.reflect.relay;

import com.spectrayan.spector.memory.persist.PartitionManager;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.EpisodicMemory;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.cortex.ProceduralMemory;
import com.spectrayan.spector.memory.cortex.SemanticMemory;
import com.spectrayan.spector.memory.cortex.WorkingMemory;
import com.spectrayan.spector.memory.model.ConversationRole;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.LlmResponse;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.session.EpisodicSessionIndex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EpisodicLogConsolidationRelay: Multi-Topic Log Consolidation Tests")
class EpisodicLogConsolidationRelayTest {

    private EpisodicMemory logMemory;
    private WorkingMemory workingMemory;
    private SemanticMemory semanticMemory;
    private ProceduralMemory proceduralMemory;

    @BeforeEach
    void setUp() {
        logMemory = new EpisodicMemory(1024 * 1024);
        workingMemory = new WorkingMemory(16, 100);
        semanticMemory = new SemanticMemory(16, 100);
        proceduralMemory = new ProceduralMemory(16, 100);
    }

    @AfterEach
    void tearDown() {
        if (logMemory != null) logMemory.close();
        if (workingMemory != null) workingMemory.close();
        if (semanticMemory != null) semanticMemory.close();
        if (proceduralMemory != null) proceduralMemory.close();
    }

    @Test
    @DisplayName("Distills multiple distinct topics from session turns and stores to RememberPathway with latest timestamp")
    void testMultiTopicDistillation() {
        long sessionId = 777L;
        byte[] turn1 = "I prefer dark mode in all IDEs.".getBytes();
        byte[] turn2 = "Also, my favorite database is PostgreSQL for relational workloads.".getBytes();

        logMemory.appendTurn(ConversationRole.USER, 1, 1000L, sessionId, turn1, (short) 1, 0, 0, 0, 0L, (short) 1, SourceModality.TEXT);
        logMemory.appendTurn(ConversationRole.USER, 2, 2000L, sessionId, turn2, (short) 1, 0, 0, 0, 0L, (short) 1, SourceModality.TEXT);

        CognitiveMemoryRouter router = new CognitiveMemoryRouter(workingMemory, semanticMemory, proceduralMemory, logMemory);
        PartitionManager partitionManager = Mockito.mock(PartitionManager.class);
        PartitionHandle handle = new PartitionHandle(0, null, router, null, false);
        when(partitionManager.snapshot()).thenReturn(List.of(handle));

        RememberPathway rememberPathway = Mockito.mock(RememberPathway.class);
        LlmProvider llm = new LlmProvider() {
            @Override
            public LlmResponse generate(LlmRequest request, GenerationOptions options) {
                return new LlmResponse("- User prefers dark mode in IDEs\n- User prefers PostgreSQL for relational databases", 10, 10, "mock-llm");
            }

            @Override
            public String generate(String prompt, GenerationOptions options) {
                return "- User prefers dark mode in IDEs\n- User prefers PostgreSQL for relational databases";
            }

            @Override public boolean isAvailable() { return true; }
            @Override public String modelName() { return "mock-llm"; }
        };

        ReflectSignal signal = ReflectSignal.builder()
                .partitionManager(partitionManager)
                .rememberPathway(rememberPathway)
                .textGenerator(llm)
                .build();

        EpisodicLogConsolidationRelay relay = new EpisodicLogConsolidationRelay();
        boolean success = relay.transmit(signal);

        assertThat(success).isTrue();
        assertThat(signal.logTurnsConsolidated()).isEqualTo(2);

        ArgumentCaptor<EncodingHeader> headerCaptor = ArgumentCaptor.forClass(EncodingHeader.class);
        verify(rememberPathway, Mockito.times(2)).ingestCognitiveWithHeader(any(), any(), any(), any(), any(), any(), headerCaptor.capture());
        assertThat(headerCaptor.getAllValues()).allMatch(h -> h.timestampMs() == 2000L);
    }

    @Test
    @DisplayName("Re-consolidation excludes already consolidated turns and supplies prior-context window to LLM")
    void testReconsolidationWithPriorContextWindow() {
        long sessionId = 888L;
        EpisodicSessionIndex sessionIndex = new EpisodicSessionIndex();

        // Initial session: turns 1 and 2
        long off1 = logMemory.appendTurn(ConversationRole.USER, 1, 1000L, sessionId, "I want to design an event-driven architecture.".getBytes(), (short) 1, 0, 0, 0, 0L, (short) 1, SourceModality.TEXT);
        sessionIndex.appendTurn(sessionId, off1);
        long off2 = logMemory.appendTurn(ConversationRole.ASSISTANT, 2, 2000L, sessionId, "You can use Kafka or RabbitMQ as the message broker.".getBytes(), (short) 1, 0, 0, 0, 0L, (short) 1, SourceModality.TEXT);
        sessionIndex.appendTurn(sessionId, off2);

        CognitiveMemoryRouter router = new CognitiveMemoryRouter(workingMemory, semanticMemory, proceduralMemory, logMemory);
        PartitionManager partitionManager = Mockito.mock(PartitionManager.class);
        PartitionHandle handle = new PartitionHandle(0, null, router, null, false);
        when(partitionManager.snapshot()).thenReturn(List.of(handle));

        RememberPathway rememberPathway = Mockito.mock(RememberPathway.class);
        List<String> capturedPrompts = new ArrayList<>();
        LlmProvider llm = new LlmProvider() {
            @Override
            public LlmResponse generate(LlmRequest request, GenerationOptions options) {
                return new LlmResponse("- User wants event-driven architecture", 10, 10, "mock-llm");
            }

            @Override
            public String generate(String prompt, GenerationOptions options) {
                capturedPrompts.add(prompt);
                return "- User wants event-driven architecture";
            }

            @Override public boolean isAvailable() { return true; }
            @Override public String modelName() { return "mock-llm"; }
        };

        // First consolidation: consolidates turns 1 and 2
        ReflectSignal signal1 = ReflectSignal.builder()
                .partitionManager(partitionManager)
                .rememberPathway(rememberPathway)
                .textGenerator(llm)
                .episodicSessionIndex(sessionIndex)
                .build();

        EpisodicLogConsolidationRelay relay = new EpisodicLogConsolidationRelay();
        boolean success1 = relay.transmit(signal1);

        assertThat(success1).isTrue();
        assertThat(signal1.logTurnsConsolidated()).isEqualTo(2);
        assertThat(capturedPrompts).hasSize(1);
        assertThat(capturedPrompts.get(0)).doesNotContain("Prior Context (Already Consolidated");

        // Now add turns 3 and 4 to the SAME session
        long off3 = logMemory.appendTurn(ConversationRole.USER, 3, 5000L, sessionId, "Let's go with Kafka because of high throughput requirements.".getBytes(), (short) 1, 0, 0, 0, 0L, (short) 1, SourceModality.TEXT);
        sessionIndex.appendTurn(sessionId, off3);
        long off4 = logMemory.appendTurn(ConversationRole.ASSISTANT, 4, 6000L, sessionId, "Understood, Kafka is chosen for high throughput.".getBytes(), (short) 1, 0, 0, 0, 0L, (short) 1, SourceModality.TEXT);
        sessionIndex.appendTurn(sessionId, off4);

        // Second consolidation: should ONLY consolidate turns 3 and 4, with turns 1 and 2 as prior context!
        ReflectSignal signal2 = ReflectSignal.builder()
                .partitionManager(partitionManager)
                .rememberPathway(rememberPathway)
                .textGenerator(llm)
                .episodicSessionIndex(sessionIndex)
                .build();

        boolean success2 = relay.transmit(signal2);

        assertThat(success2).isTrue();
        assertThat(signal2.logTurnsConsolidated()).isEqualTo(2); // Only the 2 new turns consolidated!
        assertThat(capturedPrompts).hasSize(2);

        String secondPrompt = capturedPrompts.get(1);
        assertThat(secondPrompt).contains("Prior Context (Already Consolidated — DO NOT extract facts from these):");
        assertThat(secondPrompt).contains("I want to design an event-driven architecture.");
        assertThat(secondPrompt).contains("You can use Kafka or RabbitMQ");
        assertThat(secondPrompt).contains("New Turns (Extract facts ONLY from these):");
        assertThat(secondPrompt).contains("Let's go with Kafka because of high throughput");

        // Verify the new facts have timestamp 6000L (the max of turns 3 and 4)
        ArgumentCaptor<EncodingHeader> headerCaptor = ArgumentCaptor.forClass(EncodingHeader.class);
        verify(rememberPathway, Mockito.atLeast(2)).ingestCognitiveWithHeader(any(), any(), any(), any(), any(), any(), headerCaptor.capture());
        EncodingHeader latestHeader = headerCaptor.getAllValues().get(headerCaptor.getAllValues().size() - 1);
        assertThat(latestHeader.timestampMs()).isEqualTo(6000L);
    }
}
