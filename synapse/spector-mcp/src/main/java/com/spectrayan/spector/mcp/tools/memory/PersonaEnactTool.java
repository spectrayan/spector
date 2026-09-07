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
package com.spectrayan.spector.mcp.tools.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.aisme.enactment.EnactmentConfig;
import com.spectrayan.spector.memory.aisme.enactment.EnactmentEngine;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.enactment.EnactMode;
import com.spectrayan.spector.memory.model.enactment.Enactment;
import com.spectrayan.spector.memory.model.enactment.SituationFrame;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * MCP tool: {@code persona_enact} — executes Dual-Process Persona Enactment over SpectorMemory (ADR-0032).
 */
public final class PersonaEnactTool extends MemoryToolHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public static final String NAME = "persona_enact";

    /**
     * Functional interface allowing Synapse EnactmentService to handle persona enactment
     * without introducing a circular module dependency from spector-mcp to spector-synapse.
     */
    @FunctionalInterface
    public interface Enactor {
        Enactment enact(SituationFrame situation, String namespace, String actingSoulId, EnactMode mode) throws Exception;
    }

    private final Enactor enactor;
    private final EnactmentConfig enactmentConfig;

    public PersonaEnactTool(SpectorMemory memory) {
        this(memory, (Enactor) null, EnactmentConfig.defaultConfig());
    }

    public PersonaEnactTool(SpectorMemory memory, EnactmentConfig enactmentConfig) {
        this(memory, (Enactor) null, enactmentConfig);
    }

    public PersonaEnactTool(SpectorMemory memory, Enactor enactor) {
        this(memory, enactor, EnactmentConfig.defaultConfig());
    }

    public PersonaEnactTool(SpectorMemory memory, Enactor enactor, EnactmentConfig enactmentConfig) {
        super(NAME, memory);
        this.enactor = enactor;
        this.enactmentConfig = (enactmentConfig != null) ? enactmentConfig : EnactmentConfig.defaultConfig();
    }

    /** Enterprise constructor: resolves memory per-request for tenant isolation. */
    public PersonaEnactTool(Supplier<SpectorMemory> memoryResolver) {
        this(memoryResolver, (Enactor) null, EnactmentConfig.defaultConfig());
    }

    public PersonaEnactTool(Supplier<SpectorMemory> memoryResolver, EnactmentConfig enactmentConfig) {
        this(memoryResolver, (Enactor) null, enactmentConfig);
    }

    public PersonaEnactTool(Supplier<SpectorMemory> memoryResolver, Enactor enactor) {
        this(memoryResolver, enactor, EnactmentConfig.defaultConfig());
    }

    public PersonaEnactTool(Supplier<SpectorMemory> memoryResolver, Enactor enactor, EnactmentConfig enactmentConfig) {
        super(NAME, memoryResolver);
        this.enactor = enactor;
        this.enactmentConfig = (enactmentConfig != null) ? enactmentConfig : EnactmentConfig.defaultConfig();
    }

    public Enactor enactor() {
        return enactor;
    }

    public EnactmentConfig enactmentConfig() {
        return enactmentConfig;
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory, Map<String, Object> args) throws Exception {
        if (enactor == null) {
            throw new IllegalStateException(
                    "Persona enactment via MCP requires EnactmentService to enforce soul identity and namespace isolation. "
                    + "Calling EnactmentEngine directly without EnactmentService is not permitted (ADR-0032 Invariant I6).");
        }

        String problem = requireString(args, "problem");
        String modeStr = optionalString(args, "mode", "REACT");
        String actingSoulId = optionalString(args, "acting_soul_id", "default");
        String namespace = optionalString(args, "namespace", "default");

        EnactMode mode = EnactMode.REACT;
        try {
            mode = EnactMode.valueOf(modeStr.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
        }

        SituationFrame situation = SituationFrame.of(problem);
        Enactment enactment = enactor.enact(situation, namespace, actingSoulId, mode);

        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(enactment);
        return textResult(json);
    }
}
