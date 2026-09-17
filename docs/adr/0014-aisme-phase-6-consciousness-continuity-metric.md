# ADR-0014: AISME Phase 6 — Consciousness Continuity Metric (Phi_CC)

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

**Context**: Issue #595 — Active Inference Self-Model Engine Phase 6  
**Module**: `spector-memory`, `spector-core`  

## Decision

### Package Structure

New SIMD kernel in `spector-core`:
```
nucleus/spector-core/src/main/java/com/spectrayan/spector/core/similarity/
└── IntegratedInformationKernel.java # SIMD Gram matrix, Cholesky log-det, and Gaussian IIT Multi-Information
```

New packages in `spector-memory`:
```
memory/spector-memory/src/main/java/com/spectrayan/spector/memory/aisme/
├── phi/
│   ├── ConsciousnessContinuityState.java # Immutable record: rawPhi, soulAlignment, compositePhiCC, isCohesive
│   ├── IntegratedInformationCalculator.java # Gaussian IIT multi-information & MIP calculation
│   └── ConsciousnessContinuityEvaluator.java # Evaluator, soul distance modulation & fragmentation detector
│
└── relay/
    └── ConsciousnessContinuityRelay.java # RecallPathway relay for Phi_CC evaluation & holistic ranking
```

### Architectural Decisions

1. **Gaussian Integrated Information Theory (IIT) Formulation**:
   - Replaces disconnected similarity heuristics with Gaussian Multi-Information on candidate memory subgraphs:
     $$\mathbb{I}(X) = -\sum_{i=1}^N \ln(L_{ii})$$
     where $L$ is the Cholesky factor of the regularized kernel Gram matrix $K_{\text{reg}} = K + \lambda I$.
   - Evaluates Minimum Information Partition (MIP) to extract irreducible holistic synergy $\Phi(X) = \mathbb{I}(X) - [\mathbb{I}(A) + \mathbb{I}(B)]$.

2. **Composite $\Phi_{\text{CC}}$ Soul Alignment**:
   - Multiplies irreducible holistic synergy $\Phi(X)$ with the Gaussian alignment to the persona's core `AgentSoul`:
     $$\Phi_{\text{CC}}(X, \text{soul}) = \max(0, \Phi(X)) \cdot \exp\left(-\frac{\|\bar{\mathbf{x}} - \mathbf{x}_{\text{soul}}\|^2}{2\sigma_{\text{soul}}^2}\right)$$

3. **Cognitive Cohesion & Fragmentation Defense**:
   - When $\Phi_{\text{CC}} < \theta_{\text{cohesion}}$, flags the candidate set as fragmented, preventing incoherent persona drift and guiding constructive completion.

4. **RecallPathway Relay Integration**:
   - `ConsciousnessContinuityRelay` computes and attaches `ConsciousnessContinuityState` to the `RecallSignal` trace/breakdown and boosts candidates that maximize holistic graph integration.
   - 100% backward compatible pass-through when unconfigured.

### Performance Budget

- Full $N \times N$ Cholesky decomposition ($N \le 20$) + MIP evaluation: $< 0.08\,\text{ms}$.
- Zero heap allocation on hot loops.

**Approved** — implemented in PR #596.
