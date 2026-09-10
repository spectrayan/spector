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
package com.spectrayan.spector.kernel.engram.field;

import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;

import com.spectrayan.spector.kernel.engram.EncodingHeader;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EncodingHeaderFields} flag manipulation at offset 34 (ADR-0008).
 */
class EncodingHeaderFieldsTest {

    @Test
    void offset34_governanceAndCrystallizationFlags_workCorrectly() {
        byte flags = 0;
        assertThat(EncodingHeaderFields.isContradicted(flags)).isFalse();
        assertThat(EncodingHeaderFields.isRetracted(flags)).isFalse();
        assertThat(EncodingHeaderFields.isUnverified(flags)).isFalse();
        assertThat(EncodingHeaderFields.isRestricted(flags)).isFalse();
        assertThat(EncodingHeaderFields.isCrystallized(flags)).isFalse();

        flags = EncodingHeaderFields.withRetracted(flags, true);
        assertThat(EncodingHeaderFields.isRetracted(flags)).isTrue();
        assertThat(EncodingHeaderFields.isRestricted(flags)).isFalse();

        flags = EncodingHeaderFields.withRestricted(flags, true);
        assertThat(EncodingHeaderFields.isRetracted(flags)).isTrue();
        assertThat(EncodingHeaderFields.isRestricted(flags)).isTrue();

        flags = EncodingHeaderFields.withUnverified(flags, true);
        assertThat(EncodingHeaderFields.isUnverified(flags)).isTrue();

        flags = EncodingHeaderFields.withCrystallized(flags, true);
        assertThat(EncodingHeaderFields.isCrystallized(flags)).isTrue();

        // Clear retracted
        flags = EncodingHeaderFields.withRetracted(flags, false);
        assertThat(EncodingHeaderFields.isRetracted(flags)).isFalse();
        assertThat(EncodingHeaderFields.isRestricted(flags)).isTrue();
        assertThat(EncodingHeaderFields.isUnverified(flags)).isTrue();
        assertThat(EncodingHeaderFields.isCrystallized(flags)).isTrue();
    }
}
