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
package com.spectrayan.spector.synapse.agent.graph.nodes;

import com.spectrayan.spector.memory.model.enactment.EnactMode;
import com.spectrayan.spector.memory.model.enactment.Enactment;
import com.spectrayan.spector.memory.model.enactment.SituationFrame;
import com.spectrayan.spector.synapse.agent.enactment.EnactmentService;
import com.spectrayan.spector.synapse.agent.graph.CognitiveState;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * ENACT node — executes the persona enactment cognitive loop inside a StateGraph (ADR-0032).
 */
public final class EnactNode implements NodeAction<CognitiveState> {

    private static final Logger log = LoggerFactory.getLogger(EnactNode.class);

    private final EnactmentService enactmentService;
    private final String defaultSoulId;

    public EnactNode(EnactmentService enactmentService, String defaultSoulId) {
        this.enactmentService = Objects.requireNonNull(enactmentService, "enactmentService must not be null");
        this.defaultSoulId = (defaultSoulId != null) ? defaultSoulId : "default";
    }

    @Override
    public Map<String, Object> apply(CognitiveState state) {
        String problem = state.query();
        if (problem.isBlank()) {
            problem = state.originalQuery();
        }

        String soulId = state.actingSoulId();
        if (soulId.isBlank()) {
            soulId = defaultSoulId;
        }

        EnactMode mode = EnactMode.REACT;
        try {
            mode = EnactMode.valueOf(state.enactMode().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception ignored) {
        }

        String namespace = state.namespace();
        if (namespace.isBlank()) {
            namespace = "default";
        }

        SituationFrame situation = SituationFrame.of(problem);
        Enactment enactment = enactmentService.enact(situation, namespace, soulId, mode);

        log.debug("EnactNode executed: soul={}, mode={}, confidence={}", soulId, mode, enactment.confidence());

        return Map.of(
                "enactment", enactment,
                "answer", enactment.utterance(),
                "decision", "GENERATE"
        );
    }
}
