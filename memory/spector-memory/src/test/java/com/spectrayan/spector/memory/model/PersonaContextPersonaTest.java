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
package com.spectrayan.spector.memory.model;

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
