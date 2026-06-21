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
package com.spectrayan.spector.memory.sync;

import java.time.Instant;
import java.util.Map;

import com.spectrayan.spector.events.SpectorEvent;

/**
 * Published when a {@link CheckpointDaemon} successfully completes a
 * checkpoint cycle — WAL entries are flushed and truncated.
 *
 * <p>This event drives checkpoint-driven replication in enterprise mode.
 * The {@link SpectorEvent.ContextKeys} in {@link #context()} carry the
 * identity of the memory instance that was checkpointed:</p>
 *
 * <h4>Core (OSS) Context</h4>
 * <ul>
 *   <li>{@link SpectorEvent.ContextKeys#INSTANCE} — memory base path</li>
 * </ul>
 *
 * <h4>Enterprise Context (set by enterprise layer)</h4>
 * <ul>
 *   <li>{@link SpectorEvent.ContextKeys#TENANT} — tenant/org ID</li>
 *   <li>{@link SpectorEvent.ContextKeys#NAMESPACE} — user/agent namespace</li>
 * </ul>
 *
 * @param context          generic identity context (see {@link SpectorEvent.ContextKeys})
 * @param walHighWaterMark the WAL offset up to which entries were checkpointed
 * @param indexSize        the number of entries in the index after checkpoint
 * @param elapsedMs        checkpoint duration in milliseconds
 * @param timestamp        when the checkpoint completed
 * @see SpectorLifecycleEvent
 * @see CheckpointDaemon
 */
public record CheckpointCompletedEvent(
        Map<String, String> context,
        long walHighWaterMark,
        long indexSize,
        long elapsedMs,
        Instant timestamp
) implements SpectorLifecycleEvent {

    /** Compact constructor — defensively copies the context map. */
    public CheckpointCompletedEvent {
        context = context != null ? Map.copyOf(context) : Map.of();
    }

    @Override
    public String eventType() {
        return "lifecycle.checkpoint.completed";
    }
}
