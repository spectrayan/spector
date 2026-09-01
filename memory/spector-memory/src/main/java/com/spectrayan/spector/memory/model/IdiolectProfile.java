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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import java.util.Arrays;

public record IdiolectProfile(
        StylometricFeatures stylometrics,
        IdiosyncraticLexicon lexicon,
        RhetoricalPatterns rhetoric,
        String primaryLanguage,
        float[] idiolectEmbedding
) {
    public static final IdiolectProfile NEUTRAL = new IdiolectProfile(
            StylometricFeatures.NEUTRAL, IdiosyncraticLexicon.EMPTY, RhetoricalPatterns.NEUTRAL, "en-US", null
    );

    public IdiolectProfile {
        if (stylometrics == null) stylometrics = StylometricFeatures.NEUTRAL;
        if (lexicon == null) lexicon = IdiosyncraticLexicon.EMPTY;
        if (rhetoric == null) rhetoric = RhetoricalPatterns.NEUTRAL;
        if (primaryLanguage == null) primaryLanguage = "en-US";
        if (idiolectEmbedding != null) {
            idiolectEmbedding = Arrays.copyOf(idiolectEmbedding, idiolectEmbedding.length);
        }
    }
    
    public boolean isPresent() {
        return !stylometrics.equals(StylometricFeatures.NEUTRAL)
                || !lexicon.equals(IdiosyncraticLexicon.EMPTY)
                || !rhetoric.equals(RhetoricalPatterns.NEUTRAL)
                || !"en-US".equals(primaryLanguage)
                || (idiolectEmbedding != null && idiolectEmbedding.length > 0);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private StylometricFeatures stylometrics;
        private IdiosyncraticLexicon lexicon;
        private RhetoricalPatterns rhetoric;
        private String primaryLanguage;
        private float[] idiolectEmbedding;
        
        public Builder stylometrics(StylometricFeatures stylometrics) { this.stylometrics = stylometrics; return this; }
        public Builder lexicon(IdiosyncraticLexicon lexicon) { this.lexicon = lexicon; return this; }
        public Builder rhetoric(RhetoricalPatterns rhetoric) { this.rhetoric = rhetoric; return this; }
        public Builder primaryLanguage(String primaryLanguage) { this.primaryLanguage = primaryLanguage; return this; }
        public Builder idiolectEmbedding(float[] idiolectEmbedding) { this.idiolectEmbedding = idiolectEmbedding; return this; }
        public IdiolectProfile build() {
            return new IdiolectProfile(stylometrics, lexicon, rhetoric, primaryLanguage, idiolectEmbedding);
        }
    }
}
