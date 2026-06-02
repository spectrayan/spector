/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.spectrayan.spector.events;

/**
 * Graph spreading activation telemetry — emitted per-query from the
 * 3-layer cognitive graph (Hebbian, Temporal, Entity).
 *
 * @param nodesVisited    number of graph nodes visited during activation
 * @param edgesTraversed  number of edges traversed
 * @param maxDepth        maximum activation depth reached
 * @param durationNanos   elapsed time in nanoseconds
 */
public record GraphPulseTelemetry(
        int nodesVisited,
        int edgesTraversed,
        int maxDepth,
        long durationNanos
) implements TelemetryEvent {}
