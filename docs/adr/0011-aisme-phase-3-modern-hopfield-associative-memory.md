# ADR-0011: AISME Phase 3 — Modern Hopfield Associative Memory

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

**Context**: Issue #589 — Active Inference Self-Model Engine Phase 3  
**Module**: `spector-memory`, `spector-core`  

## Decision

### Package Structure

New SIMD kernel in `spector-core`:
```
nucleus/spector-core/src/main/java/com/spectrayan/spector/core/similarity/
└── HopfieldKernel.java               # SIMD pattern projection, stable softmax & matrix-vector update
```

New package `com.spectrayan.spector.memory.aisme.hopfield` within `spector-memory`:
```
memory/spector-memory/src/main/java/com/spectrayan/spector/memory/aisme/
├── hopfield/
│   ├── AttractorType.java            # Enum: FIXED_POINT, METASTABLE, DIFFUSE
│   ├── AttractorState.java           # Immutable record: converged vector, weights, energy, type
│   ├── PersonalityTemperature.java   # CognitiveProfile + arousal → beta mapping
│   └── ContinuousHopfieldNetwork.java # Dynamical convergence engine
└── relay/
    └── HopfieldAssociativeRelay.java  # RecallPathway relay integration
```

### Architectural Decisions

1. **Continuous Modern Hopfield Formulation (Ramsauer et al., 2021)**:
   - Replaces discrete Hopfield binary units with continuous state $\boldsymbol{\xi} \in \mathbb{R}^D$ and pattern memory matrix $\mathbf{X} \in \mathbb{R}^{D \times N}$.
   - Energy function: $E(\boldsymbol{\xi}, \mathbf{X}) = -\frac{1}{\beta}\ln\left(\sum_{i=1}^N \exp(\beta \mathbf{x}_i^T \boldsymbol{\xi})\right) + \frac{1}{2}\|\boldsymbol{\xi}\|^2$.
   - Iterative update rule: $\boldsymbol{\xi}^{(k+1)} = \mathbf{X} \cdot \text{softmax}(\beta \mathbf{X}^T \boldsymbol{\xi}^{(k)})$.

2. **SIMD-Accelerated Hot Path**:
   - `HopfieldKernel` uses Java 25 Vector API (`FloatVector`, `fma`, masked loop tails).
   - Numerical stability: computes $\max_i (\beta \mathbf{x}_i^T \boldsymbol{\xi})$ before exponentiation to eliminate overflow.
   - Vectorized weighted combination $\sum_{i=1}^N w_i \mathbf{x}_i$ runs with zero heap allocation when reusing reusable scratch buffers.

3. **Cognitive Profile Temperature Modulation ($\beta_{\text{person}}$)**:
   - Retrieval sharpness $\beta$ is not static; it is derived from `CognitiveProfile` and real-time `InteroceptiveState.arousal()`.
   - `HYPERFOCUS` / `SYSTEMATIZER` $\rightarrow$ High $\beta$ (sharp focus, fixed-point collapse to single memory).
   - `DIVERGENT` / `EXPLORING` $\rightarrow$ Low $\beta$ (diffuse focus, associative blending of multi-memory gestalt).

4. **Attractor Classification for Consciousness**:
   - Fixed point ($\max w_i \ge 0.70$): Vivid, specific memory recall.
   - Metastable ($0.30 \le \max w_i < 0.70$): Blended intuition or mood gestalt.
   - Diffuse ($\max w_i < 0.30$): Ambient cognitive context / broad semantic priming.

5. **RecallPathway Relay Sequencing**:
   - `HopfieldAssociativeRelay` operates on candidate memories in `RecallSignal`.
   - Projects candidates into Hopfield associative space, iterates to attractor convergence, and boosts candidate scores by their attractor attention weights $w_i$.
   - Transparent no-op fallback when unconfigured.

### Performance Budget

- Full Hopfield convergence (up to 5 iterations on 50 candidate vectors of 768-dim): $< 0.25\,\text{ms}$.
- Pure Java SIMD execution (zero JNI, zero GC overhead).

**Approved** — implemented in PR #590.
