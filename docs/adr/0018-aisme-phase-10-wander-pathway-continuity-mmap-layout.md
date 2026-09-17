# ADR-0018: AISME Phase 10 — WanderPathway & Kernel Continuity Layout

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-22 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

Issue #609 (Active Inference Self-Model Engine Phase 10) introduces the fourth canonical cognitive pathway (`WanderPathway`) and longitudinal continuity persistence across `spector-memory`, `spector-kernel`, `spector-config`, `spector-spring`, and `spector-synapse`. Prior to Phase 10, Spector operated purely on reactive pathways (`RecallPathway`, `RememberPathway`) and periodic reflection (`ReflectPathway`). Between user queries, the memory engine remained quiescent.

## 2. Problem Statement

Biological intelligence maintains continuous spontaneous activity during quiet resting periods via the Default Mode Network (DMN), performing exploratory associative traversals, finding unexpected connections, and stabilizing self-referential identity. In Spector, absent a dedicated spontaneous pathway, idle periods were unutilized for cognitive consolidation. Furthermore, tracking longitudinal consciousness continuity ($\Phi_{\text{CC}}$) over time lacked a compact, zero-allocation binary storage layout, risking high GC overhead and file-descriptor exhaustion if persisted as plain files.

## 3. Decision Drivers

- **4th Canonical Cognitive Pathway**: Establish `WanderPathway` for resting-state Default Mode Network (DMN) spontaneous activity.
- **Zero-Allocation Binary Mmap Layout**: Longitudinal consciousness and affective trajectory snapshots must be stored in a compact, fixed-stride off-heap layout.
- **Sub-Microsecond Ring-Buffer Access**: Enable high-speed sliding-window analytics across longitudinal state vectors.
- **Supervised Virtual-Thread Execution**: Mind-wandering daemons must run cooperatively on Java 25 virtual threads during rest intervals without interfering with high-priority recall queries.

## 4. Considered Options

### Option 1: File-Per-Snapshot JSON / Parquet Logging
- **Description**: Serialize trajectory snapshots as JSON or Parquet files on disk.
- **Advantages**: Simple inspection with command-line tools.
- **Disadvantages**: Heavy GC allocations; file descriptor proliferation; slow point-lookup during live introspective querying.

### Option 2: Generic Engram Storage Slabs
- **Description**: Store continuity metrics as standard unstructured engrams in `spector-memory`.
- **Advantages**: Reuses existing memory partitions.
- **Disadvantages**: High per-record overhead (64B header + vector + text) for simple 32-byte numerical telemetry vectors; pollutes semantic memory spaces.

### Option 3: Dedicated `ContinuityLayout` in `RuntimeBundle` + 6-Relay `WanderPathway` (Selected)
- **Description**: Design a specialized 32-byte fixed-stride binary memory layout (`ContinuityLayout`) in `RegionId.CONTINUITY(25)` paired with a dedicated `WanderPathway`.
- **Advantages**: Zero GC; sub-microsecond stride lookups; bounded storage footprint; clean separation between telemetry and cognitive memories.
- **Disadvantages**: Requires dedicated layout implementation in `spector-kernel`.

## 5. Decision Outcome

**Chosen Option**: Option 3 (Dedicated `ContinuityLayout` & 6-Relay `WanderPathway`).

### Architectural Implementation:

#### 1. 4th Canonical Cognitive Pathway (`WanderPathway`)
Structured as a sequential 6-relay pipeline over `WanderSignal`:
1. `IdleGateRelay` (`WanderGates.IS_IDLE`): Asserts system quiescence ($\Delta t \ge \tau_{\text{idle}}$).
2. `AutobiographicalSamplingRelay` (`WanderGates.DMN_ENABLED`): Samples seed memory vectors from active partitions.
3. `HopfieldMindWanderingRelay` (`WanderGates.DMN_ENABLED`): Executes continuous Hopfield energy relaxation to discover novel associative attractors (Buckner & DiNicola 2019).
4. `ManifoldSynergyRelay` (`WanderGates.MANIFOLD_ENABLED`): Computes Riemannian geodesic metric synergy $d_G(\mathbf{s}_i, \mathbf{s}_j)$.
5. `HebbianSynapticReinforcementRelay` (`WanderGates.DMN_ENABLED`): Reinforces synaptic edge weights $\Delta w_{ij}$ in `HebbianGraphMemory`.
6. `LongitudinalContinuityRelay` (`WanderGates.CONTINUITY_ENABLED`): Records multi-epoch identity metrics to `ContinuityLayout`.

#### 2. Zero-Copy Kernel Mmap Continuity Layout (`ContinuityLayout`)
To maintain zero-copy, off-heap performance and prevent file descriptor proliferation, longitudinal trajectory snapshots are persisted in binary mmap format:
- **`RegionId.CONTINUITY(25)`**: Dedicated region in `RuntimeBundle` (and standalone `continuity.smd`).
- **`ContinuityLayout`**: Implements `MemoryLayout` (`LAYOUT_ID = 0x434F4E54` / `'CONT'`, `SCHEMA_VERSION = 1`, `recordStride = 32`).
- **Binary Structure**: 64B standard `MemoryHeader` + 32B Sub-header + 32B fixed-stride records ($\text{timestamp}$, $\Phi_{\text{CC}}$, $\text{Trace}(G)$, $\|\boldsymbol{\mu}_t - \boldsymbol{\mu}_0\|$, $\text{valence}$, $\text{arousal}$, $\text{energy}$, $\text{soulVersion}$).

#### 3. Background Daemon Integration
`DmnSpontaneousDaemon` is registered with `DaemonSupervisor` under `DaemonPolicy.DEFAULT`, scheduling periodic execution on Java 25 virtual threads during cognitive rest intervals.

### Positive Consequences
- Realizes spontaneous biological resting-state cognition without user prompt triggers.
- Compact, durable off-heap persistence for consciousness and affective telemetry.
- Seamless coordination with `DaemonSupervisor` and virtual thread infrastructure.

### Negative Consequences & Trade-offs
- Adds background CPU activity during idle periods (gated by configurable quiescence thresholds).

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: JSON/Parquet** | Human readable | GC churn, FD explosion, slow sliding-window reads |
| **Option 2: Generic Engram** | Reuses memory slabs | Storage bloat, semantic pollution |
| **Option 3: ContinuityLayout** | Zero GC, 32B stride, sub-microsecond access | New layout class required in kernel |

## 7. Implementation Plan

1. **Phase 1**: Define `RegionId.CONTINUITY(25)` and implement `ContinuityLayout` in `memory/spector-kernel/layout`.
2. **Phase 2**: Build `WanderPathway` and the 6 constituent synaptic relays in `memory/spector-memory/pathway/wander`.
3. **Phase 3**: Create `DmnSpontaneousDaemon` and register with `DaemonSupervisor`.
4. **Phase 4**: Add JMH benchmarks and end-to-end idle quiescence tests.

## 8. Code Reference & Verification

- **Primary Module(s)**: `memory/spector-memory`, `memory/spector-kernel`, `nucleus/spector-commons`
- **Key Packages**: `com.spectrayan.spector.memory.pathway.wander`, `com.spectrayan.spector.kernel.layout`, `com.spectrayan.spector.memory.aisme.dmn`
- **Classes**: `WanderPathway.java`, `ContinuityLayout.java`, `DmnSpontaneousDaemon.java`, `IdleGateRelay.java`, `AutobiographicalSamplingRelay.java`, `HopfieldMindWanderingRelay.java`, `ManifoldSynergyRelay.java`, `HebbianSynapticReinforcementRelay.java`, `LongitudinalContinuityRelay.java`
- **Verification Tests**: `WanderPathwayTest.java`, `ContinuityLayoutTest.java`
