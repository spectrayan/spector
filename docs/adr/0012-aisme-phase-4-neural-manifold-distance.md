# ADR-0012: AISME Phase 4 — Neural Manifold Distance (NMD)

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

**Context**: Issue #591 — Active Inference Self-Model Engine Phase 4  
**Module**: `spector-memory`, `spector-core`  

## Decision

### Package Structure

New SIMD kernel in `spector-core`:
```
nucleus/spector-core/src/main/java/com/spectrayan/spector/core/similarity/
└── NeuralManifoldDistance.java       # SIMD Riemannian quadratic form & manifold similarity
```

New package `com.spectrayan.spector.memory.aisme.manifold` within `spector-memory`:
```
memory/spector-memory/src/main/java/com/spectrayan/spector/memory/aisme/
├── manifold/
│   ├── PersonalMetricTensor.java     # Immutable record: diagonal vector d + low-rank factors U
│   ├── CognitiveManifold.java        # Thread-safe manifold manager & metric tensor container
│   └── ManifoldConsolidator.java     # Derives metric tensor updates from Hebbian graph & chains
└── relay/
    └── ManifoldRerankRelay.java      # RecallPathway relay integration
```

### Architectural Decisions

1. **Riemannian Metric Tensor Formulation**:
   - Replaces flat Euclidean/cosine distance with $d_{\text{NMD}}^2(x, y) = (x-y)^T M_{\text{person}} (x-y)$.
   - Decomposes $M_{\text{person}} = \text{diag}(\mathbf{d}) + \mathbf{U} \mathbf{U}^T$ where $\mathbf{d} \in \mathbb{R}^D$ is positive diagonal scaling ($d_k \ge 1.0$) and $\mathbf{U} \in \mathbb{R}^{D \times r}$ ($r \ll D$) captures cross-dimensional subjective associations.

2. **SIMD-Accelerated Mahalanobis Quadratic Form**:
   - `NeuralManifoldDistance` computes $(x-y)^T \text{diag}(\mathbf{d}) (x-y) + \|\mathbf{U}^T (x-y)\|^2$ in a single vectorized pass using `FloatVector` and `DotProduct`.
   - Complexity is $O(D + rD)$, which adds negligible overhead ($< 1.5\,\mu\text{s}$) for 768-dim embeddings.

3. **Cognitive Manifold & Reflection Consolidation**:
   - `PersonalMetricTensor` is immutable and versioned.
   - `ManifoldConsolidator` adjusts $\mathbf{d}$ and $\mathbf{U}$ during background consolidation by analyzing Hebbian co-activation frequencies and temporal chain adjacency.
   - Frequently co-activated concept axes receive higher precision / shorter geodesic distance.

4. **RecallPathway Relay Sequencing**:
   - `ManifoldRerankRelay` runs in the recall chain, modulating candidate scores via Riemannian manifold similarity $\exp(-d_{\text{NMD}}^2 / 2\sigma^2)$.
   - Transparent fallback to standard scoring if unconfigured.

### Performance Budget

- Metric evaluation per candidate: $< 1.5\,\mu\text{s}$.
- Batch evaluation for 50 candidates: $< 0.08\,\text{ms}$.
- Zero JNI, zero GC allocations on query hot path.

**Approved** — implemented in PR #592.
