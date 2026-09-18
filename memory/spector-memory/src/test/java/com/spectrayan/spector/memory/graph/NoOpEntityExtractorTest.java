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
package com.spectrayan.spector.memory.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for NoOpEntityExtractor.
 */
class NoOpEntityExtractorTest {

    @Test
    void extractReturnsEmpty() {
        List<ExtractedEntity> result = NoOpEntityExtractor.INSTANCE.extract("id", "some text");
        assertThat(result).isEmpty();
    }

    @Test
    void isAvailableReturnsTrue() {
        assertThat(NoOpEntityExtractor.INSTANCE.isAvailable()).isTrue();
    }

    @Test
    void singletonInstance() {
        assertThat(NoOpEntityExtractor.INSTANCE).isSameAs(NoOpEntityExtractor.INSTANCE);
    }
}
