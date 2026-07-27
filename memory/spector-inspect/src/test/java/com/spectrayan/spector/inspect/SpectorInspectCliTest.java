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
package com.spectrayan.spector.inspect;

import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.assertj.core.api.Assertions.assertThat;

class SpectorInspectCliTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void testHeaderSubcommandPrintsCorrectMetadata() throws Exception {
        Path file = tempDir.resolve("test_memory.bin");

        // Write a mock standard header
        try (Arena arena = Arena.ofShared();
             FileChannel fc = FileChannel.open(file,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.WRITE,
                     StandardOpenOption.READ)) {

            fc.position(MemoryHeader.HEADER_BYTES - 1);
            fc.write(java.nio.ByteBuffer.wrap(new byte[]{0}));

            MemorySegment segment = fc.map(FileChannel.MapMode.READ_WRITE, 0, MemoryHeader.HEADER_BYTES, arena);
            MemoryHeader.write(segment, 0, 5, MemoryShape.RECORD, 1, 1000L, 500L, 32, 0x54585444, 1717171717000L, 1818181818000L);
        }

        SpectorInspectCli.main(new String[]{"header", file.toAbsolutePath().toString()});

        String output = outContent.toString();
        assertThat(output)
                .contains("Schema Version:   5")
                .contains("Memory Shape:     RECORD")
                .contains("Capacity:         1000")
                .contains("Count:            500")
                .contains("Record Stride:    32 bytes")
                .contains("Layout ID:        0x54585444")
                .contains("DTXT");
    }
}
