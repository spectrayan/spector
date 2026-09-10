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
package com.spectrayan.spector.kernel.unsafe;

import com.spectrayan.spector.kernel.bundle.BundleDirectory;
import com.spectrayan.spector.kernel.bundle.BundleSubHeader;
import com.spectrayan.spector.kernel.region.RegionEntry;
import com.spectrayan.spector.kernel.region.RegionId;
import com.spectrayan.spector.kernel.region.RegionPreamble;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * Raw bundle access for offline tooling ONLY — inspect, migrate, repair.
 * Not for engine code. Callers assume all remap and lifetime hazards.
 *
 * <p>Permitted callers: spector-inspect, spector-cli, test sources.
 * Enforced by JPMS qualified export where available, and by ArchUnit otherwise.</p>
 */
public final class RawBundleAccess implements AutoCloseable {

    private final Path bundlePath;
    private final FileChannel channel;
    private final Arena arena;
    private final MemorySegment masterSegment;

    private RawBundleAccess(Path bundlePath, FileChannel channel, Arena arena, MemorySegment masterSegment) {
        this.bundlePath = bundlePath;
        this.channel = channel;
        this.arena = arena;
        this.masterSegment = masterSegment;
    }

    public static RawBundleAccess openReadOnly(Path bundlePath) throws IOException {
        FileChannel channel = FileChannel.open(bundlePath, StandardOpenOption.READ);
        Arena arena = Arena.ofShared();
        MemorySegment master = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena);
        return new RawBundleAccess(bundlePath, channel, arena, master);
    }

    public static RawBundleAccess openReadWrite(Path bundlePath) throws IOException {
        FileChannel channel = FileChannel.open(bundlePath, StandardOpenOption.READ, StandardOpenOption.WRITE);
        Arena arena = Arena.ofShared();
        MemorySegment master = channel.map(FileChannel.MapMode.READ_WRITE, 0, channel.size(), arena);
        return new RawBundleAccess(bundlePath, channel, arena, master);
    }

    public Path path() {
        return bundlePath;
    }

    public MemorySegment masterSegment() {
        return masterSegment;
    }

    public Optional<MemorySegment> rawSlice(RegionId id) {
        BundleDirectory dir = BundleDirectory.read(masterSegment);
        RegionEntry entry = dir.findRegion(id);
        return Optional.ofNullable(entry)
                .map(e -> masterSegment.asSlice(e.offset(), e.allocatedSize()));
    }

    public Arena arena() {
        return arena;
    }

    @Override
    public void close() throws IOException {
        try {
            arena.close();
        } finally {
            channel.close();
        }
    }
}
