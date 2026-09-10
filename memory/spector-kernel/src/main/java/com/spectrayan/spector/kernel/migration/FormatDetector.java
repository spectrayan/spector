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
package com.spectrayan.spector.kernel.migration;

import com.spectrayan.spector.kernel.migration.FormatCodec;

import com.spectrayan.spector.kernel.region.RegionPreamble;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import static java.nio.file.StandardOpenOption.READ;

/**
 * Sniffs the file header bytes to classify the FormatId.
 */
public final class FormatDetector {

    private FormatDetector() {}

    public static Optional<FormatId> detect(Path file, CodecRegistry registry) throws IOException {
        if (!Files.exists(file) || Files.size(file) < 4) {
            return Optional.empty();
        }

        try (FileChannel ch = FileChannel.open(file, READ);
             Arena arena = Arena.ofConfined()) {
            long size = Math.min(ch.size(), 64);
            MemorySegment prefix = ch.map(FileChannel.MapMode.READ_ONLY, 0, size, arena);

            int magic = prefix.get(ValueLayout.JAVA_INT_UNALIGNED, 0);
            if (magic == RegionPreamble.MAGIC) {
                int schemaVersion = RegionPreamble.readSchemaVersion(prefix, 0L);
                return Optional.of(FormatId.smkm(schemaVersion));
            }

            if (registry != null) {
                Optional<FormatCodec<?>> codecOpt = registry.byLegacyMagic(magic);
                if (codecOpt.isPresent()) {
                    FormatCodec<?> codec = codecOpt.get();
                    int version = codec.versionOf(magic, prefix);
                    return Optional.of(new FormatId(magic, version));
                }
            }

            // Fallback for direct legacy magic detection
            return Optional.of(new FormatId(magic, 1));
        }
    }
}
