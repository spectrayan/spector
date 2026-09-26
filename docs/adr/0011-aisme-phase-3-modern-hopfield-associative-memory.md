# ADR-0011: AISME Phase 3 — Modern Hopfield Associative Memory

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

## 1. Context

Classical Hopfield networks store binary patterns with limited storage capacity ($C \approx 0.14N$). Recent advances in computational neuroscience (Krotov & Hopfield, Demircigil et al., Ramsauer et al.) introduced Modern Continuous Hopfield Networks with exponential storage capacity ($C \approx 2^{N/2}$) via Log-Sum-Exp energy functions. This mathematical formulation is isomorphic to the attention mechanism in transformers and provides a rigorous foundation for content-addressable associative memory.

## 2. Problem Statement

Standard nearest-neighbor vector search in HNSW indexes retrieves individual isolated vectors. In human cognitive recall, associative memory performs pattern completion: a degraded, noisy, or partial sensory cue reconstructs an entire holistic memory attractor. Spector needs an off-heap associative memory layer that performs single-step or few-step pattern completion across stored engrams.

## 3. Decision Drivers

- **Exponential Memory Capacity**: Store and associate thousands of dense cognitive patterns without catastrophic cross-talk.
- **Pattern Completion & Denoising**: Reconstruct complete engrams from noisy, partial, or corrupted retrieval cues.
- **Zero-Allocation SIMD Attention**: Implement continuous Hopfield energy updates using Panama Vector API kernels (`LogSumExp` / Softmax).
- **Sub-Millisecond Convergence**: Attractor dynamics must converge in 1–3 iterations.

## 4. Considered Options

### Option 1: Iterative Recurrent Neural Network (RNN)

- **Description**: Train and deploy an external recurrent neural network for auto-associative memory.
- **Advantages**: Flexible nonlinear attractor landscapes.
- **Disadvantages**: Heavy GPU/PyTorch runtime dependency; high inference latency; uninterpretable energy landscape.

### Option 2: Continuous Modern Hopfield Energy Kernel (Selected)

- **Description**: Implement continuous modern Hopfield associative dynamics: $\xi^{t+1} = X \cdot \text{softmax}(\beta X^T \xi^t)$ with energy function $E = -\text{lse}(\beta, X^T \xi) + \frac{1}{2} \|\xi\|^2$. Evaluated directly off-heap in `nucleus/spector-core` using SIMD dot products and numerically stabilized Log-Sum-Exp kernels.
- **Advantages**: Guaranteed monotonic energy minimization, exponential memory capacity, exact closed-form update rule, executes in < 50µs for 1,024-dimensional vectors.
- **Disadvantages**: Requires off-heap memory staging for attractor prototype matrices.

## 5. Decision Outcome

**Chosen Option**: Option 2 (Continuous Modern Hopfield Energy Kernel).

### Positive Consequences

- Native pattern completion: partial cues retrieve holistic, denoised memory engrams.
- Mathematically provable convergence and exponential storage capacity.
- Zero-GC SIMD implementation directly integrated into Spector's off-heap kernel.

### Negative Consequences & Trade-offs

- Prototype memory matrix $X$ requires contiguous off-heap memory allocation in `spector-kernel`.
- Temperature parameter $eta$ must be tuned to control attractor basin sharpness.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Recurrent NN** | Flexible attractor boundaries | External runtime, non-deterministic, GPU required |
| **Option 2: Modern Hopfield** | Closed-form update, exponential capacity, SIMD-native | Matrix memory footprint, requires $eta$ tuning |

## 7. Implementation Plan

1. **Phase 1**: Implement `ModernHopfieldKernel` SIMD operations (stabilized Log-Sum-Exp and softmax projection) in `nucleus/spector-core`.
2. **Phase 2**: Build `HopfieldAssociativeMemory` store in `memory/spector-memory/aisme/hopfield`.
3. **Phase 3**: Add `AssociativeCompletionRelay` to the memory pathway to reconstruct full memory contexts from partial queries.
4. **Phase 4**: Benchmark pattern reconstruction fidelity under 10% to 50% vector noise.

## 8. Code Reference & Verification

- **Primary Module(s)**: `nucleus/spector-core`, `memory/spector-memory`
- **Key Packages**: `com.spectrayan.spector.core.hopfield`, `com.spectrayan.spector.memory.aisme.hopfield`
- **Classes**: `ModernHopfieldKernel.java`, `HopfieldAssociativeMemory.java`, `AssociativePatternCompleter.java`
- **Verification Tests**: `ModernHopfieldKernelTest.java`, `AssociativePatternCompletionTest.java`
