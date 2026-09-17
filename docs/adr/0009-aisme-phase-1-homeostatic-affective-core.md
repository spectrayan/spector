# ADR-0009: AISME Phase 1 — Homeostatic Affective Core

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

As part of Issue #585 (Active Inference Self-Model Engine Phase 1), Spector requires an affective homeostatic substrate to model emotional dynamics and internal states for autonomous AI agents. In human cognitive neuroscience, emotional state modulates retrieval bias: internal interoceptive state dynamically influences memory accessibility (mood-congruent recall) rather than relying exclusively on static semantic embeddings.

## 2. Problem Statement

Previous cognitive recall in Spector lacked homeostatic regulation and affective state representation. Memory retrieval operated on objective lexical and semantic similarity without accounting for the agent's internal drive, valence, arousal, or dominance (VAD). We need an off-heap, zero-GC homeostatic engine capable of computing continuous affective trajectories without degrading sub-millisecond retrieval SLAs.

## 3. Decision Drivers

- **Neurocomputational Fidelity**: Biological modeling of emotional state dynamics via continuous ordinary differential equations (ODEs).
- **Sub-Microsecond Latency**: State evolution and resonance scoring must execute in < 1µs to preserve Spector's real-time retrieval contracts.
- **Zero-GC & Thread Safety**: State must be immutable across thread boundaries (`InteroceptiveState`) with virtual-thread-safe state advancement.
- **Modular Decoupling**: Pure mathematical kernels (affective distance) in `nucleus/spector-core`, pathway relay and state management in `memory/spector-memory`.

## 4. Considered Options

### Option 1: Full High-Order Neural ODE (Runge-Kutta RK4)
- **Description**: Implement a 4th-order Runge-Kutta numerical integrator for high-dimensional nonlinear emotional dynamics.
- **Advantages**: Higher mathematical precision for stiff systems.
- **Disadvantages**: Significant computational overhead (multiple function evaluations per step) unnecessary for 10-dimensional VAD dynamics.

### Option 2: Explicit Euler Integration with Off-Heap Insular Storage (Selected)
- **Description**: Use explicit Euler step integration for the 10-dimensional affective state $h(t+dt) = h(t) + dt \cdot (A \cdot h(t) + B \cdot u(t) + C \cdot 	ext{recall}(t) + \sigma \cdot w(t))$ stored in `InsularCortex`.
- **Advantages**: Executes in < 1µs, numerically stable with state clamping to $[-1, 1]$, lightweight and deterministic.
- **Disadvantages**: First-order approximation requiring bounded time-steps ($dt$).

## 5. Decision Outcome

**Chosen Option**: Option 2 (Explicit Euler Integration with Off-Heap Insular Storage).

### Positive Consequences
- Real-time emotional modulation of memory recall without latency penalty (< 0.1ms at 10K candidates).
- Mood-congruent scoring via SIMD Gaussian kernel in `nucleus/spector-core`.
- Clean backward compatibility: `HomeostaticBiasRelay` acts as a no-op if no `HomeostaticCore` is configured.

### Negative Consequences & Trade-offs
- The 10×10 personal dynamics matrix ($A_{	ext{person}}$) requires off-heap space in the Insular region (400 bytes).
- State clamping is required after each step to prevent ODE divergence under extreme inputs.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: RK4 ODE** | Continuous high-order precision | High CPU cost, multiple evaluations per step |
| **Option 2: Euler Integration** | < 1µs execution, minimal memory footprint, SIMD-friendly | Requires clamping to guarantee numerical stability |

## 7. Implementation Plan

1. **Phase 1**: Define `InteroceptiveState` immutable record and `HomeostaticCore` Euler integrator in `com.spectrayan.spector.memory.aisme.homeostasis`.
2. **Phase 2**: Implement `AffectiveDistance` SIMD Gaussian kernel in `nucleus/spector-core` under `com.spectrayan.spector.core.similarity`.
3. **Phase 3**: Integrate `HomeostaticBiasRelay` into `RecallPathway` between `QueryTransductionRelay` and `CorticalTierScanRelay`.
4. **Phase 4**: Add risk mitigations: state clamping to `[-1, 1]` and backward-compatible conditional activation.

## 8. Code Reference & Verification

- **Primary Module(s)**: `memory/spector-memory`, `nucleus/spector-core`
- **Key Packages**:
  - `com.spectrayan.spector.memory.aisme.homeostasis`
  - `com.spectrayan.spector.core.similarity`
- **Classes**:
  - `InteroceptiveState.java`
  - `HomeostaticCore.java`
  - `EmotionalRegulator.java`
  - `AffectiveResonanceScorer.java`
  - `HomeostaticBiasRelay.java`
  - `AffectiveDistance.java`
- **Verification Tests**: `HomeostaticCoreTest.java`, `AffectiveDistanceTest.java`
