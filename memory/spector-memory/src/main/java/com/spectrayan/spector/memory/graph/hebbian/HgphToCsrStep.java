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
package com.spectrayan.spector.memory.graph.hebbian;

import com.spectrayan.spector.memory.graph.EdgeImportance;
import com.spectrayan.spector.memory.kernel.codec.FormatId;
import com.spectrayan.spector.memory.kernel.codec.MigrationContext;
import com.spectrayan.spector.memory.kernel.codec.RewriteFileStep;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Migration step converting the legacy Hebbian Graph container (HGPH, magic
 * {@code 0x48475048}) to the kernel SMKM CSR container.
 *
 * <p>Unlike the earlier draft (which merely re-wrapped the legacy bytes in an SMKM header
 * and produced a file the loader could not read — the #432 data-loss bug), this step
 * actually decodes the legacy graph via {@link HebbianGraph} and re-serializes it through
 * {@link HebbianGraphMemory#save} so the output is a valid SMKM CSR file that
 * {@code HebbianGraphMemory.load} can read.</p>
 *
 * <p>The legacy 'HGPH' magic is written big-endian on disk, but {@code FormatDetector}
 * sniffs the leading int in native (little-endian) byte order — so {@link #from()} uses the
 * byte-reversed magic to match what the detector actually sees.</p>
 */
public final class HgphToCsrStep extends RewriteFileStep {

    /** Legacy 'HGPH' magic as read in native (little-endian) order by FormatDetector. */
    public static final int FROM_MAGIC = Integer.reverseBytes(HebbianGraphMemory.LEGACY_HGPH_MAGIC);
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

    @SuppressWarnings("deprecation") // HebbianGraph.load() required for legacy V2 HGPH migration
    @Override
    protected void rewrite(Path source, Path target, MigrationContext ctx) throws IOException {
        // Decode the legacy fixed-width HGPH graph, rebuild it as a CSR graph, and write
        // the result as an SMKM container to the migration target.
        int maxDegree = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_HEBBIAN_MAX_DEGREE;
        HebbianGraph legacy = HebbianGraph.load(source, 1024,
                maxDegree, EdgeImportance.DEFAULT);
        try {
            HebbianGraphMemory csr = HebbianGraphMemory.fromNeighbors(
                    legacy, maxDegree, EdgeImportance.DEFAULT);
            try {
                csr.save(target);
            } finally {
                csr.close();
            }
        } finally {
            legacy.close();
        }
    }
}
