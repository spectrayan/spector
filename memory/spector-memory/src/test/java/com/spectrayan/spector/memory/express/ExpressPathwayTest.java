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
package com.spectrayan.spector.memory.express;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.pathway.ExpressPathway;
import com.spectrayan.spector.memory.express.relay.ExpressSignal;
import com.spectrayan.spector.memory.express.relay.ExpressReport;
import com.spectrayan.spector.memory.aisme.homeostasis.InteroceptiveState;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.memory.model.PersonaContext;
import com.spectrayan.spector.memory.model.IdiolectProfile;
import com.spectrayan.spector.memory.model.StylometricFeatures;
import com.spectrayan.spector.memory.model.IdiosyncraticLexicon;
import com.spectrayan.spector.memory.model.RhetoricalPatterns;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExpressPathwayTest {

    @Test
    public void testExpressPathway() throws Exception {
        try (ExpressPathway pathway = ExpressPathway.builder().build()) {
            
            InteroceptiveState state = new InteroceptiveState(0.5f, 0.8f, 0.4f, new float[0], 0L, 0);
                
            SoulContext soul = null;
                
            IdiolectProfile profile = IdiolectProfile.builder()
                .stylometrics(StylometricFeatures.NEUTRAL)
                .lexicon(IdiosyncraticLexicon.EMPTY)
                .rhetoric(RhetoricalPatterns.NEUTRAL)
                .idiolectEmbedding(new float[]{1.0f, 0.0f, 1.0f})
                .build();
                
            PersonaContext persona = PersonaContext.builder()
                .idiolectProfile(profile)
                .build();
            
            ExpressSignal signal = ExpressSignal.forQuery("hello world", state, soul)
                .personaContext(persona)
                .build();
                
            ExpressReport report = pathway.express(signal);
            
            assertNotNull(report);
            assertNotNull(report.prosodyVector());
            assertNotNull(report.idiolectProfile());
            assertNotNull(report.promptDirectives());
            assertNotNull(report.blendshapeVector());
            assertNotNull(report.contextPack());
        }
    }
}
