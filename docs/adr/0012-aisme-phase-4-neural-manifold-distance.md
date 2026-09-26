# ADR-0012: AISME Phase 4 — Neural Manifold Distance (NMD)

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-25 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

In biological brains, semantic representations do not reside in flat Euclidean vector spaces. Cortical activations lie on curved, low-dimensional Riemannian manifolds constrained by cognitive context and internal state. Standard Euclidean ($L_2$) or Cosine distance assumes flat geometric space, failing to capture semantic relatedness along curved cognitive trajectories.

## 2. Problem Statement

Cosine distance produces uniform distance metrics regardless of the agent's current task or cognitive domain. Two memories separated by a constant angular distance may be conceptually adjacent within one cognitive context (e.g. coding tasks) but completely unrelated in another (e.g. emotional crisis). Spector requires a context-deformable metric space that computes geodesic distance across neural manifolds.

## 3. Decision Drivers

- **Riemannian Manifold Geometry**: Compute distance along curved semantic manifolds rather than through extrinsic flat Euclidean space.
- **Context-Modulated Metric Tensor**: Allow the metric tensor $G(x)$ to deform dynamically based on current working memory context.
- **SIMD Performance Budget**: Geodesic distance approximation must execute within 2x of standard cosine similarity.
- **Zero-Allocation Execution**: No vector allocation during metric tensor contraction.

## 4. Considered Options

### Option 1: Full Geodesic Integration (Dijkstra over k-NN Graph)

- **Description**: Construct a k-nearest-neighbor manifold graph and compute shortest path lengths via Dijkstra's algorithm.
- **Advantages**: Accurate geodesic estimation along discrete manifold samples.
- **Disadvantages**: High computational overhead ($O(N \log N)$); graph construction latency unacceptable during query evaluation.

### Option 2: Mahalanobis Metric Tensor Deformation (Selected)

- **Description**: Approximate the Riemannian metric tensor via a context-conditioned diagonal or low-rank precision matrix: $d_G^2(x, y) = (x - y)^T G (x - y) = \sum_i g_i (x_i - y_i)^2$. The metric tensor elements $g_i$ deform in real time based on the agent's active cognitive profile and emotional valence.
- **Advantages**: Vectorized via Java Panama Vector API; executes in < 0.5µs; dynamic domain adaptation with zero graph traversal overhead.
- **Disadvantages**: Captures local ellipsoidal deformation rather than arbitrary global topological holes.

## 5. Decision Outcome

**Chosen Option**: Option 2 (Mahalanobis Metric Tensor Deformation).

### Positive Consequences

- Context-sensitive recall: semantic distance dynamically expands or contracts based on cognitive task relevance.
- Ultra-low latency: SIMD-accelerated tensor contraction executes at near-cosine throughput.
- Full compatibility with existing vector index candidates.

### Negative Consequences & Trade-offs

- Requires maintaining and updating the metric tensor diagonal ($G$) per cognitive namespace.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: k-NN Geodesic** | Exact manifold curve tracking | Graph traversal latency, high memory overhead |
| **Option 2: Metric Tensor** | < 0.5µs SIMD, dynamic deformation, zero GC | Local curvature approximation |

## 7. Implementation Plan

1. **Phase 1**: Implement `NeuralManifoldDistance` SIMD kernel in `nucleus/spector-core/similarity`.
2. **Phase 2**: Build `MetricTensorManager` in `memory/spector-memory/aisme/manifold` to update tensor diagonals from cognitive profiles.
3. **Phase 3**: Integrate manifold distance scoring into `CorticalTierScanRelay`.
4. **Phase 4**: Benchmark retrieval precision on context-dependent disambiguation test suites.

## 8. Code Reference & Verification

- **Primary Module(s)**: `nucleus/spector-core`, `memory/spector-memory`
- **Key Packages**: `com.spectrayan.spector.core.similarity`, `com.spectrayan.spector.memory.aisme.manifold`
- **Classes**: `NeuralManifoldDistance.java`, `MetricTensorManager.java`, `ManifoldScoringRelay.java`
- **Verification Tests**: `NeuralManifoldDistanceTest.java`, `ContextDeformationTest.java`
