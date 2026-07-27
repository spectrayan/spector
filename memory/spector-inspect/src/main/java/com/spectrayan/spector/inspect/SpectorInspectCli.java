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

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Date;

/**
 * Empty skeleton/CLI implementation for spector-inspect module in Phase 1.
 * Supports the "header" subcommand to parse and output MemoryHeader details.
 */
public class SpectorInspectCli {

    public static void main(String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("header")) {
            System.err.println("Usage: spector-inspect header <file-path>");
            System.exit(1);
        }

        String filePathStr = args[1];
        Path path = Paths.get(filePathStr);

        if (!Files.exists(path)) {
            System.err.println("Error: File does not exist: " + path.toAbsolutePath());
            System.exit(1);
        }

        try {
            long size = Files.size(path);
            if (size < MemoryHeader.HEADER_BYTES) {
                System.err.println("Error: File is too small to contain a valid Spector Memory Kernel header (size: " + size + " bytes).");
                System.exit(1);
            }
        } catch (IOException e) {
            System.err.println("Error reading file size: " + e.getMessage());
            System.exit(1);
        }

        try (Arena arena = Arena.ofShared();
             FileChannel fc = FileChannel.open(path, StandardOpenOption.READ)) {
            
            MemorySegment segment = fc.map(FileChannel.MapMode.READ_ONLY, 0, MemoryHeader.HEADER_BYTES, arena);
            
            if (!MemoryHeader.isValid(segment, 0)) {
                System.err.println("Error: Invalid or corrupted Spector Memory Kernel header.");
                System.exit(1);
            }

            int schemaVersion = MemoryHeader.readSchemaVersion(segment, 0);
            MemoryShape shape = MemoryHeader.readShape(segment, 0);
            long capacity = MemoryHeader.readCapacity(segment, 0);
            long count = MemoryHeader.readCount(segment, 0);
            int recordStride = MemoryHeader.readRecordStride(segment, 0);
            int layoutId = MemoryHeader.readLayoutId(segment, 0);
            long createdAt = MemoryHeader.readCreatedAt(segment, 0);
            long lastFlush = MemoryHeader.readLastFlush(segment, 0);
            int flags = MemoryHeader.readFlags(segment, 0);

            // Convert layoutId back to 4-character string
            String layoutIdStr = String.format("%c%c%c%c",
                    (char) (layoutId & 0xFF),
                    (char) ((layoutId >> 8) & 0xFF),
                    (char) ((layoutId >> 16) & 0xFF),
                    (char) ((layoutId >> 24) & 0xFF));

            System.out.println("==================================================");
            System.out.println("Spector Memory Kernel Header: " + path.getFileName());
            System.out.println("==================================================");
            System.out.printf("Magic:            0x%08X (SMKM)\n", MemoryHeader.MAGIC);
            System.out.printf("Schema Version:   %d\n", schemaVersion);
            System.out.printf("Memory Shape:     %s\n", shape);
            System.out.printf("Flags:            0x%08X\n", flags);
            System.out.printf("Capacity:         %d\n", capacity);
            System.out.printf("Count:            %d\n", count);
            System.out.printf("Record Stride:    %d bytes\n", recordStride);
            System.out.printf("Layout ID:        0x%08X (\"%s\")\n", layoutId, layoutIdStr);
            System.out.printf("Created At:       %s (%d)\n", new Date(createdAt), createdAt);
            System.out.printf("Last Flush At:    %s (%d)\n", new Date(lastFlush), lastFlush);
            System.out.println("==================================================");

        } catch (IOException e) {
            System.err.println("IO Error reading header: " + e.getMessage());
            System.exit(1);
        }
    }
}
