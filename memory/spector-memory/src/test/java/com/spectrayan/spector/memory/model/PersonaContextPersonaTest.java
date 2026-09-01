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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PersonaContextPersonaTest {

    @Test
    public void testBuilderWithNewFields() {
        IdiolectProfile idiolect = IdiolectProfile.builder()
                .primaryLanguage("fr-FR")
                .build();
                
        VocalProsodyDNA prosody = VocalProsodyDNA.builder()
                .baselineF0Hz(160.0f)
                .build();
                
        PersonaContext context = PersonaContext.builder()
                .about("Test persona")
                .idiolect(idiolect)
                .vocalProsody(prosody)
                .build();
                
        assertTrue(context.isPresent());
        assertEquals("fr-FR", context.idiolect().primaryLanguage());
        assertEquals(160.0f, context.vocalProsody().baselineF0Hz());
        assertNotNull(context.modifiers()); // Defaults are generated properly
    }

    @Test
    public void testDefaultsAndBackwardsCompatibility() {
        PersonaContext context = PersonaContext.NONE;
        
        assertFalse(context.isPresent());
        assertEquals(IdiolectProfile.NEUTRAL, context.idiolect());
        assertEquals(VocalProsodyDNA.NEUTRAL, context.vocalProsody());
    }
}
