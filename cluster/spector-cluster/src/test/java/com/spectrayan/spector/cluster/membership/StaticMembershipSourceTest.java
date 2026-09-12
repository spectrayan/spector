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
package com.spectrayan.spector.cluster.membership;

import com.spectrayan.spector.commons.error.SpectorValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaticMembershipSourceTest {

    @Test
    @DisplayName("Successfully creates membership from list")
    void testListMembership() {
        StaticMembershipSource source = new StaticMembershipSource("cell-1", 1, List.of("node-a", "node-b"));
        CellMembership membership = source.current();

        assertThat(membership.cellId()).isEqualTo("cell-1");
        assertThat(membership.ringVersion()).isEqualTo(1);
        assertThat(membership.members()).containsExactly("node-a", "node-b");
    }

    @Test
    @DisplayName("Fails closed when member list is empty (Req R7.3, L2)")
    void testEmptyMemberListFailsClosed() {
        assertThatThrownBy(() -> new StaticMembershipSource("cell-1", 1, List.of()))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("static membership must contain at least one member");
    }

    @Test
    @DisplayName("Successfully parses membership from newline-delimited file with comments and whitespace")
    void testFileMembership(@TempDir Path tempDir) throws IOException {
        Path membersFile = tempDir.resolve("members.txt");
        Files.writeString(membersFile, """
                # Cluster nodes for cell-1
                node-1
                
                # Secondary
                node-2
                  node-3  
                """);

        StaticMembershipSource source = new StaticMembershipSource("cell-1", 2, membersFile);
        CellMembership membership = source.current();

        assertThat(membership.cellId()).isEqualTo("cell-1");
        assertThat(membership.ringVersion()).isEqualTo(2);
        assertThat(membership.members()).containsExactly("node-1", "node-2", "node-3");
    }

    @Test
    @DisplayName("Fails closed when membership file does not exist")
    void testMissingFileFailsClosed(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("nonexistent.txt");
        assertThatThrownBy(() -> new StaticMembershipSource("cell-1", 1, missing))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("membership file does not exist");
    }

    @Test
    @DisplayName("Fails closed when membership file contains only empty lines and comments")
    void testEmptyFileFailsClosed(@TempDir Path tempDir) throws IOException {
        Path emptyFile = tempDir.resolve("empty.txt");
        Files.writeString(emptyFile, """
                # Only comments
                
                # Another comment
                """);

        assertThatThrownBy(() -> new StaticMembershipSource("cell-1", 1, emptyFile))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("membership file is empty or contains only comments");
    }
}
