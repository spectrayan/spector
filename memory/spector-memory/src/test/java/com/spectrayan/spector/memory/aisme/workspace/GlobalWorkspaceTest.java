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
package com.spectrayan.spector.memory.aisme.workspace;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link GlobalWorkspace}.
 */
class GlobalWorkspaceTest {

    @Test
    void filterForBroadcast_truncatesToCapacityAndSortsByScore() {
        GlobalWorkspace workspace = new GlobalWorkspace(3);

        List<CognitiveResult> candidates = new ArrayList<>();
        candidates.add(createResult("m1", 0.2f));
        candidates.add(createResult("m2", 0.9f));
        candidates.add(createResult("m3", 0.5f));
        candidates.add(createResult("m4", 0.8f));

        List<CognitiveResult> broadcast = workspace.filterForBroadcast(candidates);

        assertThat(broadcast).hasSize(3);
        assertThat(broadcast.get(0).id()).isEqualTo("m2"); // 0.9
        assertThat(broadcast.get(1).id()).isEqualTo("m4"); // 0.8
        assertThat(broadcast.get(2).id()).isEqualTo("m3"); // 0.5
    }

    @Test
    void updateAttentionSchema_updatesActiveSchema() {
        GlobalWorkspace workspace = new GlobalWorkspace();
        AttentionSchema custom = new AttentionSchema("EMOTION", 2.5f, 1000L, "High arousal context");

        workspace.updateAttentionSchema(custom);
        assertThat(workspace.activeSchema()).isEqualTo(custom);
    }

    @Test
    void invalidCapacity_throwsValidationException() {
        assertThatThrownBy(() -> new GlobalWorkspace(0))
                .isInstanceOf(SpectorValidationException.class);
    }

    private static CognitiveResult createResult(String id, float score) {
        return new CognitiveResult(
                id,
                "text " + id,
                score,
                1.0f,
                0.1f,
                0,
                (byte) 0,
                MemoryType.EPISODIC,
                MemorySource.USER_STATED,
                new String[0],
                1.0f,
                1.0f,
                CognitiveResult.RetrievalMode.STANDARD,
                null,
                null,
                null,
                Map.of()
        );
    }
}
