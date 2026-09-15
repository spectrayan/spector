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
package com.spectrayan.spector.commons.valhalla;

import com.spectrayan.spector.commons.TextChunk;
import com.spectrayan.spector.commons.WordTokenizer;
import com.spectrayan.spector.commons.chunker.Chunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueClassValidatorTest {

    @Test
    @DisplayName("Spector commons value candidates pass JEP 390 / JEP 401 compliance audit")
    void testCommonsValueCandidatesPassAudit() {
        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(WordTokenizer.Token.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(TextChunk.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(Chunk.class))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Unannotated class fails audit")
    void testUnannotatedClassFails() {
        record UnannotatedRecord(int x) {}

        assertThatThrownBy(() -> ValueClassValidator.assertValueClassCompliant(UnannotatedRecord.class))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("must be annotated with @ValueCandidate");
    }

    @Test
    @DisplayName("Non-final non-record class fails audit")
    void testNonFinalClassFails() {
        @ValueCandidate
        class NonFinalClass {
            final int x = 1;
            @Override public boolean equals(Object o) { return true; }
            @Override public int hashCode() { return 1; }
        }

        assertThatThrownBy(() -> ValueClassValidator.assertValueClassCompliant(NonFinalClass.class))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("must be a Java record or final class");
    }

    @Test
    @DisplayName("Class with non-final field fails audit")
    void testNonFinalFieldFails() {
        @ValueCandidate
        final class MutableFieldClass {
            int x = 1;
            @Override public boolean equals(Object o) { return true; }
            @Override public int hashCode() { return 1; }
        }

        assertThatThrownBy(() -> ValueClassValidator.assertValueClassCompliant(MutableFieldClass.class))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("is not final");
    }

    @Test
    @DisplayName("Class with synchronized method fails audit")
    void testSynchronizedMethodFails() {
        @ValueCandidate
        final class SynchronizedMethodClass {
            final int x = 1;
            public synchronized void foo() {}
            @Override public boolean equals(Object o) { return true; }
            @Override public int hashCode() { return 1; }
        }

        assertThatThrownBy(() -> ValueClassValidator.assertValueClassCompliant(SynchronizedMethodClass.class))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("is synchronized");
    }
}
