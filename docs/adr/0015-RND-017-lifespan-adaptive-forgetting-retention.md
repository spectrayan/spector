# ADR-0015-RND: Lifespan-Adaptive Forgetting & Retention Kernel

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

**Spec Identifier**: RND-2026-017  
**Category**: Cognitive Architecture / Active Inference Self-Model Engine (AISME)  
**Authors**: Architecture Working Group (Cognitive Systems), Architecture Working Group (Systems Architecture)  
**Status**: Approved  
**Target Release**: Spector 0.1.0-alpha (Phase 7)  
**Related Specifications**: [RND-2026-016](0014-RND-016-multimodal-composite-importance-scoring.md), ADR-0015 (`adr-0015-lifespan-adaptive-forgetting-retention.md`)  

---

## 1. Theoretical & Neurobiological Foundations

### 1.1 Synaptic Homeostasis Hypothesis (SHY) & Adaptive Pruning
In the biological brain, wakefulness increases synaptic strength across cortical circuits, leading to energetic, volumetric, and cognitive saturation (Tononi & Cirelli, 2014). Slow-wave deep sleep mediates homeostatic synaptic downscaling, pruning weak synaptic connections while preserving and consolidating high-salience, emotionally tagged, and autobiographically critical circuits (Poe, 2017).

### 1.2 Autobiographical Memory Stratification
Human long-term memory organizes experiences into hierarchical tiers (Conway & Pleydell-Pearce, 2000):
1. **Lifetime Periods & Milestones (Core Tier)**: Invariant anchors representing lifetime milestones and relationships.
2. **General Events (Flavour Tier)**: Consolidated schema representing recurring routines and task skills.
3. **Event-Specific Knowledge (Ephemeral Tier)**: Fine-grained perceptual details that undergo rapid exponential decay unless integrated into general schemas.

---

## 2. Mathematical Formulation

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

## 3. Component Specification & System Wiring

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
