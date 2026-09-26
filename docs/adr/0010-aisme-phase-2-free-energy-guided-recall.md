# ADR-0010: AISME Phase 2 — Free-Energy Guided Recall

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-23 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

In Karl Friston's active inference framework, biological brains minimize Variational Free Energy ($F = 	ext{Complexity} - 	ext{Accuracy}$) to maintain cognitive homeostasis and resolve epistemic uncertainty. Standard vector retrieval maximizes similarity (accuracy) but ignores model complexity and informational surprise, leading to redundant, highly repetitive context retrieval.

## 2. Problem Statement

Cognitive recall in autonomous agents frequently retrieves redundant engrams that confirm existing priors without delivering epistemic value. We need an objective function in Spector that balances semantic relevance against informational novelty, penalizing redundant representations while prioritizing surprise-reducing engrams.

## 3. Decision Drivers

- **Active Inference Objective**: Formulate recall as minimizing variational free energy: $F(q) = D_{\text{KL}}(q(\theta) \parallel p(\theta)) - \mathbb{E}_{q}[\ln p(y \mid \theta)]$.
- **SIMD-Accelerated Surprise Scoring**: Approximate KL divergence and prediction error in off-heap vector kernels.
- **Dynamic Exploration/Exploitation Balance**: Balance precision-weighted sensory prediction errors against confidence bounds.
- **Seamless Pipeline Integration**: Inject free-energy scoring into the existing multi-phase retrieval pipeline.

## 4. Considered Options

### Option 1: Iterative Gradient Descent over Latent Representations

- **Description**: Optimize free energy via gradient steps in embedding space during query time.
- **Advantages**: Exact variational approximation.
- **Disadvantages**: Prohibitive query latency (10–50ms); violates sub-millisecond retrieval SLAs.

### Option 2: Analytical Free-Energy Ranking Kernel (Selected)

- **Description**: Formulate a closed-form approximation of free energy combining Gaussian prediction error (accuracy) and empirical entropy penalization (complexity): $\text{Score}(m) = S_{\text{semantic}}(q, m) - \lambda \cdot D_{\text{prior}}(m \parallel \mu_{\text{context}})$. Implemented as an off-heap SIMD scoring stage.
- **Advantages**: Sub-microsecond execution (< 1µs per candidate); direct SIMD vectorization; fully deterministic.
- **Disadvantages**: Requires maintaining running context centroids ($\mu_{	ext{context}}$).

## 5. Decision Outcome

**Chosen Option**: Option 2 (Analytical Free-Energy Ranking Kernel).

### Positive Consequences

- Cognitive recall actively balances relevant information with epistemic novelty.
- Eliminates repetitive echo-chamber retrieval in conversational memory.
- Sub-microsecond execution preserves real-time response budgets.

### Negative Consequences & Trade-offs

- Requires calibrating the complexity weighting parameter $\lambda$.
- Context centroid updates require running exponential moving average calculations.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Gradient Descent** | Theoretical exactness | Unacceptable query latency (10–50ms) |
| **Option 2: Analytical Kernel** | < 1µs latency, SIMD vectorized, zero allocations | Approximate complexity penalty |

## 7. Implementation Plan

1. **Phase 1**: Implement `FreeEnergyScorer` in `memory/spector-memory/aisme/freeenergy`.
2. **Phase 2**: Add `FreeEnergyGuidedRecallRelay` to the recall pipeline between tier scanning and final reranking.
3. **Phase 3**: Implement SIMD vector variance and centroid tracking in `nucleus/spector-core`.
4. **Phase 4**: Validate against epistemic benchmarks verifying reduction in retrieved context redundancy.

## 8. Code Reference & Verification

- **Primary Module(s)**: `memory/spector-memory`, `nucleus/spector-core`
- **Key Packages**: `com.spectrayan.spector.memory.aisme.freeenergy`, `com.spectrayan.spector.core.similarity`
- **Classes**: `FreeEnergyGuidedRecallRelay.java`, `FreeEnergyScorer.java`, `SurpriseEstimator.java`
- **Verification Tests**: `FreeEnergyRecallTest.java`, `EpistemicNoveltyBenchmarkTest.java`
