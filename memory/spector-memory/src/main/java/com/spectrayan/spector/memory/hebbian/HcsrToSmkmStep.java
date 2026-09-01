/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.hebbian;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.codec.FormatId;
import com.spectrayan.spector.memory.kernel.codec.MigrationContext;
import com.spectrayan.spector.memory.kernel.codec.RewriteFileStep;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

import static com.spectrayan.spector.memory.kernel.layout.HebbianLayout.DATA_START;
import static com.spectrayan.spector.memory.kernel.layout.HebbianLayout.EDGE_BYTES;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Migration step converting the interim CSR container (HCSR, magic {@code 0x48435352},
 * introduced by #432) to the kernel SMKM CSR container.
 *
 * <p>Both formats already store identical CSR slabs (offset slab + edge slab); only the
 * fixed header differs — the interim 24-byte HCSR header vs. the 64-byte kernel
 * {@link MemoryHeader} plus the 16-byte Hebbian graph sub-header. This step therefore
 * re-writes the header and copies the slab bytes verbatim (they are stored in native byte
 * order in both containers).</p>
 *
 * <p>The interim HCSR header is written big-endian (via {@code ByteBuffer}); its magic is
 * therefore read byte-reversed by {@code FormatDetector}, which {@link #from()} reflects.</p>
 */
public final class HcsrToSmkmStep extends RewriteFileStep {

    /** Interim 'HCSR' magic as read in native (little-endian) order by FormatDetector. */
    public static final int FROM_MAGIC = Integer.reverseBytes(HebbianGraphMemory.INTERIM_HCSR_MAGIC);
    public static final FormatId FROM_FORMAT = new FormatId(FROM_MAGIC, 1);
    public static final FormatId TO_FORMAT = FormatId.smkm(1);

    @Override
    public FormatId from() {
        return FROM_FORMAT;
    }

    @Override
    public FormatId to() {
        return TO_FORMAT;
    }

    @Override
    protected void rewrite(Path source, Path target, MigrationContext ctx) throws IOException {
        try (FileChannel srcCh = FileChannel.open(source, READ);
             FileChannel dstCh = FileChannel.open(target, CREATE, READ, WRITE);
             Arena arena = Arena.ofConfined()) {

            // Interim HCSR header is big-endian: magic, version, capacity, edgeCap,
            // totalEdges, currentCycle (6 x 4 bytes).
            ByteBuffer hdr = ByteBuffer.allocate(HebbianGraphMemory.HCSR_HEADER_BYTES);
            while (hdr.hasRemaining()) {
                if (srcCh.read(hdr) < 0) break;
            }
            hdr.flip();
            int magic = hdr.getInt();
            hdr.getInt(); // version (unused)
            int capacity = hdr.getInt();
            int edgeCap = hdr.getInt();
            int totalEdges = hdr.getInt();
            int cycle = hdr.getInt();

            if (magic != HebbianGraphMemory.INTERIM_HCSR_MAGIC) {
                throw new IOException("Not an interim HCSR file: magic=0x" + Integer.toHexString(magic));
            }

            long offsetBytes = (long) (capacity + 1) * Integer.BYTES;
            long edgeBytes = (long) totalEdges * EDGE_BYTES;
            long slabBytes = offsetBytes + edgeBytes;

            // Write the SMKM 64-byte header + 16-byte graph sub-header.
            MemorySegment head = arena.allocate(DATA_START);
            HebbianGraphMemory.writeSmkmHeader(head, capacity, edgeCap, totalEdges, cycle);
            dstCh.write(head.asByteBuffer());

            // Copy the CSR slabs verbatim (native byte order preserved).
            long transferred = 0;
            while (transferred < slabBytes) {
                long n = srcCh.transferTo(
                        HebbianGraphMemory.HCSR_HEADER_BYTES + transferred,
                        slabBytes - transferred, dstCh);
                if (n <= 0) break;
                transferred += n;
            }
            dstCh.force(true);
        }
    }
}
