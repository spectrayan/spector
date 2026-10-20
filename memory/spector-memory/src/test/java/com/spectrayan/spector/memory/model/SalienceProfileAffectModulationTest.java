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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Persona modulation of signed valence and unsigned arousal (#1137).
 *
 * <p>Valence is signed everywhere in the engine (0 is neutral). Modulation used to treat it as unsigned
 * centred on 128, which turned neutral memories into slightly positive ones and processed negative
 * valence as positive.</p>
 */
class SalienceProfileAffectModulationTest {

    /** Neurotic, low-agreeableness style: amplify extremes, do not buffer negatives. */
    private static final PersonalityModifiers AMPLIFYING = new PersonalityModifiers(
            1.15f, 0.85f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f);

    /** Agreeable style: buffer negatives only. */
    private static final PersonalityModifiers BUFFERING = new PersonalityModifiers(
            1.0f, 1.0f, 1.0f, 0.85f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f);

    @Test
    void neutralValenceStaysNeutral() {
        assertThat(profile(AMPLIFYING).modulateValence((byte) 0)).isZero();
    }

    @Test
    void signIsPreservedAndMagnitudeAmplified() {
        SalienceProfile profile = profile(AMPLIFYING);

        assertThat(profile.modulateValence((byte) 40)).isEqualTo((byte) 46);
        assertThat(profile.modulateValence((byte) -40)).isEqualTo((byte) -46);
    }

    @Test
    void negativeBufferAppliesToNegativeValenceOnly() {
        SalienceProfile profile = profile(BUFFERING);

        assertThat(profile.modulateValence((byte) -40)).isEqualTo((byte) -34);
        assertThat(profile.modulateValence((byte) 40)).isEqualTo((byte) 40);
    }

    @Test
    void extremesAreClampedToTheSignedRange() {
        SalienceProfile profile = profile(AMPLIFYING);

        assertThat(profile.modulateValence((byte) 127)).isEqualTo((byte) 127);
        assertThat(profile.modulateValence((byte) -128)).isEqualTo((byte) -128);
    }

    @Test
    void unappraisedArousalIsNotInvented() {
        assertThat(profile(AMPLIFYING).modulateArousal((byte) 0)).isZero();
    }

    @Test
    void arousalIsCompressedTowardTheMidpoint() {
        SalienceProfile profile = profile(AMPLIFYING);

        assertThat(Byte.toUnsignedInt(profile.modulateArousal((byte) 228))).isEqualTo(213);
    }

    @Test
    void withoutPersonaValuesPassThrough() {
        assertThat(SalienceProfile.NEUTRAL.modulateValence((byte) -40)).isEqualTo((byte) -40);
        assertThat(Byte.toUnsignedInt(SalienceProfile.NEUTRAL.modulateArousal((byte) 120))).isEqualTo(120);
    }

    private static SalienceProfile profile(PersonalityModifiers modifiers) {
        PersonaContext persona = mock(PersonaContext.class);
        when(persona.isPresent()).thenReturn(true);
        when(persona.modifiers()).thenReturn(modifiers);
        return SalienceProfile.builder().persona(persona).build();
    }
}
