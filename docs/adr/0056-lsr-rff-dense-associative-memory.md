# ADR-0056: Log-Sum-ReLU (LSR) & Random Fourier Features (RFF) Dense Associative Memory

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

In Spector's Active Inference Self-Model Engine (AISME Phase 12), memory retrieval and cognitive mind-wandering require high-capacity pattern completion that scales beyond traditional vector indexing.

### Context & Architectural Background

ADR-0011 (AISME Phase 3) successfully introduced the **Continuous Modern Hopfield Network (MHAMN)** based on the standard Log-Sum-Exp (LSE) formulation (Ramsauer et al., 2021). While functional, production profiling and cognitive benchmarks have exposed three key bottlenecks:

1. **Multi-Step Convergence Overhead**: Iterative relaxation ($3\text{--}5$ loops of scaled softmax) creates a $\sim 0.25\,\text{ms}$ latency overhead in `RecallPathway`.
2. **Infinite Support Cross-Talk**: Gaussian kernels assign nonzero attention weights to all candidates, introducing slight semantic noise from distant/irrelevant vectors.
3. **Local Candidate Scope ($\mathcal{O}(D \cdot K)$)**: Attractor dynamics can only operate on pre-filtered top-$N$ candidates; global whole-brain associative resonance across millions of lifetime memories is impossible at query time.

---

## 2. Problem Statement

Standard associative recall mechanisms face severe limitations in production cognitive architectures:

1. **Iterative Convergence Latency**: Classical and modern continuous Hopfield networks require multiple numerical settlement loops, making latency non-deterministic.
2. **Exponential Tail Interference**: Softmax attention functions evaluate over all stored memory tokens, allowing distant, irrelevant memories to leak noise into recalled patterns.
3. **Linear Scan Overhead in Dreaming**: Simulating spontaneous mind-wandering and dreaming requires evaluating global free-energy landscapes, which scales poorly when computed against millions of individual memory vectors.

## 3. Decision Drivers

- **Deterministic Latency**: Settle candidate memories in a single vectorized pass.
- **Zero Heap Overhead**: Run associative kernels off-heap via Panama Vector API without garbage collection allocation.
- **Compact Epanechnikov Support**: Hard zeroing outside the attraction basin eliminates spurious pattern interference.
- **Constant-Time Diffusion**: Support continuous Langevin diffusion over a fixed-size memory hologram.

## 4. Considered Options

### Option 1: Iterative Softmax Continuous Hopfield Network
- Maintain standard Demircigil/Hopfield exponential energy formulations.
- **Verdict**: Rejected for real-time inference due to variable loop iterations and $O(N)$ energy evaluation overhead.

### Option 2: Approximate Nearest Neighbor (ANN) HNSW Probing
- Rely purely on vector index traversal.
- **Verdict**: Incomplete. ANN does not reconstruct missing features or perform holographic state synthesis.

### Option 3: Two-Tier LSR Settlement and PRF Holographic Tensor (Selected)
- Use Log-Sum-ReLU (LSR) Epanechnikov energy for single-step pattern completion.
- Maintain Positive Random Features (PRF) holographic tensor for constant-time global energy evaluation.
- **Verdict**: Accepted. Delivers deterministic sub-100us settlement and constant-time mind-wandering.

## 5. Decision Outcome

### Architectural Decisions & Subsystem Layout

### 2.1 Package & Class Layout

#### 1. SIMD Kernel in `spector-core`:
```
nucleus/spector-core/src/main/java/com/spectrayan/spector/core/
├── similarity/
│   ├── HopfieldKernel.java             # Existing LSE kernel
│   └── LsrHopfieldKernel.java          # [NEW] Single-pass Epanechnikov SIMD kernel
└── rff/
    ├── RandomFeatureProjector.java     # [NEW] SIMD Positive Random Feature mapper
    └── LsrKernelConfig.java            # [NEW] Kernel parameter records
```

#### 2. Engine & Off-Heap Storage in `spector-memory`:
```
memory/spector-memory/src/main/java/com/spectrayan/spector/memory/
├── aisme/hopfield/
│   ├── KernelType.java                 # [NEW] Enum: LSE, LSR (default: LSR)
│   ├── ContinuousHopfieldNetwork.java # Updated to support KernelType
│   └── LsrAttractorEngine.java        # [NEW] T=1 exact single-step settlement
├── kernel/shape/
│   └── DistributedMemoryTensor.java    # [NEW] Off-heap Panama FFM Hologram (MemoryShape.HOLOGRAPHIC)
└── wander/relay/
    └── RffMindWanderingRelay.java     # [NEW] Global Langevin diffusion over Tensor T
```

---

### 2.2 Mathematical Specifications

#### 1. Log-Sum-ReLU (LSR) Candidate Settlement:
$$E_{\text{LSR}}(\mathbf{v}; \mathbf{X}) = -\log \sum_{i=1}^N \text{ReLU}\left(1 - \frac{\beta}{2} \|\mathbf{v} - \mathbf{x}_i\|^2\right)$$
- **Exact Convergence**: Single-step ($T=1$) gradient descent reaches the exact pattern when inside its basin radius $r_c = \sqrt{2/\beta}$.
- **Zero Transcendental CPU Cost**: Evaluated using SIMD `fma` and `FloatVector.max(0.0f)` without computing `Math.exp()`.

#### 2. Positive Random Features (PRF) Holographic Memory:
$$\mathbf{\Phi}(\mathbf{x}) = \frac{\exp\left(-\frac{\beta \|\mathbf{x}\|^2}{2}\right)}{\sqrt{Y}} \begin{bmatrix} \exp(\sqrt{\beta} \boldsymbol{\omega}_1^T \mathbf{x}) \\ \vdots \\ \exp(\sqrt{\beta} \boldsymbol{\omega}_Y^T \mathbf{x}) \end{bmatrix}, \quad \mathbf{T} = \sum_{\mu=1}^K \mathbf{\Phi}(\boldsymbol{\xi}^\mu) \in \mathbb{R}^Y$$
- **Constant Time $\mathcal{O}(Y)$**: Global associative energy $\tilde{E}(\mathbf{v}; \mathbf{T}) = -\log \langle \mathbf{\Phi}(\mathbf{v}), \mathbf{T}\rangle$ is evaluated in $< 10\,\mu\text{s}$ independent of memory count $K$.

---

### 2.3 Off-Heap Memory Layout (`MemoryShape.HOLOGRAPHIC`)

`DistributedMemoryTensor` allocates an off-heap Panama `MemorySegment` with a 64-byte aligned header followed by $Y$ 32-bit floating-point accumulator lanes ($Y = 2048$, 8KB footprint):
- **Magic**: `0x5350454354` (`SPECT`)
- **Version**: `0x00000001`
- **Projection Dim ($Y$)**: 2048
- **Vector Dim ($D$)**: 768
- **Pattern Count ($K$)**: 64-bit counter

---

## 6. Pros and Cons of the Options

### Positive
- **Guaranteed Single-Step Settlement**: Mathematical proof guarantees exact retrieval in one vectorized step.
- **Strict Energy Bounding**: Epanechnikov kernel eliminates floating-point softmax underflow and overflow.
- **Sub-100us Execution**: Highly optimized SIMD kernel executes directly on off-heap memory segments.

### Negative / Trade-offs
- **Radius Parameterization**: Requires calibrating support radius $R$ and threshold $\Delta$ according to embedding dimension.
- **Pre-computed Random Projections**: PRF requires generating and caching orthogonal Gaussian projection matrices.

## 7. Implementation Plan

### Performance & Quality Budgets

| Metric | Target |
|:---|:---|
| **LSR Settlement Latency (50 candidates, 768-dim)** | $< 35\,\mu\text{s}$ ($6.8\times$ faster than LSE) |
| **RFF Global Energy Evaluation ($100\text{k}$ memories)** | $< 8.5\,\mu\text{s}$ |
| **Off-Heap Memory Footprint** | Exactly $8\,\text{KB}$ per workspace |
| **Single-Step L2 Reconstruction Error** | $< 10^{-6}$ inside basin |

---

### Rollout & Delivery Plan

1. **Phase 1**: Merge `LsrHopfieldKernel.java` in `spector-core` and wire into `HopfieldAssociativeRelay`.
2. **Phase 2**: Add `DistributedMemoryTensor` in `spector-memory` and integrate with WAL lifecycle.
3. **Phase 3**: Activate `RffMindWanderingRelay` in `WanderPathway` for global DMN associative synthesis.

## 8. Code Reference & Verification

All associative memory components are implemented and verified in the repository:
- **Core Math Kernel**: `nucleus/spector-core/src/main/java/com/spectrayan/spector/core/cognitive/LsrHopfieldKernel.java`
- **Memory Network Engine**: `memory/spector-memory/src/main/java/com/spectrayan/spector/memory/aisme/hopfield/ContinuousHopfieldNetwork.java`
- **Unit and Benchmark Verification**: `nucleus/spector-core/src/test/java/com/spectrayan/spector/core/similarity/LsrHopfieldKernelTest.java`
