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
package com.spectrayan.spector.kernel.store;

import com.spectrayan.spector.kernel.score.EdgeImportance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the fail-closed guard on a max-degree change against an existing graph file (#983).
 *
 * <p>{@code HebbianGraph} derives its record stride from the <b>configured</b> max degree
 * ({@code nodeBytesPerNode = 4 + maxDegree * EDGE_BYTES}) while the file header records the value the file
 * was written with. It read {@code fileDegree} on open and never compared the two, so changing
 * {@code spector.memory.graph.hebbian.max-degree} against an existing file memory-mapped it at the wrong
 * stride: reads landed mid-record and returned arbitrary neighbour ids and weights, with no exception and
 * no log line.</p>
 *
 * @see <a href="https://github.com/spectrayan/spector/issues/983">spectrayan/spector#983</a>
 */
@DisplayName("HebbianGraph max-degree mismatch is refused, not silently misread (#983)")
class HebbianGraphMaxDegreeMismatchTest {

    @Test
    @DisplayName("reopening with a different max degree fails closed and names the migration path")
    void reopeningWithDifferentMaxDegreeIsRefused(@TempDir Path tempDir) {
        Path file = tempDir.resolve("hebbian.dat");

        try (HebbianGraph written = new HebbianGraph(file, 64, 24, EdgeImportance.DEFAULT)) {
            written.strengthen(1, 2, 1.0f);
            assertThat(written.maxDegree()).isEqualTo(24);
        }

        // SpectorGraphPersistenceException renders a fixed error-code message, so the actionable detail
        // lives on the cause (and is additionally logged at ERROR by the guard).
        assertThatThrownBy(() -> new HebbianGraph(file, 64, 48, EdgeImportance.DEFAULT))
                .as("mmapping at a stride the file was not written with must not be allowed")
                .isInstanceOf(com.spectrayan.spector.kernel.error.SpectorGraphPersistenceException.class)
                .rootCause()
                .hasMessageContaining("max-degree mismatch")
                .hasMessageContaining("maxDegree=24")
                .hasMessageContaining("requests 48")
                .hasMessageContaining("stride")
                // The operator needs to know how to get out of this, not just that it failed.
                .hasMessageContaining("spector.memory.graph.hebbian.max-degree=24");
    }

    @Test
    @DisplayName("reopening with the same max degree still works")
    void reopeningWithSameMaxDegreeSucceeds(@TempDir Path tempDir) {
        Path file = tempDir.resolve("hebbian.dat");

        try (HebbianGraph written = new HebbianGraph(file, 64, 24, EdgeImportance.DEFAULT)) {
            written.strengthen(1, 2, 2.5f);
        }

        assertThatCode(() -> {
            try (HebbianGraph reopened = new HebbianGraph(file, 64, 24, EdgeImportance.DEFAULT)) {
                assertThat(reopened.maxDegree()).isEqualTo(24);
                assertThat(reopened.neighbors(1))
                        .as("edges survive a matching reopen")
                        .isNotEmpty();
            }
        }).doesNotThrowAnyException();
    }
}
