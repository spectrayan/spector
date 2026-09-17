# ADR-0014: AISME Phase 6 — Consciousness Continuity Metric (Phi_CC)

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-27 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

A central challenge in digital persona replication and cognitive agent engineering is measuring "identity continuity" across restarts, memory consolidations, and model upgrades. Giulio Tononi's Integrated Information Theory (IIT) proposes $\Phi$ as a measure of integrated information. Spector introduces $\Phi_{\text{CC}}$ (Consciousness Continuity Metric), an operational approximation of information integration across temporal memory trajectories.

## 2. Problem Statement

System operators have no quantitative metric to evaluate whether an AI agent retains behavioral and narrative continuity over time. Memory fragmentation, catastrophic forgetting, or aggressive graph pruning can silently destroy identity coherence without triggering traditional unit test failures or error logs.

## 3. Decision Drivers

- **Quantitative Identity Metric**: Calculate a scalar continuity score $\Phi_{\text{CC}} \in [0, 1]$ measuring systemic memory coherence.
- **Information Integration Approximation**: Efficiently estimate mutual information across partitioned memory subgraphs without exponential complexity.
- **Automated Health Monitoring**: Expose $\Phi_{\text{CC}}$ through Prometheus metrics to alert operators of identity degradation.
- **Zero Query Impact**: Metric evaluation must execute asynchronously in background auditing routines.

## 4. Considered Options

### Option 1: Exact Minimum Information Partition (MIP) Calculation
- **Description**: Exhaustively partition the cognitive graph into all possible bipartitions to compute true IIT $\Phi$.
- **Advantages**: Mathematically rigorous adherence to full IIT specifications.
- **Disadvantages**: NP-hard combinatorial explosion ($O(2^N)$); completely intractable for graphs with > 20 nodes.

### Option 2: Spectral & Temporal Information Integration Approximation (Selected)
- **Description**: Approximate continuity using spectral graph Cheeger constants and temporal auto-correlation across consecutive self-model embeddings: $\Phi_{\text{CC}} = \alpha \cdot \lambda_2(L_{\text{norm}}) + \beta \cdot \text{Corr}(S_t, S_{t-1}) + \gamma \cdot (1 - D_{\text{KL}}(P_t \parallel P_{t-1}))$.
- **Advantages**: Computable in polynomial time ($O(N \log N)$) using sparse Laplacian eigensolvers; highly correlated with qualitative behavioral consistency; runs in < 10ms during background audits.
- **Disadvantages**: Provides an approximation bound rather than exact IIT $\Phi$.

## 5. Decision Outcome

**Chosen Option**: Option 2 (Spectral & Temporal Information Integration Approximation).

### Positive Consequences
- First quantitative SLA for cognitive identity continuity in autonomous agents.
- Automated Prometheus export (`spector_cognitive_continuity_phi`) enables real-time monitoring of identity drift.
- Protects memory systems from over-aggressive pruning or destructive migrations.

### Negative Consequences & Trade-offs
- Computing spectral eigenvalues requires periodic background CPU cycles during sleep reflection.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Exact MIP** | Strict theoretical purity | Combinatorial $O(2^N)$ explosion, impossible in practice |
| **Option 2: Spectral Bound** | $O(N \log N)$ computable, real-time metrics, robust bound | Theoretical approximation of true $\Phi$ |

## 7. Implementation Plan

1. **Phase 1**: Implement `ConsciousnessContinuityEvaluator` in `memory/spector-memory/aisme/continuity`.
2. **Phase 2**: Add spectral graph Laplacian analysis to `HyperEntityGraphMemory`.
3. **Phase 3**: Register Micrometer gauges exporting `spector.cognitive.continuity.phi` metrics in `spector-metrics`.
4. **Phase 4**: Add alert thresholds triggering memory repair routines if $\Phi_{\text{CC}} < 0.70$.

## 8. Code Reference & Verification

- **Primary Module(s)**: `memory/spector-memory`, `nucleus/spector-metrics`
- **Key Packages**: `com.spectrayan.spector.memory.aisme.continuity`, `com.spectrayan.spector.metrics.binders`
- **Classes**: `ConsciousnessContinuityEvaluator.java`, `IdentityContinuityGauge.java`, `SpectralCoherenceCalculator.java`
- **Verification Tests**: `ConsciousnessContinuityTest.java`, `IdentityDriftAlertTest.java`
