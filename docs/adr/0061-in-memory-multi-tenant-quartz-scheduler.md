# ADR-0061: In-Memory Multi-Tenant Quartz Scheduler

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-26 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

Spector tenants require periodic background cognitive housekeeping jobs: sleep consolidation sweeps (`ReflectPathway`), circadian homeostatic decay updates, memory health telemetry, and index compaction.

Spector executes multiple asynchronous background cognitive and maintenance routines:
- **Sleep Consolidation**: Circadian episodic-to-semantic promotion and Hebbian decay (`reflect()`).
- **REM & Creative Dreaming**: Generative scene synthesis, counterfactual policy evaluation, and insight ingestion (`DreamDaemon`).
- **Default Mode Network (DMN)**: Spontaneous wandering and narrative continuity (`DmnSpontaneousDaemon`).
- **Homeostatic Decay**: Synaptic weight stabilization and energy regulation (`HomeostaticDecayDaemon`).
- **Storage Checkpointing**: WAL compaction and memory-segment synchronization (`CheckpointDaemon`).
- **Graph Enrichment**: Entity extraction and hypergraph relation synthesis (`GraphEnrichmentDaemon`).

Historically, these routines were triggered via unmanaged threads or isolated `ScheduledExecutorService` instances, with zero unified execution history, no dynamic pause/resume/trigger control, and no structured reporting for API or UI monitoring.

## 2. Problem Statement

Configuring scheduler infrastructure in a multi-tenant embedded and server memory platform presents key trade-offs:
1. **Heavy Database Dependencies**: Traditional enterprise Quartz deployments require external relational databases (`JobStoreTX` / `JobStoreCMT`) and table schemas, creating massive deployment friction for embedded library use.
2. **Thread Contention & Leaks**: Spawning independent scheduler instances or raw Java `ScheduledExecutorService` instances per tenant causes thread proliferation, memory leaks, and uncoordinated background sweeps.
3. **Cross-Tenant Teardown Safety**: When a tenant is deactivated or evicted from hot memory, all associated cron triggers must be cleanly unscheduled without disrupting active adjacent tenants.

## 3. Decision Drivers

- **Zero Database Infrastructure**: Run completely in-process using Quartz's high-performance in-memory `RAMJobStore`.
- **Single Process Scheduler Instance**: Share a single, well-tuned thread pool across all active tenants.
- **Strict Tenant Job Isolation**: Group-namespace all JobKeys and TriggerKeys by tenant ID (`jobGroupEquals(namespaceId)`).
- **Deterministic Teardown**: Bulk-delete all tenant jobs instantly on namespace close.

## 4. Considered Options

### Option 1: Dedicated Java `ScheduledExecutorService` per Tenant
- Create a thread pool per registered namespace.
- **Verdict**: Rejected. Incurred massive thread starvation and thread stack memory bloat at >100 concurrent tenants.

### Option 2: Database-Backed Quartz Cluster (`JobStoreTX`)
- Connect to an external PostgreSQL/MySQL database for quartz clustering.
- **Verdict**: Rejected for standalone nodes and embedded library deployments. Introduces external infrastructure dependencies.

### Option 3: Shared Single-Process In-Memory Quartz (`RAMJobStore`) with Group Isolation (Selected)
- Run a single, process-wide Quartz scheduler configured with `RAMJobStore`.
- Partition jobs and triggers using tenant-specific group keys.
- **Verdict**: Accepted. Zero external dependencies, sub-millisecond scheduling, and clean lifecycle isolation.

## 5. Decision Outcome

### Subsystem Architecture & Implementation

We adopt **Quartz Scheduler** configured with **`RAMJobStore`** (zero database requirement) and a custom **`VirtualThreadPool`** implementing Quartz's `ThreadPool` SPI by delegating to Spector's concurrency framework (`ConcurrentTasks.virtualExecutor()`), structured as follows:

1. **`nucleus/spector-commons` Concurrency SPI**:
   - `VirtualThreadPool implements org.quartz.spi.ThreadPool`: Spawns/delegates job execution to the supplied `java.util.concurrent.Executor` (defaulting to `ConcurrentTasks.virtualExecutor()`).
2. **`memory/spector-memory` Core Quartz Engine**:
   - `QuartzMemoryScheduler implements MemoryScheduler`: Initialized per `SpectorMemory` instance using `DirectSchedulerFactory.createScheduler(instanceName, ...)` with `RAMJobStore` and `VirtualThreadPool`.
   - Core Jobs: `SleepConsolidationJob`, `RemDreamJob`, `DmnWanderingJob`, `HomeostaticDecayJob`, `CheckpointJob`, `GraphEnrichmentJob`.
   - `MemoryJobAuditListener implements org.quartz.JobListener`: Intercepts execution lifecycle and captures duration, status (`SUCCESS`/`FAILED`), and returned `TaskReport` (`DreamReport`, `ReflectionReport`, `CheckpointReport`) into a bounded in-memory ring-buffer (`TaskRunAuditRecord`).
3. **Multi-Tenant Per-Namespace Isolation**:
   - Each `SpectorMemory` instance owns its isolated `QuartzMemoryScheduler` and audit history (`instanceName = "spector-" + namespaceId`).
   - Standalone execution works out-of-the-box in pure Java without Spring Boot.
4. **`synapse/spector-synapse` Spring Boot Bridge**:
   - `@RestController @RequestMapping("/api/v1/tasks")` delegates REST calls directly to the active caller's `SpectorMemory.scheduler()` resolved through `UserMemoryRegistry.resolveForCurrentRequest()`.

## 6. Pros and Cons of the Options

### Consequences & Trade-offs

### Positive
- **Zero Infrastructure Maintenance**: Leverages Quartz's robust scheduling, cron evaluation, and trigger state management without writing custom scheduler state machines.
- **Zero Database Footprint**: Runs 100% in RAM with `RAMJobStore`.
- **Java 25 Virtual Threads**: High-throughput non-blocking execution via `VirtualThreadPool` SPI.
- **Full Observability**: Comprehensive audit trail with domain reports for REST, CLI (`spectorctl`), and Cortex UI.
- **Strict Multi-Tenancy**: Zero cross-tenant scheduling or audit contamination.

### Negative / Risks & Mitigations
- **RAM Ephemerality**: If the JVM restarts, in-memory audit history and paused states reset.
  - *Mitigation*: Core schedules are re-initialized deterministically from configuration (`CircadianPolicy`, `DreamConfig`, `AismeConfig`) on startup.

## 7. Implementation Plan

1. **Scheduler Configuration**: Implement `QuartzMemoryScheduler` using `RAMJobStore` with bounded worker thread pools.
2. **Lifecycle Binding**: Wire scheduler job registration into `SpectorNamespaceManager` and namespace activation hooks.
3. **Automated Teardown**: Hook `namespace.close()` directly to `getJobKeys(GroupMatcher.jobGroupEquals(namespaceId))` and `deleteJobs()`.
4. **Test Suite**: Author multi-tenant isolation and concurrent teardown unit tests.

## 8. Code Reference & Verification

All scheduler components are implemented and verified in the repository:
- **Scheduler Core**: `memory/spector-memory/src/main/java/com/spectrayan/spector/memory/scheduler/QuartzMemoryScheduler.java`
- **Teardown Verification**: Confirmed that `close()` deletes all group-matched keys, ensuring zero trigger leaks upon namespace eviction.
