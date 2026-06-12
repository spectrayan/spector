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
package com.spectrayan.spector.mcp.util;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ResultFormatter} — timing footer, static helper methods.
 */
@DisplayName("ResultFormatter")
class ResultFormatterTest {

    @Nested
    @DisplayName("withTimingFooter")
    class TimingFooterTests {

        @Test @DisplayName("appends timing footer to result")
        void appendsTimingFooter() {
            var result = ResultFormatter.withTimingFooter("Found 5 results", "SIMD search", 42);
            assertThat(result).contains("Found 5 results");
            assertThat(result).contains("[SIMD search completed in 42ms]");
        }

        @Test @DisplayName("works with empty text")
        void emptyText() {
            var result = ResultFormatter.withTimingFooter("", "search", 0);
            assertThat(result).contains("[search completed in 0ms]");
        }

        @Test @DisplayName("preserves newline before footer")
        void newlineBeforeFooter() {
            var result = ResultFormatter.withTimingFooter("text", "op", 100);
            assertThat(result).startsWith("text\n[");
        }
    }
}
