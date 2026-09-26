# ADR-0054: Lifespan-Adaptive Forgetting & Retention Kernel

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-24 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---


## 1. Context

In biological organisms, forgetting is not an engineering defect or failure mode; it is an active, adaptive cognitive capability essential for generalization, noise attenuation, and energy optimization. Over a multi-decade lifespan, the human brain prunes unimportant synaptic connections while selectively consolidating identity anchors, conceptual wisdom, and highly significant episodic milestones.

### Theoretical & Neurobiological Foundations

### 1.1 Synaptic Homeostasis Hypothesis (SHY) & Adaptive Pruning
In the biological brain, wakefulness increases synaptic strength across cortical circuits, leading to energetic, volumetric, and cognitive saturation (Tononi & Cirelli, 2014). Slow-wave deep sleep mediates homeostatic synaptic downscaling, pruning weak synaptic connections while preserving and consolidating high-salience, emotionally tagged, and autobiographically critical circuits (Poe, 2017).

### 1.2 Autobiographical Memory Stratification
Human long-term memory organizes experiences into hierarchical tiers (Conway & Pleydell-Pearce, 2000):

1. **Lifetime Periods & Milestones (Core Tier)**: Invariant anchors representing lifetime milestones and relationships.
2. **General Events (Flavour Tier)**: Consolidated schema representing recurring routines and task skills.
3. **Event-Specific Knowledge (Ephemeral Tier)**: Fine-grained perceptual details that undergo rapid exponential decay unless integrated into general schemas.

---

## 2. Problem Statement

Long-lived cognitive agents accumulating memories continuously face severe storage and retrieval degradation:

1. **Linear Memory Bloat**: Without active forgetting, memory stores expand indefinitely, degrading search latency and exhausting memory-mapped resources.
2. **Retrieval Signal Dilution**: Decades-old trivia and ephemeral conversations clutter vector and graph recall indices, drowning out salient facts.
3. **Naive Time-Based Expiration**: Fixed TTLs (Time-To-Live) arbitrarily destroy valuable historic milestones and learned user preferences simply because they occurred in the past.

## 3. Decision Drivers

- **Active Synaptic Pruning**: Ground memory decay in the Synaptic Homeostasis Hypothesis (Tononi & Cirelli), selectively pruning low-strength connections during offline consolidation.
- **Stratified Retention Tiers**: Protect autobiographical identity anchors, semantic concepts, and high-importance milestones from routine decay.
- **Continuous Adaptive Thresholding**: Adjust retention thresholds dynamically based on current memory capacity and utilization pressure.
- **Sub-Millisecond Off-Heap Scoring**: Evaluate retention predicates in $<20\mu s$ during background reflection sweeps.

## 4. Considered Options

### Option 1: Static LRU/FIFO Eviction

- Discard the least-recently-used memories when storage capacity reaches 100%.
- **Verdict**: Rejected. Evicts critical historical memories that haven't been accessed recently, destroying long-term continuity.

### Option 2: Fixed Exponential Decay TTL

- Decay all memories with a single uniform half-life.
- **Verdict**: Rejected. Erases important foundational knowledge and preferences at the same rate as mundane chitchat.

### Option 3: Lifespan-Adaptive Forgetting & Dynamic Retention Gating (Selected)

- Synthesize composite importance, activation frequency, recency, and identity stratification into a continuous retention function.
- Execute adaptive pruning during sleep consolidation sweeps (`ReflectPathway`).
- **Verdict**: Accepted. Delivers biologically authentic memory lifecycles that scale across decades.

## 5. Decision Outcome

### Mathematical Formulation & Retention Dynamics

The dynamic retention threshold \(\tau(t)\) is formalized as:

```
\tau(t) = \text{clamp}\left( \tau_0 \cdot \left(1 + k \cdot \ln\left(1 + \frac{t}{T_0}\right)\right) \cdot \left(\frac{V(t)}{V_{\text{target}}}\right)^\gamma, 0.0, 1.0 \right)
```

Where:

- \(\tau(t)\): Retention cutoff at operational age \(t\).
- \(\tau_0 = 0.30\): Baseline retention cutoff.
- \(k = 0.15\): Lifespan hardening rate.
- \(t \ge 0\): Cumulative elapsed reflection epochs.
- \(T_0 = 365\): Lifespan epoch scaling constant (1 simulated year).
- \(V(t)\): Current active stored memory volume.
- \(V_{\text{target}} = 100,000\): Steady-state capacity target.
- \(\gamma = 1.2\): Volumetric capacity pressure scaling exponent.

---

### Component Specification & Subsystem Architecture

### 3.1 Kernel (`LifespanThresholdKernel.java`)

- Pure, branchless mathematical evaluation of \(\tau(t)\).
- Singularity protection for non-positive \(t\) or \(V(t)\).
- Strict bounds clamping to \([0.0, 1.0]\).

### 3.2 Lifespan Retention Controller (`LifespanRetentionController.java`)

- Maintains epoch counter and interfaces with `PartitionManager` to sample \(V(t)\).
- Implements `evaluateRetentionDecision(RememberSignal signal)` returning `RETAIN`, `CONSOLIDATE`, or `PRUNE`.
- Ensures `signal.flashbulb() == true` or \(I(o_t) \ge 0.85\) unconditionally maps to `RETAIN`.

### 3.3 Sleep Consolidation Relay (`LifespanAdaptivePruningRelay.java`)

- Integrated into `ReflectPathway` as `lifespan_adaptive_pruning`.
- Replaces static decay thresholds with dynamically evaluated \(\tau(t)\).
- Emits telemetry on total milestones preserved, flavour consolidated, and ephemeral memories pruned.

## 6. Pros and Cons of the Options

### Positive

- **Bounded Storage Footprint**: Keeps memory growth bounded within provisioned storage tiers while preserving essential knowledge.
- **Sharper Retrieval Quality**: Removing stale, low-salience noise improves top-$k$ recall precision and semantic clarity.
- **Lifelong Continuity**: Foundational facts and high-importance milestones persist indefinitely.

### Negative / Trade-offs

- **Pruning Sweep Scheduling**: Requires periodic offline or idle background sweeps (`ReflectPathway`) to execute decay calculations.
- **Irreversible Deletion Safeguards**: Must maintain safety guards ensuring memories flagged as core identity anchors are never pruned.

## 7. Implementation Plan

1. **Kernel Math**: Implement decay kernels and threshold evaluation functions in `spector-core`.
2. **Controller Layer**: Implement `LifespanRetentionController` in `spector-memory` to compute retention thresholds based on storage pressure.
3. **Reflect Pathway Integration**: Integrate `LifespanAdaptivePruningRelay` into background consolidation jobs (`ReflectConsolidationJobConfig`).
4. **Safety Verification**: Comprehensive tests verifying that identity anchors remain exempt from pruning under high memory load.

## 8. Code Reference & Verification

All lifespan controllers, score models, and consolidation components are verified in the repository:

- **Lifespan Retention Controller**:
    - `memory/spector-memory/src/main/java/com/spectrayan/spector/memory/aisme/lifespan/LifespanRetentionController.java`
- **Consolidation & Reflect Wiring**:
    - `synapse/spector-batch/src/main/java/com/spectrayan/spector/batch/ReflectConsolidationJobConfig.java`
- **Kernel Storage Scores**:
    - `memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/score/EdgeImportance.java`
