# Spector Batch (`spector-batch`)

High-throughput, chunk-oriented **Spring Batch migration engine** for Spector Memory.

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

### CLI Integration (`spectorctl`)
- Remote export: `spectorctl memory export --namespace=default --output=/tmp/backup.smb`
- Offline export: `spectorctl memory export --namespace=default --output=/tmp/backup.smb --offline`
- Remote import: `spectorctl memory import --input=/tmp/backup.smb --target-namespace=migrated_ns`
- Offline import: `spectorctl memory import --input=/tmp/backup.smb --target-namespace=migrated_ns --offline`
