# Project: spector namespace-scale-and-observability

## Architecture
The `namespace-scale-and-observability` capability introduces scalable partition management, bounded fan-out recall, full graph capacity telemetry, index-assisted table pagination, and container security across Spector's modular architecture:
- **`memory/spector-kernel`**: Manages the binary partition bundle header layout. Implements `PartitionSummaryHeader` at offset 512 (within the unused 448–4095 range of the first 4KB header page) with magic `0x5350534D` ('SPSM') and CRC32C validation.
- **`memory/spector-memory`**:
  - `PartitionHandle.asFrozen`: Computes and writes `PartitionSummary` to bundle header at freeze time.
  - `PartitionManager.openFrozenBundlePartition`: Reads and CRC-validates persisted summary, achieving $O(\text{partitions})$ cold start with fallback to `PartitionSummary.fromRouter(...)` scan.
  - `CorticalTierScanRelay`: Adds a dedicated post-pruning `PartitionVisitBudgetStage` that sorts candidate partitions recency-first (`maxTimestampMs` DESC, `seq` DESC), truncates to `RecallOptions.partitionVisitBudget()`, and sets `RecallSignal.truncated = true`.
- **`memory/spector-metrics`**:
  - Exports Micrometer counters: `spector.recall.partitions_visited`, `spector.recall.partitions_skipped`, and `spector.recall.partitions_budgeted`.
  - Exports Prometheus gauges via Spring `MeterBinder` in `SpectorMemoryGauges`: `spector.graph.nodes`, `spector.graph.edges`, `spector.graph.bytes`, and `spector.graph.headroom` using `structureHealthSnapshot()`.
- **`synapse/spector-synapse`**:
  - Evolving `GET /api/v1/memory/table` to use base64 opaque cursor pagination over total order `(timestampMs DESC, id DESC)`.
  - Filters: `created_from`, `created_to`, `source`, `tier`, `tombstoned`.
  - Uses `PartitionSummary` temporal gates to read only intersecting partitions without full-namespace heap materialization and without touching the 6-phase vector scorer.
  - Surfacing `truncated` boolean in `RecallResponse`.
- **`synapse/spector-mcp`**:
  - Adds `memory_list` tool with filter and cursor parity.
  - Updates `MemoryExportTool` to support scoping filters and limits.
  - Surfaces truncation notices in `memory_recall`.
- **`bench/`**:
  - Single-namespace benchmarks under `bench/` evaluating 100k, 1M, and 10M engrams with graph expansion on/off, measuring p50/p99 latency, partitions visited, cold start, and RSS.
- **`deploy/docker/`**:
  - Pins runtime base image `eclipse-temurin:25-jre` in `Dockerfile` using exact `@sha256:` digest and adds automated Dependabot pin updates in `.github/dependabot.yml`.

---

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| F1 | Git Issue, Branch, and PR Lifecycle | Issue-first workflow, branch `feat/namespace-scale-and-observability`, DCO 1.1 sign-offs, open PR (DO NOT MERGE) | M1 | ORIGINAL_REQUEST R1 |
| F2 | PartitionSummary Header Persistence | Write `PartitionSummary` into partition bundle header at offset 512 at freeze time | M2 | ORIGINAL_REQUEST R2 |
| F3 | $O(\text{partitions})$ Cold-Start Loading | Read persisted `PartitionSummary` on partition open, bypassing tier record scans | M2 | ORIGINAL_REQUEST R2 |
| F4 | Summary CRC32C Validation & Fallback | Validate summary CRC32C on open; fallback to `fromRouter` scan on mismatch or absence | M2 | ORIGINAL_REQUEST R2 |
| F5 | Dynamic Active Partition Summary | Active writable partition summary remains dynamically computed (`maxTimestampMs = Long.MAX_VALUE`) | M2 | ORIGINAL_REQUEST R2 |
| F6 | Post-Pruning Partition Visit Budget | Configurable visit budget cap applied after `DefaultPartitionPruner` in `CorticalTierScanRelay` | M3 | ORIGINAL_REQUEST R3 |
| F7 | Recency-First Budget Truncation Ordering | Sort candidate partitions by `maxTimestampMs` DESC, `seq` DESC when budget bites | M3 | ORIGINAL_REQUEST R3 |
| F8 | Recall Result Truncation Flag | Set explicit `truncated` flag on `RecallSignal` and result metadata when budget bites | M3 | ORIGINAL_REQUEST R3 |
| F9 | REST & MCP Truncation Propagation | Expose `truncated` flag in REST `RecallResponse` and MCP `memory_recall` warning notices | M3 | ORIGINAL_REQUEST R3 |
| F10 | Partition Recall Micrometer Counters | Emit `spector.recall.partitions_visited`, `partitions_skipped`, `partitions_budgeted` | M3 | ORIGINAL_REQUEST R3 |
| F11 | Prometheus Graph Structural Gauges | Export `spector.graph.nodes`, `edges`, `bytes` gauges via `MeterBinder` backed by `structureHealthSnapshot()` | M4 | ORIGINAL_REQUEST R4 |
| F12 | Graph Node-Space Headroom Telemetry | Export `spector.graph.headroom` gauge ($[0.0, 1.0]$) to alert before capacity exhaustion | M4 | ORIGINAL_REQUEST R4 |
| F13 | Stable Total Order `(created_at, id)` | Sort memory records by `(timestampMs DESC, id DESC)` for deterministic paging across writes | M5 | ORIGINAL_REQUEST R5 |
| F14 | Opaque Base64 Cursor Pagination | Base64 cursor token for `GET /api/v1/memory/table`, retaining `page` backward compatibility | M5 | ORIGINAL_REQUEST R5 |
| F15 | Time-Ranged & Source Query Filtering | Add `created_from`, `created_to`, `source` filters to `GET /api/v1/memory/table` | M5 | ORIGINAL_REQUEST R5 |
| F16 | Index-Assisted Temporal Partition Seeking | Route listing queries through `PartitionSummary` temporal gates to touch only in-range partitions | M5 | ORIGINAL_REQUEST R5 |
| F17 | Scorer-Free Listing Path | Guarantee that memory table listing strictly avoids the 6-phase vector scoring pipeline | M5 | ORIGINAL_REQUEST R5 |
| F18 | MCP `memory_list` Tool | Tool for listing memories with filters (`created_from`, `created_to`, `tier`, `source`, `limit`, `cursor`) | M5 | ORIGINAL_REQUEST R5 |
| F19 | Scoped MCP `memory_export` Tool | Upgrade `MemoryExportTool` to support scoping filters and pagination | M5 | ORIGINAL_REQUEST R5 |
| F20 | Single-Namespace Scale Benchmark | 100k, 1M, 10M engrams scale benchmark measuring p50/p99 latency, partitions, cold start, RSS | M6 | ORIGINAL_REQUEST R6 |
| F21 | Container Base Digest Pinning | Pin `eclipse-temurin:25-jre` in `deploy/docker/Dockerfile` using exact `@sha256:` digest | M7 | ORIGINAL_REQUEST R7 |
| F22 | Automated Base Pin Advance Policy | Configure Dependabot docker ecosystem and document pin-update policy | M7 | ORIGINAL_REQUEST R7 |

---

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Git Issue, Branch, and PR Lifecycle | Create GitHub issue, cut feature branch `feat/namespace-scale-and-observability`, open draft PR | none | IN_PROGRESS |
| M2 | Persist Partition Summary ($O(\text{partitions})$ Cold Start) | Bundle header persistence at offset 512, CRC32C validation, cold-start optimization, scan fallback, `PartitionSummaryPersistenceTest` | M1 | PLANNED |
| M3 | Partition Recall Fan-Out Budget & Recall Observability | Post-pruning visit budget in `CorticalTierScanRelay`, recency-first sort, truncation flag in REST/MCP, Micrometer counters | M2 | PLANNED |
| M4 | Graph Memory & Headroom Telemetry | Prometheus gauges for nodes, edges, bytes, headroom in `SpectorMemoryGauges`, `GraphHealthMetricsTest` | M1 | PLANNED |
| M5 | Index-Assisted Cursor Listing & MCP Parity | Opaque cursor pagination on `/table`, temporal partition seeking, query filters, MCP `memory_list`, updated `memory_export` | M2 | PLANNED |
| M6 | Single-Namespace Scale Benchmarks | 100k/1M/10M scale benchmark under `bench/`, cold-start & RSS measurements, capacity docs | M2, M3, M4, M5 | PLANNED |
| M7 | Container Base Digest Pinning | Exact `@sha256:` pinning in `deploy/docker/Dockerfile`, Dependabot docker config, security verification | M1 | PLANNED |
| M-E2E | Dual Track E2E Verification & Adversarial Hardening | Comprehensive test suite (Tiers 1-4), 100% pass verification, Tier 5 adversarial hardening, Sentinel hand-off | M1–M7 | PLANNED |

---

## Interface Contracts

### 1. `memory/spector-kernel` ↔ `memory/spector-memory`
- **Location**: `PartitionBundle` offset 512 (64-byte block).
- **Magic**: `0x5350534D` ('SPSM'). Version: `1`.
- **Layout**: `magic (4B), version (4B), seq (4B), reserved (4B), minTimestampMs (8B), maxTimestampMs (8B), synapticTagMaskLo (8B), synapticTagMaskHi (8B), semanticCount (4B), episodicCount (4B), proceduralCount (4B), crc32c (4B)`.
- **Contract**:
  - `writeSummary(PartitionSummary summary)` writes the 64-byte block and flushes header slice.
  - `readSummary()` returns validated `PartitionSummary` or `null` if magic != SPSM or CRC32C mismatch.
  - Zero payload records read when summary is valid.

### 2. `DefaultPartitionPruner` ↔ `CorticalTierScanRelay`
- **Invariant**: `DefaultPartitionPruner.prune(...)` soundness logic is NEVER modified (zero false negatives).
- **Contract**:
  - `List<PartitionHandle> surviving = partitionPruner.prune(...)`.
  - Post-pruning budget stage evaluates `budget = options.partitionVisitBudget()`.
  - When `budget > 0 && surviving.size() > budget`:
    - Candidates sorted: `(maxTimestampMs DESC, seq DESC)`.
    - Truncated candidates taken: `surviving.subList(0, budget)`.
    - `signal.setTruncated(true)`.
  - Counters incremented: `spector.recall.partitions_visited`, `spector.recall.partitions_skipped`, `spector.recall.partitions_budgeted`.

### 3. `structureHealthSnapshot()` ↔ `SpectorMemoryGauges`
- **Contract**:
  - Gauge `spector.graph.nodes` (tags: `graph="hebbian"|"entity"`, `spector.namespace`): highest node index seen / active entities.
  - Gauge `spector.graph.edges` (tags: `graph="hebbian"|"entity"`, `spector.namespace`): total edges.
  - Gauge `spector.graph.bytes` (tags: `graph="hebbian"|"entity"`, `spector.namespace`): allocated bytes.
  - Gauge `spector.graph.headroom` (tags: `graph="hebbian"`, `spector.namespace`): $[0.0, 1.0]$ headroom ratio.

### 4. REST `/api/v1/memory/table` ↔ Synapse DAO
- **Query Params**: `cursor` (base64 string), `pageSize` (default 50, max 500), `created_from` (Long ms), `created_to` (Long ms), `source` (String), `tier` (String), `tombstoned` (Boolean), `page` (deprecated backward compatibility).
- **Sort Total Order**: `(timestampMs DESC, id DESC)`.
- **Cursor Format**: Base64 encoded JSON or string `ts:id`.
- **Response**: `MemoryTableResponse` including `nextCursor` (null if end of data), `rows`, `totalCount`, `page`, `pageSize`, `tierCounts`, `tombstoneRatios`.
- **Seeking**: Check `PartitionSummary` temporal gates `[minTimestampMs, maxTimestampMs]` against `[created_from, created_to]`. Skip non-overlapping partitions completely. Never invoke the 6-phase vector scoring relay.

### 5. Synapse ↔ MCP Parity
- **`memory_list`**: Accepts `cursor`, `limit`, `tier`, `source`, `created_from`, `created_to`. Returns formatted table with `nextCursor`.
- **`memory_export`**: Accepts `cursor`, `limit`, `tier`, `source`, `created_from`, `created_to`. Returns scoped JSON records.
- **`memory_recall`**: Renders explicit warning when `truncated == true`.

---

## Code Layout
- `memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/bundle/` — Bundle header layout & summary read/write
- `memory/spector-memory/src/main/java/com/spectrayan/spector/memory/cortex/` — `PartitionSummary`, `PartitionHandle`
- `memory/spector-memory/src/main/java/com/spectrayan/spector/memory/persist/` — `PartitionManager`
- `memory/spector-memory/src/main/java/com/spectrayan/spector/memory/pathway/` — `CorticalTierScanRelay`, `RecallOptions`, `RecallSignal`
- `memory/spector-metrics/src/main/java/com/spectrayan/spector/metrics/observation/` — `SpectorMemoryGauges`, graph telemetry
- `synapse/spector-synapse/src/main/java/com/spectrayan/spector/synapse/memory/` — `MemoryController`, `MemoryAccessObject`, `MemoryDto`
- `synapse/spector-mcp/src/main/java/com/spectrayan/spector/mcp/tools/memory/` — `MemoryListTool`, `MemoryExportTool`, `MemoryRecallTool`
- `bench/` — Scale benchmark suite
- `deploy/docker/` — Dockerfile and security pinning
