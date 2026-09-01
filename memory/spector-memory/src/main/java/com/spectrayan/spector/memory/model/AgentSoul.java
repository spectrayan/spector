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
package com.spectrayan.spector.memory.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The agent's persistent identity — its "soul."
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

        // Versioning & timestamps
        short soulVersion,
        Instant createdAt,
        Instant updatedAt
) implements SoulContext {

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

    @Override
    public float[] identityEmbedding() {
        return purposeEmbedding;
    }

    /**
     * No soul configured — agent operates as a generic assistant.
     */
    public static final AgentSoul NONE = new AgentSoul(
            null, null, null, null, null, null,
            List.of(), List.of(), List.of(),
            EmotionalBaseline.NEUTRAL,
            null, null, List.of(),
            null, null, (short) 0, null, null);

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
                null, null, (short) 0, now, now);
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
     */
    public record EmotionalBaseline(byte defaultValence, byte defaultArousal) {
        public static final EmotionalBaseline NEUTRAL = new EmotionalBaseline((byte) 0, (byte) 128);
        public static final EmotionalBaseline WARM = new EmotionalBaseline((byte) 30, (byte) 100);
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
        private short soulVersion;
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

        public Builder soulVersion(short soulVersion) { this.soulVersion = soulVersion; return this; }
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
                    soulVersion, createdAt, updatedAt);
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
