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
package com.spectrayan.spector.memory.persist;

import com.spectrayan.spector.kernel.api.MemoryLocation;
import com.spectrayan.spector.kernel.api.MemoryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TombstoneLedger")
class TombstoneLedgerTest {

    @Test
    @DisplayName("round-trips ids with spaces, unicode and separators")
    void roundTrip(@TempDir Path dir) throws Exception {
        var ledger = new TombstoneLedger(dir);
        Map<String, MemoryLocation> in = new LinkedHashMap<>();
        in.put("plain-id", new MemoryLocation(MemoryType.SEMANTIC, 328L, 4, 0, 1024L, 57));
        in.put("id with spaces\tand tabs", new MemoryLocation(MemoryType.EPISODIC, 0L, -1, 2, -1L, -1));
        in.put("ünïcødé/../#hash", new MemoryLocation(MemoryType.WORKING, -1L, 9, 0, 12L, 3));

        ledger.save(in);

        assertThat(new TombstoneLedger(dir).load()).containsExactlyEntriesOf(in);
        // No content is ever written, only ids (encoded) and positions.
        assertThat(Files.readString(ledger.file())).doesNotContain("plain-id");
    }

    @Test
    @DisplayName("an empty registry deletes the file, and a missing file loads as empty")
    void emptyDeletesFile(@TempDir Path dir) throws Exception {
        var ledger = new TombstoneLedger(dir);
        ledger.save(Map.of("x", new MemoryLocation(MemoryType.SEMANTIC, 1L, 1)));
        assertThat(ledger.file()).exists();

        ledger.save(Map.of());

        assertThat(ledger.file()).doesNotExist();
        assertThat(ledger.load()).isEmpty();
    }

    @Test
    @DisplayName("malformed lines are skipped, valid ones kept")
    void skipsMalformedLines(@TempDir Path dir) throws Exception {
        var ledger = new TombstoneLedger(dir);
        ledger.save(Map.of("ok", new MemoryLocation(MemoryType.SEMANTIC, 8L, 2)));
        Files.writeString(ledger.file(), Files.readString(ledger.file()) + "garbage line\nAAAA NOPE 1 2 3 4 5\n");

        assertThat(ledger.load()).containsOnlyKeys("ok");
    }
}
