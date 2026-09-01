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
/**
 * Spector Memory — Biologically-Inspired Cognitive Memory Architecture for Autonomous AI Agents.
 *
 * <p>A Zero-GC, SIMD-accelerated cognitive memory backbone running natively on the Java Panama
 * Foreign Function &amp; Memory (FFM) API.</p>
 *
 * <h3>Public API Surface (Root &amp; {@code api/})</h3>
 * <ul>
 *   <li>{@link com.spectrayan.spector.memory.SpectorMemory} — Unified cognitive memory facade</li>
 *   <li>{@link com.spectrayan.spector.memory.SpectorMemoryBuilder} — Fluent configuration and assembly entry</li>
 *   <li>{@link com.spectrayan.spector.memory.SpectorMemoryAdmin} — Administrative inspection and maintenance facade</li>
 *   <li>{@link com.spectrayan.spector.memory.api.MemoryRemember} — Interface Segregation: memory ingestion and encoding operations</li>
 *   <li>{@link com.spectrayan.spector.memory.api.MemoryRecall} — Interface Segregation: fused cognitive retrieval operations</li>
 *   <li>{@link com.spectrayan.spector.memory.api.MemoryReflection} — Interface Segregation: sleep consolidation, decay, dreaming, and metamemory</li>
 *   <li>{@link com.spectrayan.spector.memory.api.MemoryAdminView} — Interface Segregation: metric inspection and soul/identity administration</li>
 *   <li>{@link com.spectrayan.spector.memory.api.ImportanceProvider} — SPI for custom importance scoring algorithms</li>
 *   <li>{@link com.spectrayan.spector.memory.api.SalienceProfileProvider} — SPI for enterprise salience profiles</li>
 * </ul>
 *
 * <h3>Subsystem Packages</h3>
 * <ul>
 *   <li>{@code api/} — Segregated interfaces (ISP) and SPI contracts for fine-grained dependency injection</li>
 *   <li>{@code bootstrap/} — Subsystem builders, wiring orchestrators, and memory factory</li>
 *   <li>{@code pathway/} — Cognitive pathway pipelines (Remember, Recall, Reflect, Dream, Wander, Express, Decide)</li>
 *   <li>{@code persist/} — Data encryption SPI, layout versioning, WAL recovery, PartitionManager, and lifecycle persistence</li>
 *   <li>{@code cortex/} — Memory tiers (Working, Episodic, Semantic, Procedural) + source monitoring</li>
 *   <li>{@code synapse/} — 64-byte header layout, fused SIMD scoring, Bloom filter tags, bucket decay</li>
 *   <li>{@code dopamine/} — Adaptive surprise detection (z-score importance assignment)</li>
 *   <li>{@code amygdala/} — Emotional valence and outcome-driven reinforcement</li>
 *   <li>{@code hippocampus/} — REM/Deep Sleep consolidation daemon</li>
 *   <li>{@code hebbian/} — Spreading activation and co-occurrence tracking</li>
 *   <li>{@code graph/} — 3-Layer Cognitive Entity Graph (HyperEntityGraph, EntityDirectory)</li>
 *   <li>{@code temporal/} — Temporal chain and knowledge graph indexing</li>
 *   <li>{@code aisme/} — Active Inference Self-Model Engine</li>
 *   <li>{@code kernel/} — Panama FFM off-heap memory layouts and bundle managers</li>
 *   <li>{@code namespace/} — Multi-tenant namespace isolation</li>
 *   <li>{@code error/} — SPE-310 typed domain exception hierarchy</li>
 * </ul>
 *
 * @see com.spectrayan.spector.memory.SpectorMemory
 * @see com.spectrayan.spector.memory.SpectorMemoryBuilder
 */
package com.spectrayan.spector.memory;
