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
/**
 * Provides the durability and maintenance operations of the kernel: write-ahead logging,
 * checkpointing, tombstone compaction, and header schema migration (R9.1–R9.4).
 *
 * <h3>Write-ahead log</h3>
 * <p>{@link MemoryWal} records mutations before they reach an mmap'd region, so a crash between
 * write and flush is recoverable. Replay pushes decoded records through {@link WalVisitor},
 * which deliberately hands consumers fully decoded payloads rather than raw Panama
 * {@link java.lang.foreign.MemorySegment} references — above-the-line code never touches a
 * segment whose lifetime it does not control.</p>
 *
 * <h3>Checkpoint and vacuum</h3>
 * <p>A {@link CheckpointRequest} names the WAL high-water mark to persist, the regions to flush
 * (or all dirty ones), and whether to {@code fsync}; {@link CheckpointResult} reports what was
 * actually synced. {@link VacuumPolicy} sets the tombstone fraction at which a tier is worth
 * compacting — 20% by default — and {@link VacuumResult} reports records scanned and compacted,
 * bytes reclaimed, and the relocated offsets callers must use to fix up their indexes.</p>
 *
 * <h3>Migration</h3>
 * <p>{@link HeaderMigrator} converts store files between header layout versions, reading legacy
 * bytes through the quarantined decoders in
 * {@link com.spectrayan.spector.kernel.engram.compat} and summarizing the run — including
 * whether it was lossy and where the backup landed — in a {@link MigrationReport}.</p>
 *
 * <p>Key components include {@link MemoryWal}, {@link WalVisitor}, {@link CheckpointRequest},
 * {@link CheckpointResult}, {@link VacuumPolicy}, {@link VacuumResult},
 * {@link HeaderMigrator}, and {@link MigrationReport}.</p>
 *
 * @author Spectrayan Maintainers
 */
package com.spectrayan.spector.kernel.sync;
