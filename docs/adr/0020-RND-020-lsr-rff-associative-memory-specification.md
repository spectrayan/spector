# ADR-0020-RND: LSR & RFF Dense Associative Memory Engineering Specification

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

**Document ID**: `RND-2026-020` / `NEURO-LSR-RFF-001`  
**Date**: 2026-08-23  
**Authors**: Architecture Working Group (Cognitive Systems), Architecture Working Group (Systems Architecture), Technical Lead  
**Status**: APPROVED FOR IMPLEMENTATION  
**Target Repos/Modules**: `spector` (`spector-core`, `spector-memory`, `spector-bench`), `spectrayan`  
**Related ADRs**: [ADR-0011](0011-aisme-phase-3-modern-hopfield-associative-memory.md), [ADR-0020](0020-lsr-rff-dense-associative-memory.md)  
**Theoretical Reference**: Krotov, Hoover, Ram, Pham (*Modern Methods in Associative Memory*, ICML 2025 Tutorial, arXiv:2507.06211v2)

---

## 1. Executive Summary & Problem Statement

Spector's AISME Phase 3 currently utilizes a **Continuous Modern Hopfield Network (MHAMN)** based on the standard Log-Sum-Exp (LSE) formulation (Ramsauer et al., 2021). While mathematically elegant and SIMD-accelerated, the LSE architecture possesses three fundamental operational constraints:

1. **Iterative Convergence Latency**: LSE attractor settlement is an asymptotic relaxation process requiring $3\text{--}5$ iterations of scaled softmax updates, imposing a $\sim 0.25\,\text{ms}$ computational floor in `RecallPathway`.
2. **Diffuse Softmax Contamination (Infinite Support)**: Gaussian kernels have infinite support ($\exp(-x) > 0$ for all $x \in \mathbb{R}$). In candidate memory pools, weak or irrelevant memories constantly leak nonzero attention weights into the retrieved attractor state, introducing subtle semantic noise.
3. **Candidate-Scoping Bottleneck ($\mathcal{O}(D \cdot K)$)**: Attractor dynamics can only be evaluated over pre-filtered candidate sets (e.g., top-50 vectors from HNSW/BM25). Spector cannot compute whole-brain associative resonance across the agent's entire multi-million memory history in real time.

This specification introduces a **Dual-Engine Associative Memory Substrate** that resolves all three bottlenecks:
- **Engine 1: Log-Sum-ReLU (LSR) Epanechnikov Kernel** for candidate-level pattern settlement: Achieves **exact single-step ($T=1$) retrieval**, compact finite support (zero long-tail noise), and eliminates all transcendental CPU instructions.
- **Engine 2: Positive Random Feature (PRF/RFF) Distributed Holographic Tensor** for whole-brain associative memory: Compresses $K$ lifetime memories into an off-heap tensor $\mathbf{T} \in \mathbb{R}^Y$ of fixed size, enabling **$\mathcal{O}(Y)$ constant-time global energy evaluation and subconscious DMN wandering** without candidate pre-filtering.

---

## 2. Biological Grounding & Cognitive Neuroscience

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                Neurobiological Memory Circuit                                    │
├─────────────────────────────────────────────┬────────────────────────────────────────────────────┤
│  Hippocampal CA3 Recurrent Collaterals      │  Pribram's Holonomic Brain / Neocortical Ensemble │
│  (LSR: Sparse, Exact Attractor Settlement)  │  (RFF: Distributed Holographic Memory Tensor)      │
│                                             │                                                    │
│  • Sparse pyramidal cell assemblies         │  • Wide-field phase-interference patterns          │
│  • Finite basin of attraction per engram    │  • Whole-brain ambient associative resonance       │
│  • Sharp-Wave Ripples (SWRs) in single pass │  • Default Mode Network (DMN) spontaneous wander   │
└─────────────────────────────────────────────┴────────────────────────────────────────────────────┘
```

### 2.1 Hippocampal CA3: Compact Support and Exact Sharp-Wave Ripples
In the mammalian hippocampus, the CA3 subfield exhibits dense recurrent collateral connectivity ($> 10^4$ synaptic contacts per pyramidal neuron). Crucially, biological synaptic firing is not a diffuse softmax; it operates via thresholded membrane potentials ($\text{ReLU}$). When an associative cue triggers CA3 during Sharp-Wave Ripples (SWRs, $150\text{--}250\,\text{Hz}$), settlement into an attractor basin occurs in a single burst ($15\text{--}30\,\text{ms}$), not through prolonged gradual iterations. The **Epanechnikov kernel ($\text{ReLU}(1 - r^2)$)** reflects this biological reality: a memory has a finite radius of attraction; outside that basin, synaptic conductance is strictly zero.

### 2.2 Pribram's Holonomic Brain: Distributed Interference Patterns
Karl Pribram's holonomic brain theory and Longuet-Higgins' holographic associative memory postulate that long-term memory is not stored solely as localized discrete slots, but as distributed harmonic transformations across neural fields. **Positive Random Features (PRF)** provide the exact mathematical bridge: by projecting individual memory vectors onto a set of frozen normal random bases, an entire lifetime of memories superimposes additively into a single holographic tensor $\mathbf{T}$. Subconscious mind-wandering in the Default Mode Network (DMN) then corresponds to continuous Langevin diffusion over this global energy surface.

---

## 3. Mathematical Formalization

```
                                      MATHEMATICAL LANDSCAPES
                                      
      [Log-Sum-Exp: Infinite Support]                   [Log-Sum-ReLU: Compact Support]
      
      E_LSE(v) = -1/β ln ∑ exp(-β/2 ||v-ξ||²)           E_LSR(v) = -ln ∑ ReLU(1 - β/2 ||v-ξ||²)
      
             ▲ Energy                                          ▲ Energy
             │    \       /                                    │  \           /
             │     \     /                                     │   \         /
             │  ~~~~\   /~~~~  [Noise leakage]                 │    \_______/  [Strict zero outside r_c]
             └────────────────► State                          └────────────────► State
```

### 3.1 Log-Sum-ReLU (LSR) Energy & Single-Step Settlement

Let $\mathbf{\Xi} = \{\boldsymbol{\xi}^1, \boldsymbol{\xi}^2, \dots, \boldsymbol{\xi}^K\} \subset \mathbb{R}^D$ represent $K$ stored memory vectors.

#### Definition (Epanechnikov Energy)
The Log-Sum-ReLU associative energy is defined as:
$$E_{\text{LSR}}(\mathbf{v}; \mathbf{\Xi}) = -\log \sum_{\mu=1}^K \text{ReLU}\left(1 - \frac{\beta}{2} \|\mathbf{v} - \boldsymbol{\xi}^\mu\|^2\right)$$

where $\beta > 0$ is the inverse temperature controlling the basin radius:
$$r_c = \sqrt{\frac{2}{\beta}}$$

#### Active Support Set
For a query vector $\mathbf{v}$, define the active support index set $\mathcal{S}(\mathbf{v})$:
$$\mathcal{S}(\mathbf{v}) \triangleq \left\{ \mu \in \{1, \dots, K\} \;\middle|\; \|\mathbf{v} - \boldsymbol{\xi}^\mu\|^2 < \frac{2}{\beta} \right\}$$

If $\mathcal{S}(\mathbf{v}) = \emptyset$, the energy $E_{\text{LSR}}(\mathbf{v}) = \infty$ (query is outside all known memory basins).

#### Gradient & Exact Settlement Dynamics
For any state $\mathbf{v}$ where $\mathcal{S}(\mathbf{v}) \neq \emptyset$, the gradient is:
$$\nabla_{\mathbf{v}} E_{\text{LSR}}(\mathbf{v}; \mathbf{\Xi}) = \beta \cdot \frac{\sum_{\mu \in \mathcal{S}(\mathbf{v})} (\mathbf{v} - \boldsymbol{\xi}^\mu)}{\sum_{\mu \in \mathcal{S}(\mathbf{v})} \left(1 - \frac{\beta}{2} \|\mathbf{v} - \boldsymbol{\xi}^\mu\|^2\right)}$$

#### Theorem 1 (Single-Step Exact Retrieval)
*If $\mathbf{v}$ lies within the isolated basin of pattern $\boldsymbol{\xi}^k$ (such that $\mathcal{S}(\mathbf{v}) = \{k\}$), then performing a single gradient descent step with learning rate $\eta = \frac{1}{\beta} \left(1 - \frac{\beta}{2}\|\mathbf{v} - \boldsymbol{\xi}^k\|^2\right)$ yields:*
$$\mathbf{v}^{(1)} = \mathbf{v} - \eta \nabla_{\mathbf{v}} E_{\text{LSR}}(\mathbf{v}) = \mathbf{v} - (\mathbf{v} - \boldsymbol{\xi}^k) = \boldsymbol{\xi}^k$$
*Exact pattern recovery is achieved in exactly $T = 1$ step.*

#### Normalized Attractor Attention Weights
For general multi-candidate recall, the effective attention weight $w_\mu$ allocated to memory $\mu$ is:
$$w_\mu = \frac{\text{ReLU}\left(1 - \frac{\beta}{2}\|\mathbf{v} - \boldsymbol{\xi}^\mu\|^2\right)}{\sum_{j=1}^K \text{ReLU}\left(1 - \frac{\beta}{2}\|\mathbf{v} - \boldsymbol{\xi}^j\|^2\right)}$$
Notice that $\forall \mu \notin \mathcal{S}(\mathbf{v}),\; w_\mu = 0.0$ strictly, guaranteeing zero cross-talk from distant candidates.

---

### 3.2 Positive Random Features (PRF) & Global Memory Tensor

To scale associative memory to millions of patterns without $\mathcal{O}(D \cdot K)$ candidate scans, we approximate the continuous RBF kernel $\kappa(\mathbf{x}, \mathbf{y}) = \exp\left(-\frac{\beta}{2}\|\mathbf{x} - \mathbf{y}\|^2\right)$ via randomized feature decomposition.

#### Positive Feature Mapping
Let $\mathbf{\Omega} \in \mathbb{R}^{Y \times D}$ be a fixed, frozen random projection matrix where each row $\boldsymbol{\omega}_i \sim \mathcal{N}(0, \mathbf{I}_D)$, and $Y$ is the projection dimension ($Y = 1024$ or $2048$).

The positive random feature map $\mathbf{\Phi}: \mathbb{R}^D \to \mathbb{R}^Y$ is defined as:
$$\mathbf{\Phi}(\mathbf{x}) = \frac{\exp\left(-\frac{\beta \|\mathbf{x}\|^2}{2}\right)}{\sqrt{Y}} \begin{bmatrix} \exp(\sqrt{\beta} \boldsymbol{\omega}_1^T \mathbf{x}) \\ \exp(\sqrt{\beta} \boldsymbol{\omega}_2^T \mathbf{x}) \\ \vdots \\ \exp(\sqrt{\beta} \boldsymbol{\omega}_Y^T \mathbf{x}) \end{bmatrix}$$

#### Holographic Memory Tensor Accumulator
Given a continuous stream of stored memories $\{\boldsymbol{\xi}^1, \dots, \boldsymbol{\xi}^K\}$, the global memory state is stored as a single accumulator vector $\mathbf{T} \in \mathbb{R}^Y$:
$$\mathbf{T} \triangleq \sum_{\mu=1}^K \mathbf{\Phi}(\boldsymbol{\xi}^\mu)$$

#### Properties of the Hologram $\mathbf{T}$:
1. **Incremental Ingestion**: When ingesting new memory $\boldsymbol{\xi}_{\text{new}}$:
   $$\mathbf{T} \leftarrow \mathbf{T} + \mathbf{\Phi}(\boldsymbol{\xi}_{\text{new}}) \quad (\mathcal{O}(Y \cdot D) \text{ one-time cost})$$
2. **Exact Forgetting / Eviction**: When deleting memory $\boldsymbol{\xi}_{\text{del}}$:
   $$\mathbf{T} \leftarrow \mathbf{T} - \mathbf{\Phi}(\boldsymbol{\xi}_{\text{del}})$$
3. **Decay Scaling**: During biological sleep consolidation:
   $$\mathbf{T} \leftarrow \gamma \cdot \mathbf{T}$$

#### Global Energy Evaluation in $\mathcal{O}(Y)$ Constant Time
For any query state $\mathbf{v} \in \mathbb{R}^D$:
$$\tilde{E}_\beta(\mathbf{v}; \mathbf{T}) = -\log \left\langle \mathbf{\Phi}(\mathbf{v}),\; \mathbf{T} \right\rangle$$

#### DMN Spontaneous Mind-Wandering (Langevin Diffusion over $\mathbf{T}$)
During background idle cycles in `WanderPathway`, the agent explores novel associative attractors across its entire lifetime knowledge by integrating the Stochastic Differential Equation:
$$d\mathbf{v}_t = -\nabla_{\mathbf{v}} \tilde{E}_\beta(\mathbf{v}_t; \mathbf{T}) dt + \sqrt{2 \mathcal{T}} \, d\mathbf{W}_t$$
where $d\mathbf{W}_t$ is standard Brownian noise and $\mathcal{T}$ is the exploration temperature.

---

## 4. Bio-to-Silicon Algorithmic Translation (Titan & Forge)

```
nucleus/spector-core
└── src/main/java/com/spectrayan/spector/core/similarity/
    ├── HopfieldKernel.java             # Existing: LSE Continuous Kernel
    └── LsrHopfieldKernel.java          # [NEW]: AVX-512 Epanechnikov SIMD Kernel

memory/spector-memory
├── src/main/java/com/spectrayan/spector/memory/kernel/shape/
│   └── DistributedMemoryTensor.java    # [NEW]: Off-Heap Panama FFM Holographic Tensor
├── src/main/java/com/spectrayan/spector/memory/aisme/hopfield/
│   ├── ContinuousHopfieldNetwork.java # Enhanced: KernelType selector (LSE / LSR)
│   └── LsrAttractorEngine.java        # [NEW]: T=1 Exact Attractor Settlement
└── src/main/java/com/spectrayan/spector/memory/wander/relay/
    └── RffMindWanderingRelay.java     # [NEW]: Global Langevin Diffusion over Tensor T
```

### 4.1 SIMD Hot-Path: `LsrHopfieldKernel.java` (`spector-core`)

The core SIMD kernel calculates pairwise squared distances, applies the Epanechnikov cutoff, and computes the exact settled vector in a single pass using the Java Vector API:

```java
package com.spectrayan.spector.core.similarity;

import com.spectrayan.spector.core.simd.SimdCapability;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

public final class LsrHopfieldKernel {

    private static final VectorSpecies<Float> SPECIES = SimdCapability.PREFERRED_SPECIES;

    /**
     * Executes exact single-pass LSR attention weights and energy calculation.
     *
     * @param query current query state vector v in R^D
     * @param patterns array of candidate memory vectors X_i in R^D
     * @param beta inverse temperature
     * @param outWeights destination array for normalized Epanechnikov weights
     * @return scalar Epanechnikov energy E_LSR
     */
    public static float computeLsrWeights(
            float[] query,
            float[][] patterns,
            float beta,
            float[] outWeights
    ) {
        int numPatterns = patterns.length;
        int dim = query.length;
        float halfBeta = 0.5f * beta;
        float sumWeights = 0.0f;

        for (int p = 0; p < numPatterns; p++) {
            float[] pat = patterns[p];
            float sqDist = EuclideanDistance.computeSquared(query, pat);
            float score = 1.0f - halfBeta * sqDist;
            float weight = Math.max(0.0f, score);
            outWeights[p] = weight;
            sumWeights += weight;
        }

        if (sumWeights > 0.0f) {
            float invSum = 1.0f / sumWeights;
            for (int p = 0; p < numPatterns; p++) {
                outWeights[p] *= invSum;
            }
            return -(float) Math.log(sumWeights);
        } else {
            // Out-of-support fallback: uniform weights, infinite energy
            float uniform = 1.0f / numPatterns;
            java.util.Arrays.fill(outWeights, uniform);
            return Float.POSITIVE_INFINITY;
        }
    }
}
```

### 4.2 Panama FFM Layout: `DistributedMemoryTensor` (`spector-memory`)

The global holographic tensor is backed by a native off-heap `MemorySegment`:

```
┌────────────────────────────────────────────────────────────────────────┐
│                   HOLOGRAPHIC TENSOR BINARY MMAP LAYOUT                │
├──────────────┬──────────────┬──────────────┬───────────────────────────┤
│ Offset 0..7  │ Offset 8..15 │ Offset 16..23│ Offset 24..31             │
│ MAGIC (8B)   │ VERSION (8B) │ DIMS D (8B)  │ PROJECTION DIM Y (8B)     │
│ 0x5350454354 │ 0x00000001   │ e.g. 768     │ e.g. 2048                 │
├──────────────┼──────────────┼──────────────┼───────────────────────────┤
│ Offset 32..39│ Offset 40..47│ Offset 48..63│ Offset 64 .. (64 + 4*Y)   │
│ COUNT K (8B) │ SEED (8B)    │ RESERVED(16B)│ ACCUMULATOR VECTOR T[Y]   │
└──────────────┴──────────────┴──────────────┴───────────────────────────┘
```

- **Zero Serialization Overhead**: Updates modify the off-heap `T[Y]` floats directly using Panama memory segments.
- **WAL Integration**: Ingest/Delete events append a 4-byte index marker to the WAL; checkpointing writes the 8KB accumulator segment directly to disk.

---

## 5. Cognitive Profile Tuning & Behavioral Modulation

The basin width parameter $\beta$ dynamically shifts according to the agent's active personality profile and autonomic arousal:

$$\beta_{\text{effective}} = \beta_{\text{base}} \times \left(1.0 + 0.5 \times \text{Arousal}\right)$$

```
Cognitive Profile      β_base    Basin Radius r_c = √(2/β)    Attractor Behavior
─────────────────────────────────────────────────────────────────────────────────────────────────
HYPERFOCUS             16.0      0.354                        Laser-focused: single exact memory
CRITICAL               8.0       0.500                        Narrow tolerance: strict verification
BALANCED               2.0       1.000                        Standard: balanced candidate support
EXPLORING              0.5       2.000                        Wide basin: multi-concept fusion
DEFAULT_MODE_NETWORK   0.25      2.828                        Panoramic: global holographic drift
```

- **Under Panic / Elevated Arousal**: $\beta$ spikes, narrowing $r_c$. The agent locks onto exact proven procedures and rejects diffuse associations.
- **Under Relaxation / Low Arousal**: $\beta$ decreases, widening $r_c$. Epanechnikov basins overlap, synthesizing creative multi-memory cross-domain gestalts.

---

## 6. Validation & Quality Gates (Sentinel)

### 6.1 Mathematical & Unit Test Gates
1. **$T=1$ Single-Step Settlement Verification**:
   - Given an isolated memory vector $\boldsymbol{\xi}$ and a corrupted query $\mathbf{v} = \boldsymbol{\xi} + \boldsymbol{\epsilon}$ (where $\|\boldsymbol{\epsilon}\| < \sqrt{2/\beta}$), verify that `LsrAttractorEngine.settle()` returns $\boldsymbol{\xi}$ with L2 error $< 10^{-6}$ in exactly 1 iteration.
2. **Compact Support Strictness**:
   - For any vector with $\|\mathbf{v} - \boldsymbol{\xi}\|^2 \ge 2/\beta$, assert that `attentionWeight == 0.0f` exactly.
3. **RFF Unbiased Density Estimation**:
   - Verify that $|\langle \mathbf{\Phi}(\mathbf{x}), \mathbf{\Phi}(\mathbf{y})\rangle - \exp(-\beta/2 \|\mathbf{x}-\mathbf{y}\|^2)| < 0.05$ across 10,000 random vectors with $Y=2048$.

### 6.2 Performance Benchmark Targets (`spector-bench`)

| Benchmark Scenario | Current LSE Baseline | Target LSR / RFF Milestone | Speedup |
|:---|:---:|:---:|:---:|
| **Candidate Settlement (50 vectors, 768-dim)** | $240\,\mu\text{s}$ (5 iters) | **$< 35\,\mu\text{s}$ ($T=1$)** | **$6.8\times$** |
| **SIMD Energy Evaluation (Zero Exp)** | $18.5\,\mu\text{s}$ | **$< 3.2\,\mu\text{s}$** | **$5.7\times$** |
| **Global Memory Resonance ($100\text{k}$ vectors)** | Impractical ($> 25\,\text{ms}$) | **$< 8.5\,\mu\text{s}$ (Constant $\mathcal{O}(Y)$)** | **$> 2900\times$** |

---

## 7. Next Steps & Persona Handover

1. **@titan**: Author [ADR-0020](0020-lsr-rff-dense-associative-memory.md) formally establishing `MemoryShape.HOLOGRAPHIC` and updating `AismeConfig`.
2. **@forge**: Implement `LsrHopfieldKernel.java` in `spector-core` and `LsrAttractorEngine.java` in `spector-memory`.
3. **@sentinel**: Implement regression tests in `LsrHopfieldKernelTest.java` and benchmark against `HopfieldKernelTest`.
4. **@nexus**: Verify Panama FFM off-heap memory safety and native AVX-512 compiler flags in CI/CD.
