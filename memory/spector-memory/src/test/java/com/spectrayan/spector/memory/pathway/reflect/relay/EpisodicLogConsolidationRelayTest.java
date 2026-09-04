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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
    @DisplayName("Distills multiple distinct topics from session turns and stores to RememberPathway")
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
        verify(rememberPathway, Mockito.times(2)).ingestCognitiveWithHeader(any(), any(), any(), any(), any(), any(), any());
    }
}
