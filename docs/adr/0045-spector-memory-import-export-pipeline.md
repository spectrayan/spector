# ADR-0045: Spector Memory Import & Export Pipeline

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-13 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

Spector Memory stores rich cognitive state across multiple heterogeneous tiers:
- Memory text contents, tags, key-value attributes, salience scores, decay values, and importance estimates
- High-dimensional vector embeddings (HNSW indices and raw float arrays)
- Cognitive hypergraphs (entities, hyperedges, and Hebbian synaptic coactivation weights)
- Biological subsystem states (Hippocampus, Amygdala, Insula, Dopamine levels)
- Partition encryption headers, keys, and transactional write-ahead logs (WAL)

As Spector Memory evolves, schemas, index layouts, vector dimensions, and biological model configurations undergo major upgrades across releases. To enable zero-downtime migrations, disaster recovery, cloud backups, and offline data transfer, Spector requires a robust, high-throughput, chunk-oriented import and export pipeline.

## 2. Problem Statement

Migrating or backing up multi-gigabyte cognitive memory states introduces critical architectural challenges:
1. **Memory Pressure & OOM Hazards**: Ingesting or dumping millions of high-dimensional vectors and graph nodes into monolithic heap objects inevitably triggers Garbage Collection pauses or JVM `OutOfMemoryError` failures.
2. **Dual Operational Contexts**: Administrators require both online live backups via Synapse REST/gRPC endpoints and offline maintenance operations via the CLI (`spectorctl`) for air-gapped or disaster-recovery environments.
3. **CLI Latency vs Heavy Runtimes**: Standard CLI invocations (`spectorctl status`, `spectorctl version`) must start in under 100 milliseconds and cannot tolerate a 2–3 second Spring Boot bootstrap delay.
4. **Data Completeness**: An export cannot simply be a raw database dump; it must capture full semantics, vector indices, hypergraph topologies, and affective biological parameters in a portable format.

## 3. Decision Drivers

- **Chunked Streaming & Bounded Heap**: All import and export operations must execute in configurable streaming chunks (e.g., 1,000 items/chunk) to keep heap consumption strictly constant regardless of total corpus size.
- **Resumability & Checkpointing**: Long-running migration jobs must maintain persistent state to support pause, cancel, and checkpointed resume operations upon network or host failures.
- **Dual-Mode Ergonomics**: Provide instant sub-100ms CLI execution in online mode by delegating to Synapse, while supporting a standalone embedded execution mode for offline air-gapped clusters.
- **Comprehensive Encapsulated Format**: The backup archive must be self-contained, compressed, checksummed, and versioned.

## 4. Considered Options

### Option 1: Custom Ad-Hoc Streaming Scripts
- Implement bespoke file-streaming threads and socket serializers within `spector-synapse`.
- **Verdict**: Rejected. Fragile, difficult to maintain, lacks standardized job tracking, metrics, retry semantics, and checkpointing.

### Option 2: Apache Camel Route-Based Data Flow
- Leverage Camel enterprise integration routes to marshal and unmarshal components.
- **Verdict**: Rejected for batch migrations. Camel excels at real-time message exchange and streaming endpoints, but lacks native chunk-step-job transaction coordination and job-repository semantics.

### Option 3: Spring Batch in `synapse/spector-batch` with Dual-Mode CLI (Selected)
- Locate the core batch migration engine within `synapse/spector-batch` using Spring Batch's battle-tested `ItemReader`, `ItemProcessor`, and `ItemWriter` abstractions.
- Adopt a dual-mode CLI where `spectorctl` delegates to Synapse REST APIs by default, but provides an embedded `--offline` mode for standalone maintenance.
- Bundle exports into a standardized `.smb` (`tar.zst`) container.
- **Verdict**: Accepted. Delivers industrial-grade chunk processing, progress tracking, and low-latency CLI interactions.

## 5. Decision Outcome

### 5.1 Spring Batch Engine Location & Scoping
The batch processing engine is isolated in **`synapse/spector-batch`**:
- **Chunk Processing Pipeline**: `ItemReader`, `ItemProcessor`, and `ItemWriter` abstractions stream nodes, memory texts, tags, vectors, and graph edges in configurable chunk sizes (default 1,000 items/chunk).
- **Execution Persistence**: `JobRepository` backed by H2 (in-memory or file-based for CLI) or Spring JDBC tracks step progress, execution parameters, and failure checkpoints.
- **Auto-Configuration**: Packaged as `SpectorBatchAutoConfiguration` with explicit export (`SpectorExportJobConfig`) and import (`SpectorImportJobConfig`) pipelines.

### 5.2 Dual-Mode CLI Architecture
We adopt a split execution model:
1. **Online Mode (Default)**:
   - `spectorctl memory export` connects to `spector-synapse` REST API (`/api/v1/migration/export`).
   - Synapse executes the Spring Batch job asynchronously.
   - `spectorctl` streams progress via Server-Sent Events (SSE) or polls job execution status. Startup time remains <100ms.
2. **Offline / Standalone Mode (`--offline`)**:
   - `spectorctl memory export --offline` launches an embedded Spring Batch context directly inside the CLI using `picocli-spring-boot-starter`.
   - Used for air-gapped environments or emergency disaster recovery when Synapse is down.

### 5.3 Spector Memory Bundle (`.smb`) Container Format
Exports are archived into a compressed `.smb` (`tar.zst`) bundle containing structured partitions:
- `manifest.json`: Schema version, entity count, vector dimensions, partition maps, CRC32 checksums.
- `nodes/chunk-*.jsonl.zst`: Full memory items (node IDs, text, tags, key-values, salience, importance, decay).
- `vectors/vectors-*.bin`: Raw float arrays and vector index metadata.
- `graph/edges.jsonl.zst`: Full hypergraph connections, relation attributes, and Hebbian weights.
- `subsystems/state.json`: Biological subsystem parameters (Hippocampus, Amygdala, Insula, Dopamine levels).
- `security/keys.json`: Encryption key references and header metadata.

```mermaid
flowchart LR
    subgraph Clients
        CLI_Online["spectorctl memory export"]
        CLI_Offline["spectorctl memory export --offline"]
        WebUI["Cortex Admin UI"]
    end

    subgraph Synapse ["Spector Synapse Server"]
        REST_API["Migration REST Controller"]
        JobLauncher_Server["Spring Batch JobLauncher"]
    end

    subgraph CoreBatch ["synapse/spector-batch Module"]
        ExportJob["Export Job Pipeline"]
        ImportJob["Import Job Pipeline"]
        SMB_Codec["Spector Memory Bundle (.smb) Codec"]
    end

    CLI_Online -->|REST / SSE| REST_API
    WebUI -->|REST| REST_API
    REST_API --> JobLauncher_Server
    CLI_Offline -->|Embedded Spring Context| ExportJob

    JobLauncher_Server --> ExportJob
    JobLauncher_Server --> ImportJob

    ExportJob --> SMB_Codec
    ImportJob --> SMB_Codec
```

## 6. Pros and Cons of the Options

### Positive
- **Bounded Memory Footprint**: Chunked streaming guarantees that heap consumption remains flat even when processing multimillion-node memory bundles.
- **Comprehensive Preservation**: Full fidelity preservation of vectors, graphs, and affective biological states without data degradation.
- **Fast CLI Interaction**: Online mode keeps the CLI startup latency under 100ms by avoiding JVM Spring context initialization.
- **Resilient Recovery**: In-flight job states are committed incrementally to `JobRepository`, enabling automated resume after network hiccups or JVM restarts.

### Negative / Trade-offs
- **Packaging Footprint**: Including embedded batch dependencies in `spector-cli` increases the binary JAR distribution size.
- **Compression Overhead**: Zstandard compression (`.zst`) introduces CPU overhead during high-throughput exports, requiring thread-pool throttling on active production nodes.

## 7. Implementation Plan

1. **Batch Core Implementation (`synapse/spector-batch`)**:
   - Implement `SpectorBundleCodec` supporting `.smb` archive packaging with Zstd compression and CRC32 verification.
   - Implement `SpectorExportJobConfig` and `SpectorImportJobConfig` defining readers, processors, and writers.
   - Implement `ReflectConsolidationJobConfig` and `SpringBatchReflectSweepExecutor` for background consolidation sweeps.
2. **REST Integration (`synapse/spector-synapse`)**:
   - Expose asynchronous endpoints `/api/v1/migration/export` and `/api/v1/migration/import`.
   - Expose Server-Sent Events (SSE) stream for real-time progress monitoring.
3. **CLI Integration (`synapse/spector-cli`)**:
   - Wire Picocli command tree for `spectorctl memory export` and `spectorctl memory import`.
   - Support `--offline` flag to trigger embedded batch executor when running out-of-band.

## 8. Code Reference & Verification

All batch components and pipelines are implemented and verified in the repository:
- **Batch Core & Auto-Configuration**: `synapse/spector-batch/src/main/java/com/spectrayan/spector/batch/SpectorBatchAutoConfiguration.java`
- **Bundle Codec & Archive Formatting**: `synapse/spector-batch/src/main/java/com/spectrayan/spector/batch/SpectorBundleCodec.java`
- **Batch Service & Lifecycle**: `synapse/spector-batch/src/main/java/com/spectrayan/spector/batch/SpectorBatchService.java`
- **Import & Export Job Configurations**:
  - `synapse/spector-batch/src/main/java/com/spectrayan/spector/batch/SpectorExportJobConfig.java`
  - `synapse/spector-batch/src/main/java/com/spectrayan/spector/batch/SpectorImportJobConfig.java`
- **Reflective Consolidation Pipeline**:
  - `synapse/spector-batch/src/main/java/com/spectrayan/spector/batch/ReflectConsolidationJobConfig.java`
  - `synapse/spector-batch/src/main/java/com/spectrayan/spector/batch/SpringBatchReflectSweepExecutor.java`
