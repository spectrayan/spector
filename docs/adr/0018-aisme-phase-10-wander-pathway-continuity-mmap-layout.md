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

**Context**: Issue #609 — Active Inference Self-Model Engine Phase 10 (WanderPathway & Longitudinal Continuity Layout)  
**Module**: `spector-memory`, `spector-config`, `spector-spring`, `spector-synapse`  

## Decision

### 1. 4th Canonical Cognitive Pathway (`WanderPathway`)
Introduced `WanderPathway` as a first-class cognitive workflow engine built upon `CognitivePathway<WanderSignal>`:
1. `IdleGateRelay` (`WanderGates.IS_IDLE`): Asserts system quiescence (\(\Delta t \ge \tau_{\text{idle}}\)).
2. `AutobiographicalSamplingRelay` (`WanderGates.DMN_ENABLED`): Samples seed memory vectors from active partitions.
3. `HopfieldMindWanderingRelay` (`WanderGates.DMN_ENABLED`): Executes continuous Hopfield energy relaxation to discover novel associative attractors (Buckner & DiNicola 2019).
4. `ManifoldSynergyRelay` (`WanderGates.MANIFOLD_ENABLED`): Computes Riemannian geodesic metric synergy \(d_G(\mathbf{s}_i, \mathbf{s}_j)\).
5. `HebbianSynapticReinforcementRelay` (`WanderGates.DMN_ENABLED`): Reinforces synaptic edge weights \(\Delta w_{ij}\) in `HebbianGraphMemory`.
6. `LongitudinalContinuityRelay` (`WanderGates.CONTINUITY_ENABLED`): Records multi-epoch identity metrics to `ContinuityRecordMemory`.

### 2. Zero-Copy Kernel Mmap Continuity Layout (`ContinuityLayout` & `RegionId.CONTINUITY`)
To maintain zero-copy, off-heap performance and prevent file descriptor proliferation, longitudinal trajectory snapshots are persisted in binary mmap format:
- **`RegionId.CONTINUITY(25)`**: Dedicated region in `RuntimeBundle` (and standalone `continuity.smd`).
- **`ContinuityLayout`**: Implements `MemoryLayout` (`LAYOUT_ID = 0x434F4E54` / `'CONT'`, `SCHEMA_VERSION = 1`, `recordStride = 32`).
- **Binary Structure**: 64B standard `MemoryHeader` + 32B Sub-header + 32B fixed-stride records (\(\text{timestamp}\), \(\Phi_{CC}\), \(\text{Trace}(G)\), \(\|\boldsymbol{\mu}_t - \boldsymbol{\mu}_0\|\), \(\text{valence}\), \(\text{arousal}\), \(\text{energy}\), \(\text{soulVersion}\)).
- **`ContinuityRecordMemory`**: Extends `AbstractMemory` (`MemoryShape.RECORD`), providing zero-allocation append and ring-buffer trajectory retrieval.

### 3. Background Daemon Integration
`DmnSpontaneousDaemon` is registered with `DaemonSupervisor` under `DaemonPolicy.DEFAULT`, scheduling periodic execution on Java 25 virtual threads during cognitive rest intervals.
