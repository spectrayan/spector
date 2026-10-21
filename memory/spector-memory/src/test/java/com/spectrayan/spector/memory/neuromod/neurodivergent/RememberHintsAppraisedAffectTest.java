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
package com.spectrayan.spector.memory.neuromod.neurodivergent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link RememberHints#withAppraisedAffect}: caller-supplied affect wins, field by field (#1137).
 */
class RememberHintsAppraisedAffectTest {

    @Test
    void appraisalFillsAbsentHints() {
        RememberHints merged = RememberHints.withAppraisedAffect(null, (byte) -40, (byte) 120);

        assertThat(merged.valence()).isEqualTo((byte) -40);
        assertThat(Byte.toUnsignedInt(merged.arousal())).isEqualTo(120);
        assertThat(merged.isEmpty()).isTrue();
    }

    @Test
    void callerAffectWins() {
        RememberHints caller = new RememberHints(0.5f, 0.2f, 0.1f, (byte) 30, (byte) 50);

        RememberHints merged = RememberHints.withAppraisedAffect(caller, (byte) -40, (byte) 120);

        assertThat(merged).isSameAs(caller);
    }

    @Test
    void appraisalFillsOnlyTheFieldTheCallerLeftUnset() {
        RememberHints caller = new RememberHints(0.5f, 0.2f, 0.1f, (byte) 30, (byte) 0);

        RememberHints merged = RememberHints.withAppraisedAffect(caller, (byte) -40, (byte) 120);

        assertThat(merged.valence()).isEqualTo((byte) 30);
        assertThat(Byte.toUnsignedInt(merged.arousal())).isEqualTo(120);
        assertThat(merged.interest()).isEqualTo(0.5f);
        assertThat(merged.challenge()).isEqualTo(0.2f);
        assertThat(merged.urgency()).isEqualTo(0.1f);
    }

    @Test
    void neutralAppraisalChangesNothing() {
        RememberHints caller = new RememberHints(0.5f, 0f, 0f);

        assertThat(RememberHints.withAppraisedAffect(caller, (byte) 0, (byte) 0)).isSameAs(caller);
        assertThat(RememberHints.withAppraisedAffect(null, (byte) 0, (byte) 0)).isNull();
    }
}
