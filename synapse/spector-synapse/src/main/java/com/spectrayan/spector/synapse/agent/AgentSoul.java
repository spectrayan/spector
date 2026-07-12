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
package com.spectrayan.spector.synapse.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The agent's persistent identity — its "soul."
 *
 * <h3>Biological Analog: Default Mode Network (DMN) Self-Model</h3>
 * <p>Just as the human brain's DMN maintains a persistent self-model that shapes
 * perception, memory encoding, and social cognition, the AgentSoul defines WHO
 * the agent is. This identity persists across sessions and influences how the agent
 * processes, recalls, and responds to information.</p>
 *
 * <h3>Symmetry with PersonaContext</h3>
 * <p>The user has {@code PersonaContext} (their soul). The agent has {@code AgentSoul}
 * (its soul). Both are injected into the LLM system prompt and both influence
 * cognitive scoring — PersonaContext via self-relevance boost, AgentSoul via
 * expertise-relevance boost.</p>
 *
 * <h3>Persistence</h3>
 * <p>Stored in H2 via {@link AgentSoulRepository}. The agent can self-modify its
 * soul (except ethical guardrails) with user approval via conversational
 * interaction.</p>
 *
 * <h3>Mutability Rules</h3>
 * <ul>
 *   <li><b>Mutable (with user approval)</b>: name, purpose, personality, expertise,
 *       values, emotionalBaseline, communicationStyle</li>
 *   <li><b>Immutable (agent cannot modify)</b>: ethicalGuardrails</li>
 * </ul>
 *
 * @param id                   unique identifier (UUID)
 * @param name                 the agent's name (e.g., "Aria", "Atlas")
 * @param description          brief description of the agent's role
 * @param systemPrompt         system-level instructions for the LLM
 * @param purpose              the agent's core purpose (e.g., "ABA therapy companion")
 * @param personality          free-text personality description
 * @param expertiseDomains     domains the agent specializes in
 * @param coreValues           the agent's guiding values
 * @param ethicalGuardrails    immutable ethical constraints (agent CANNOT modify these)
 * @param emotionalBaseline    the agent's default emotional state
 * @param communicationStyle   how the agent communicates (e.g., "warm and encouraging")
 * @param model                preferred LLM model (e.g., "qwen3.5:latest")
 * @param tools                list of enabled tool names
 * @param expertiseEmbedding   pre-computed embedding of expertise domains (for scoring)
 * @param purposeEmbedding     pre-computed embedding of purpose text (for scoring)
 * @param createdAt            creation timestamp
 * @param updatedAt            last update timestamp
 */
public record AgentSoul(
        // Identity
        String id,
        String name,
        String description,
        String systemPrompt,
        String purpose,
        String personality,

        // Expertise
        List<String> expertiseDomains,

        // Values & Ethics
        List<String> coreValues,
        List<String> ethicalGuardrails,

        // Emotional baseline
        EmotionalBaseline emotionalBaseline,

        // Communication
        String communicationStyle,

        // Runtime configuration
        String model,
        List<String> tools,

        // Pre-computed embeddings (for expertise-relevance scoring)
        float[] expertiseEmbedding,
        float[] purposeEmbedding,

        // Timestamps
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Compact constructor — enforces immutability and safe defaults.
     */
    public AgentSoul {
        expertiseDomains = expertiseDomains != null
                ? Collections.unmodifiableList(expertiseDomains) : List.of();
        coreValues = coreValues != null
                ? Collections.unmodifiableList(coreValues) : List.of();
        ethicalGuardrails = ethicalGuardrails != null
                ? Collections.unmodifiableList(ethicalGuardrails) : List.of();
        tools = tools != null
                ? Collections.unmodifiableList(tools) : List.of();

        if (emotionalBaseline == null) emotionalBaseline = EmotionalBaseline.NEUTRAL;

        // Defensive copy of embeddings
        if (expertiseEmbedding != null) {
            expertiseEmbedding = Arrays.copyOf(expertiseEmbedding, expertiseEmbedding.length);
        }
        if (purposeEmbedding != null) {
            purposeEmbedding = Arrays.copyOf(purposeEmbedding, purposeEmbedding.length);
        }
    }

    /**
     * No soul configured — agent operates as a generic assistant.
     */
    public static final AgentSoul NONE = new AgentSoul(
            null, null, null, null, null, null,
            List.of(), List.of(), List.of(),
            EmotionalBaseline.NEUTRAL,
            null, null, List.of(),
            null, null, null, null);

    /**
     * Creates a minimal agent soul with defaults (backwards compatibility).
     */
    public static AgentSoul of(String id, String name, String systemPrompt) {
        Instant now = Instant.now();
        return new AgentSoul(id, name, null, systemPrompt,
                null, null,
                List.of(), List.of(), List.of(),
                EmotionalBaseline.NEUTRAL,
                null, null, List.of(),
                null, null, now, now);
    }

    /**
     * Returns true if this soul has any meaningful identity data.
     */
    public boolean isPresent() {
        return (name != null && !name.isBlank())
                || (purpose != null && !purpose.isBlank())
                || !expertiseDomains.isEmpty();
    }

    /**
     * Returns true if this soul has pre-computed embeddings for expertise matching.
     */
    public boolean hasEmbeddings() {
        return (expertiseEmbedding != null && expertiseEmbedding.length > 0)
                || (purposeEmbedding != null && purposeEmbedding.length > 0);
    }

    /**
     * The agent's default emotional state — influences recall bias.
     *
     * <p>A "warm" agent with positive valence baseline will prefer recalling
     * memories that led to positive outcomes for the user. A "clinical" agent
     * with neutral baseline will recall objectively.</p>
     *
     * @param defaultValence  emotional baseline (-128 to +127, 0 = neutral)
     * @param defaultArousal  activation baseline (0-255, 128 = moderate)
     */
    public record EmotionalBaseline(byte defaultValence, byte defaultArousal) {

        /** Neutral baseline — no emotional bias in recall. */
        public static final EmotionalBaseline NEUTRAL = new EmotionalBaseline((byte) 0, (byte) 128);

        /** Warm baseline — slight positive bias in recall (therapy/companion agents). */
        public static final EmotionalBaseline WARM = new EmotionalBaseline((byte) 30, (byte) 100);

        /** Energetic baseline — high arousal, positive (coaching/motivational agents). */
        public static final EmotionalBaseline ENERGETIC = new EmotionalBaseline((byte) 40, (byte) 200);
    }

    /**
     * Creates a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link AgentSoul}.
     */
    public static final class Builder {
        private String id;
        private String name;
        private String description;
        private String systemPrompt;
        private String purpose;
        private String personality;
        private List<String> expertiseDomains = new ArrayList<>();
        private List<String> coreValues = new ArrayList<>();
        private List<String> ethicalGuardrails = new ArrayList<>();
        private EmotionalBaseline emotionalBaseline;
        private String communicationStyle;
        private String model;
        private List<String> tools = new ArrayList<>();
        private float[] expertiseEmbedding;
        private float[] purposeEmbedding;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public Builder purpose(String purpose) { this.purpose = purpose; return this; }
        public Builder personality(String personality) { this.personality = personality; return this; }
        public Builder communicationStyle(String style) { this.communicationStyle = style; return this; }
        public Builder model(String model) { this.model = model; return this; }

        public Builder expertiseDomain(String domain) {
            this.expertiseDomains.add(domain);
            return this;
        }
        public Builder expertiseDomains(List<String> domains) {
            this.expertiseDomains = new ArrayList<>(domains);
            return this;
        }

        public Builder coreValue(String value) {
            this.coreValues.add(value);
            return this;
        }
        public Builder coreValues(List<String> values) {
            this.coreValues = new ArrayList<>(values);
            return this;
        }

        public Builder ethicalGuardrail(String guardrail) {
            this.ethicalGuardrails.add(guardrail);
            return this;
        }
        public Builder ethicalGuardrails(List<String> guardrails) {
            this.ethicalGuardrails = new ArrayList<>(guardrails);
            return this;
        }

        public Builder tool(String tool) {
            this.tools.add(tool);
            return this;
        }
        public Builder tools(List<String> tools) {
            this.tools = new ArrayList<>(tools);
            return this;
        }

        public Builder emotionalBaseline(EmotionalBaseline baseline) {
            this.emotionalBaseline = baseline;
            return this;
        }

        public Builder expertiseEmbedding(float[] embedding) {
            this.expertiseEmbedding = embedding;
            return this;
        }

        public Builder purposeEmbedding(float[] embedding) {
            this.purposeEmbedding = embedding;
            return this;
        }

        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public AgentSoul build() {
            return new AgentSoul(
                    id, name, description, systemPrompt,
                    purpose, personality,
                    expertiseDomains, coreValues, ethicalGuardrails,
                    emotionalBaseline,
                    communicationStyle,
                    model, tools,
                    expertiseEmbedding, purposeEmbedding,
                    createdAt, updatedAt);
        }
    }

    @Override
    public String toString() {
        return "AgentSoul[id=" + id
                + ", name=" + name
                + ", purpose=" + (purpose != null ? purpose.length() + " chars" : "null")
                + ", expertise=" + expertiseDomains.size() + " domains"
                + ", guardrails=" + ethicalGuardrails.size()
                + ", emotional=" + emotionalBaseline
                + ", model=" + model
                + ", tools=" + tools.size()
                + ", embeddings=" + hasEmbeddings()
                + "]";
    }
}
