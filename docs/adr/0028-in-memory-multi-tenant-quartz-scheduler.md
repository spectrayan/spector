# ADR-0028-QUARTZ: In-Memory Multi-Tenant Quartz Scheduler

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-27 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

**Author:** Titan (@titanspectrayan, Solutions Architect)  
**Reviewer:** Jarvis (Technical Leadpectrayan, CTO) & Project Lead  
**Status:** Accepted  
**Target Modules:** `nucleus/spector-commons`, `nucleus/spector-bom`, `memory/spector-memory`, `synapse/spector-synapse`  
**Issue:** #683  
**Date:** August 27, 2026  

---

## 1. Context & Problem Statement

Spector executes multiple asynchronous background cognitive and maintenance routines:
- **Sleep Consolidation**: Circadian episodic-to-semantic promotion and Hebbian decay (`reflect()`).
- **REM & Creative Dreaming**: Generative scene synthesis, counterfactual policy evaluation, and insight ingestion (`DreamDaemon`).
- **Default Mode Network (DMN)**: Spontaneous wandering and narrative continuity (`DmnSpontaneousDaemon`).
- **Homeostatic Decay**: Synaptic weight stabilization and energy regulation (`HomeostaticDecayDaemon`).
- **Storage Checkpointing**: WAL compaction and memory-segment synchronization (`CheckpointDaemon`).
- **Graph Enrichment**: Entity extraction and hypergraph relation synthesis (`GraphEnrichmentDaemon`).

Historically, these routines were triggered via unmanaged threads or isolated `ScheduledExecutorService` instances, with zero unified execution history, no dynamic pause/resume/trigger control, and no structured reporting for API or UI monitoring.

## 2. Decision

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

## 3. Consequences

### Positive
- **Zero Infrastructure Maintenance**: Leverages Quartz's robust scheduling, cron evaluation, and trigger state management without writing custom scheduler state machines.
- **Zero Database Footprint**: Runs 100% in RAM with `RAMJobStore`.
- **Java 25 Virtual Threads**: High-throughput non-blocking execution via `VirtualThreadPool` SPI.
- **Full Observability**: Comprehensive audit trail with domain reports for REST, CLI (`spectorctl`), and Cortex UI.
- **Strict Multi-Tenancy**: Zero cross-tenant scheduling or audit contamination.

### Negative / Risks & Mitigations
- **RAM Ephemerality**: If the JVM restarts, in-memory audit history and paused states reset.
  - *Mitigation*: Core schedules are re-initialized deterministically from configuration (`CircadianPolicy`, `DreamConfig`, `AismeConfig`) on startup.
