# Spector Batch (`spector-batch`)

High-throughput, chunk-oriented **Spring Batch migration engine** for Spector Memory.

> ## ⛔ Export and import are DISABLED — do not use for migration or backup
>
> **Status: not implemented.** Every export and import step below now throws
> `UnsupportedOperationException`. See [issue #981](https://github.com/spectrayan/spector/issues/981) and
> [ADR-0045 §0](https://github.com/spectrayan/spector/blob/main/docs/adr/0045-spector-memory-import-export-pipeline.md).
>
> Until 2026-09-23 these pipelines **appeared** to work and did not. `SpectorExportJobConfig` held no
> reference to a memory engine and could not read a namespace: it wrote two hardcoded sample memory rows,
> four literal vector bytes, one invented graph edge, and a literal `AES-256-GCM` claim (no encryption code
> exists anywhere in the product). The validation step then stamped `"verified": true` onto a manifest
> describing its own fixtures. On import, the node, graph and vector-index steps were `log.info` no-ops —
> nothing was parsed and nothing was written.
>
> An operator following the usage instructions below would have received a bundle containing none of their
> data, seen `"verified": true`, and restored it into a fresh instance to a log line reporting success.
> **If you hold an `.smb` file produced before 2026-09-23, it does not contain your data. Do not rely on it
> as a backup.**
>
> The design is sound and is retained as the target. Implementation is tracked in
> `spectrayan/.kiro/specs/memory-portability`. It will ship only with a golden test that exports, wipes,
> imports and asserts memory-id, vector and graph-edge parity.
>
> **For backup and disaster recovery in the meantime, use the DR export path**
> ([operations/disaster-recovery](https://github.com/spectrayan/spector/blob/main/docs/operations/disaster-recovery.md)),
> not this module.
>
> Everything below describes the intended design, not current behaviour.

## Overview

`spector-batch` provides robust batch pipelines for exporting and importing complete Spector Memory cognitive state to support zero-downtime migrations, disaster recovery, cloud backups, and offline data transfer.

## Exported Artifact Structure (`.smb` - Spector Memory Bundle)

Exported archives are compressed `.smb` (`tar.zst` / zip) bundles containing:

- `manifest.json`: Schema version, entity count, vector dimensions, CRC32 checksums.
- `nodes/`: Full memory items (texts, tags, key-values, salience, decay, importance scores).
- `vectors/`: Contiguous float array embeddings and index state metadata.
- `graph/`: Cognitive graph hyperedges, entity relations, and Hebbian weights.
- `subsystems/`: Biological subsystem parameters (Hippocampus, Amygdala, Insula, Dopamine levels).
- `security/`: Encryption header metadata and key references.

## Usage

### REST API Integration (Synapse)

- `POST /api/v1/migration/export?namespace=default&outputPath=/tmp/backup.smb`
- `POST /api/v1/migration/import?bundlePath=/tmp/backup.smb&targetNamespace=migrated_ns`
- `GET /api/v1/migration/jobs/{executionId}`

### CLI Integration (`spector`)

- Remote export: `spector memory export --namespace=default --output=/tmp/backup.smb`
- Offline export: `spector memory export --namespace=default --output=/tmp/backup.smb --offline`
- Remote import: `spector memory import --input=/tmp/backup.smb --target-namespace=migrated_ns`
- Offline import: `spector memory import --input=/tmp/backup.smb --target-namespace=migrated_ns --offline`
