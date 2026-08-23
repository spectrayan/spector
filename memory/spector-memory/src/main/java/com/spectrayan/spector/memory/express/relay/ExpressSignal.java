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
package com.spectrayan.spector.memory.express.relay;

import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.aisme.homeostasis.InteroceptiveState;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.memory.model.PersonaContext;
import com.spectrayan.spector.memory.model.SourceModality;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

public record ExpressSignal(
        String queryText,
        List<CognitiveResult> candidates,
        InteroceptiveState interoceptiveState,
        SoulContext soulContext,
        PersonaContext personaContext,
        Set<SourceModality> requestedModalities,
        Map<String, Object> attributes
) {

    public static Builder forQuery(String query, InteroceptiveState state, SoulContext soul) {
        return new Builder().queryText(query).interoceptiveState(state).soulContext(soul);
    }

    public static class Builder {
        private String queryText;
        private List<CognitiveResult> candidates = Collections.emptyList();
        private InteroceptiveState interoceptiveState;
        private SoulContext soulContext;
        private PersonaContext personaContext;
        private Set<SourceModality> requestedModalities = Collections.emptySet();
        private Map<String, Object> attributes = new HashMap<>();

        public Builder queryText(String queryText) {
            this.queryText = queryText;
            return this;
        }

        public Builder candidates(List<CognitiveResult> candidates) {
            this.candidates = candidates;
            return this;
        }

        public Builder interoceptiveState(InteroceptiveState interoceptiveState) {
            this.interoceptiveState = interoceptiveState;
            return this;
        }

        public Builder soulContext(SoulContext soulContext) {
            this.soulContext = soulContext;
            return this;
        }

        public Builder personaContext(PersonaContext personaContext) {
            this.personaContext = personaContext;
            return this;
        }

        public Builder requestedModalities(Set<SourceModality> requestedModalities) {
            this.requestedModalities = requestedModalities;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes;
            return this;
        }
        
        public Builder attribute(String key, Object value) {
            this.attributes.put(key, value);
            return this;
        }

        public ExpressSignal build() {
            return new ExpressSignal(queryText, candidates, interoceptiveState, soulContext, personaContext, requestedModalities, attributes);
        }
    }
}
