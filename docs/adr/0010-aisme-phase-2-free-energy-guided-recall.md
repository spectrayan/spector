# ADR-0010-AISME: AISME Phase 2 — Free-Energy Guided Recall

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

**Context**: Issue #587 — Active Inference Self-Model Engine Phase 2  
**Module**: `spector-memory`, `spector-core`  

## Decision

### Package Structure

New SIMD kernel in `spector-core`:
```
nucleus/spector-core/src/main/java/com/spectrayan/spector/core/similarity/
└── FreeEnergyKernel.java             # SIMD Gaussian KL divergence & precision weighting
```

New package `com.spectrayan.spector.memory.aisme.fegr` within `spector-memory`:
```
memory/spector-memory/src/main/java/com/spectrayan/spector/memory/aisme/
├── fegr/
│   ├── MentalStatePosterior.java     # Immutable record: mean + precision vectors
│   ├── GenerativeSelfModel.java      # Prior p(s|m) & observation mapping
│   ├── FreeEnergyCalculator.java     # Variational free energy & ΔF computation
│   └── MentalStateTracker.java       # Thread-safe continuous posterior manager
└── relay/
    └── FreeEnergyGuidedRelay.java    # RecallPathway relay integration
```

### Architectural Decisions

1. **Gaussian Variational Approximation**:
   - Approximate posterior $q(s_t) = \mathcal{N}(\boldsymbol{\mu}_q, \text{diag}(\boldsymbol{\pi}_q^{-1}))$, where $\boldsymbol{\pi}_q$ is the precision vector (inverse variance).
   - Generative prior $p(s|m) = \mathcal{N}(\boldsymbol{\mu}_p, \text{diag}(\boldsymbol{\pi}_p^{-1}))$, initialized from `AgentSoul` identity embedding and `CognitiveProfile`.
   - Closed-form variational free energy calculation $\mathcal{F}(q) = D_{\text{KL}}[q \| p] - \mathbb{E}_q[\log p(o|s)]$.

2. **SIMD-Accelerated FreeEnergyKernel**:
   - Vectorized single-pass computation of diagonal Gaussian KL divergence and expected log-likelihood using `jdk.incubator.vector.FloatVector`.
   - AVX2 / AVX-512 preferred species with masked loop tail handling. Zero allocation on hot paths.

3. **Memory Conditioning & $\Delta \mathcal{F}$ Reduction**:
   - Each candidate memory provides evidence vector $e_{\mu_i}$.
   - Updated posterior $q(s_t | \mu_i)$ computed via precision-weighted Bayesian cue combination.
   - $\Delta \mathcal{F}(\mu_i) = \mathcal{F}(q(s_t)) - \mathcal{F}(q(s_t | \mu_i))$ measures situational ambiguity reduction.

4. **Multi-Factor Free-Energy Relevance Score (FERS)**:
   - $\text{FERS}(\mu_i | s_t, o_t) = \alpha \cdot \text{sim}(e_q, e_{\mu_i}) + \beta \cdot \text{sigmoid}(\Delta \mathcal{F}(\mu_i)) + \gamma \cdot \mathcal{A}(\mu_i, s_t)$
   - Fuses semantic retrieval, predictive surprise reduction, and homeostatic affective resonance (Phase 1).

5. **Thread Safety & Virtual Thread Compatibility**:
   - `MentalStateTracker` uses `ReentrantLock` for state mutation and atomic state snapshots.
   - `MentalStatePosterior` is an immutable record, safe for concurrent sharing across virtual threads.

6. **RecallPathway Relay Sequencing**:
   - `FreeEnergyGuidedRelay` executes after `CorticalTierScanRelay` / `HomeostaticBiasRelay` and enhances candidate scores before associative graph expansion.
   - Graceful fallback: If FEGR is unconfigured, the relay is a transparent pass-through.

### Performance Budget

- SIMD KL divergence & $\Delta \mathcal{F}$ per candidate: $< 1.2\,\mu\text{s}$ for 768-dim vectors.
- Total latency overhead on 50 candidate set: $< 0.08\,\text{ms}$.

**Approved** — implemented in PR #588.
