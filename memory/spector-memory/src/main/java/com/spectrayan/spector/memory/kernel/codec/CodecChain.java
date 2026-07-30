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
package com.spectrayan.spector.memory.kernel.codec;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;

/**
 * Manages ordered migration hops and executes version upgrades.
 */
public final class CodecChain {

    private final Codec<?> codec;
    private final Map<FormatId, CodecStep> stepMap = new HashMap<>();

    private CodecChain(Codec<?> codec) {
        this.codec = codec;
        for (CodecStep step : codec.steps()) {
            if (stepMap.put(step.from(), step) != null) {
                throw new IllegalArgumentException("Duplicate migration step for format: " + step.from());
            }
        }
    }

    public static CodecChain of(Codec<?> codec) {
        return new CodecChain(codec);
    }

    public MigrationResult run(MigrationContext ctx) throws IOException {
        if (!Files.exists(ctx.sourcePath()) || Files.size(ctx.sourcePath()) == 0) {
            return MigrationResult.freshFile(codec.current());
        }

        Optional<FormatId> detectedOpt = FormatDetector.detect(ctx.sourcePath(), null);
        if (detectedOpt.isEmpty()) {
            return MigrationResult.freshFile(codec.current());
        }

        FormatId at = detectedOpt.get();
        if (at.equals(codec.current())) {
            return MigrationResult.freshFile(codec.current());
        }

        List<FormatId> hops = new ArrayList<>();
        long startNanos = System.nanoTime();

        while (!at.equals(codec.current())) {
            CodecStep step = stepMap.get(at);
            if (step == null) {
                throw new MigrationException(
                        MigrationException.Reason.NO_UPGRADE_PATH,
                        at,
                        "No upgrade path from format " + at + " to target " + codec.current()
                );
            }

            if (!ctx.dryRun()) {
                try {
                    step.apply(ctx);
                } catch (Exception e) {
                    throw new MigrationException(
                            MigrationException.Reason.STEP_FAILED,
                            at,
                            "Migration step failed: " + step.from() + " -> " + step.to() + ": " + e.getMessage(),
                            e
                    );
                }
            }

            at = step.to();
            hops.add(at);
        }

        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        return new MigrationResult(detectedOpt.get(), at, hops, List.of(), elapsed);
    }
}
