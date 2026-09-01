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
package com.spectrayan.spector.memory.persona;

import com.spectrayan.spector.memory.model.IdiolectProfile;
import com.spectrayan.spector.memory.model.IdiosyncraticLexicon;
import com.spectrayan.spector.memory.model.RhetoricalPatterns;
import com.spectrayan.spector.memory.model.StylometricFeatures;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class IdiolectPromptFormatterTest {

    @Test
    public void testFormatPromptDirectives() {
        IdiosyncraticLexicon lexicon = new IdiosyncraticLexicon(
                List.of("Bazinga"), List.of(), List.of(), List.of(), List.of(), List.of("badword"), Map.of()
        );
        RhetoricalPatterns rhetoric = new RhetoricalPatterns(
                RhetoricalPatterns.DirectnessLevel.SOCRATIC, RhetoricalPatterns.HumorArchetype.DRY, 0.2f, 0.1f, List.of(), List.of()
        );
        IdiolectProfile profile = IdiolectProfile.builder()
                .stylometrics(StylometricFeatures.NEUTRAL)
                .lexicon(lexicon)
                .rhetoric(rhetoric)
                .build();
                
        String result = IdiolectPromptFormatter.formatPromptDirectives(profile);
        
        assertNotNull(result);
        assertTrue(result.contains("Catchphrases: Bazinga"));
        assertTrue(result.contains("Taboo Words (Avoid): badword"));
        assertTrue(result.contains("SOCRATIC"));
        assertTrue(result.contains("DRY"));
    }

    @Test
    public void testNeutralProfile() {
        String result = IdiolectPromptFormatter.formatPromptDirectives(IdiolectProfile.NEUTRAL);
        assertEquals("", result);
    }
}
