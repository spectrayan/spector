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
package com.spectrayan.spector.memory.pathway.dream.relay;

import com.spectrayan.spector.core.similarity.VectorOps;
import com.spectrayan.spector.kernel.api.DreamMode;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.memory.model.RememberResult;
import com.spectrayan.spector.memory.pathway.SoulVersionSource;
import com.spectrayan.spector.memory.pathway.remember.relay.RememberSignal;

/**
 * Port adapter mapping between {@link DreamSignal} / {@link DreamSignal.DreamScene}
 * and {@link RememberSignal} / {@link RememberResult} for nested pathway invocation.
 *
 * <h3>Biological Analog: Dream-to-Memory Consolidation Gate</h3>
 * <p>Translates verified dream insight scenes into memory consolidation signals
 * that the Remember pathway can ingest, preserving provenance metadata
 * ({@code FLAG_DREAMED}, {@code FLAG_SIMULATED}) and Hebbian context.</p>
 *
 * @since 1.5.0
 */
public final class DreamPorts {

    private DreamPorts() {
        // Utility class — no instances
    }

    /**
     * Converts a dream scene into a {@link RememberSignal} suitable for
     * {@code catalog.invoke(RememberPathway.class, ctx, signal)}.
     *
     * @param signal the parent dream signal (provides mode, soul version, timing)
     * @param scene  the dream scene to persist
     * @param id     unique memory identifier for this ingestion
     * @param soulVersion current soul version from context or fallback
     * @return a fully constructed RememberSignal
     */
    public static RememberSignal toRememberSignal(final DreamSignal signal,
                                                   final DreamSignal.DreamScene scene,
                                                   final String id,
                                                   final short soulVersion) {
        final byte procFlags = EncodingHeaderFields.withMemoryType((byte) 0, MemoryType.SEMANTIC.ordinal());
        final float norm = scene.embedding() != null ? VectorOps.magnitude(scene.embedding()) : 1.0f;
        final byte dreamFlags = (byte) (EncodingHeaderFields.FLAG_DREAMED | EncodingHeaderFields.FLAG_SIMULATED);

        final EncodingHeader header = EncodingHeader.createSynthetic(
                signal.simulationTimeMs(), 0L, norm,
                scene.qualityScore(), (byte) 0, (byte) 128, procFlags,
                dreamFlags, soulVersion, 0.0f
        );

        final MemorySource src = signal.mode() == DreamMode.THOUGHT_EXPERIMENT
                ? MemorySource.THOUGHT_EXPERIMENT
                : MemorySource.DREAMED;

        final String text = scene.insightText() != null && !scene.insightText().isBlank()
                ? scene.narrative() + " | " + scene.insightText()
                : scene.narrative();

        final String[] tags = new String[]{
                "dreamed",
                signal.mode().name().toLowerCase(),
                scene.triageOutcome().name().toLowerCase()
        };

        return RememberSignal.forCognitiveWithHeader(
                id, text, scene.embedding(), MemoryType.SEMANTIC, tags, src, header
        );
    }

    /**
     * Resolves the effective soul version for dream ingestion, preferring the
     * pathway context's {@link SoulVersionSource} over the legacy signal accessor.
     *
     * @param signal the dream signal
     * @return current soul version
     */
    public static short resolveSoulVersion(final DreamSignal signal) {
        if (signal.context() != null) {
            return signal.context().find(SoulVersionSource.class)
                    .map(SoulVersionSource::currentSoulVersion)
                    .orElse((short) 0);
        }
        return (short) 0;
    }

    /**
     * Absorbs a {@link RememberResult} back into the dream signal.
     *
     * <p>Currently a no-op — the dream pathway tracks ingested counts
     * via its own atomic counter rather than per-scene results.</p>
     *
     * @param signal the parent dream signal
     * @param result the result from Remember pathway conduction
     */
    public static void absorbRemembered(final DreamSignal signal, final RememberResult result) {
        // Dream tracks ingested counts via dreamsIngested AtomicInteger.
        // Per-scene result absorption can be added when needed.
    }
}
