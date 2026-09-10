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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorInternalException;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;

/**
 * Manages ordered migration hops and executes version upgrades.
 */
public final class CodecChain {

    private final FormatCodec<?> codec;
    private final Map<FormatId, CodecStep> stepMap = new HashMap<>();

    private CodecChain(FormatCodec<?> codec) {
        this.codec = codec;
        for (CodecStep step : codec.steps()) {
            if (stepMap.put(step.from(), step) != null) {
                throw new SpectorInternalException(
                        ErrorCode.INVARIANT_VIOLATED, "duplicate migration step for format " + step.from());
            }
        }
    }

    public static CodecChain of(FormatCodec<?> codec) {
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
