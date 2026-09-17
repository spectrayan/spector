# ADR-0021: Nucleus Symmetric Hardware Abstraction Layer (HAL)

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

**Approver**: Bharat (CEO)  
**Target Repository**: `spectrayan/spector`  

---

## 1. Context and Problem Statement

Spector is a high-performance cognitive memory and vector search architecture written in Java 25. The system performs high-throughput vector arithmetic, approximate nearest-neighbor (ANN) graph traversal, late-interaction token scoring (MaxSim), and asymmetric quantized distance calculations.

### Current Architectural Limitations
1. **Asymmetric Hardware Coupling in `spector-core`**:
   `nucleus/spector-core` contains both domain abstractions and low-level CPU SIMD vector implementations using the Panama Vector API (`jdk.incubator.vector`). This forces all downstream modules depending on `spector-core` to inherit incubator module requirements, violating the principle of a clean, stable core.
2. **Orphan GPU Acceleration Kernels**:
   `nucleus/spector-gpu` contains specialized kernels (`CudaHnswKernel`, `CudaSvasqKernel`, `CudaMaxSimKernel`) that are disconnected from the actual indexing classes (`AbstractHnswIndex`, `QuantizedHnswIndex`) and cognitive rerankers (`ColBERTReranker`).
3. **Scalar Candidate Evaluation in HNSW Traversal**:
   `AbstractHnswIndex.searchLayer()` evaluates unvisited neighbor candidates one-by-one in a scalar loop rather than dispatching batched candidate sets to SIMD or GPU.
4. **Module Misplacement & Provider Leak in `spector-index`**:
   `spector-index` was placed under `memory/` and inherited a dependency on `spector-provider-api` solely because `ColBERTReranker` combined text token encoding with MaxSim late-interaction scoring in the same class.

---

## 2. Decision Drivers

* **Dependency Inversion Principle (DIP)**: High-level indexing and memory modules must depend on pure abstractions (SPIs), not on CPU SIMD or GPU CUDA implementations.
* **Single Responsibility Principle (SRP)**:
  - `spector-core` owns contracts, math formulations, cognitive abstractions, and registry routing.
  - `spector-cpu` owns CPU SIMD vector execution (AVX-512, AVX2, ARM Neon) via Java 25 Panama Vector API.
  - `spector-gpu` owns GPU CUDA execution (PTX kernels, Panama FFM, VRAM manager).
  - `spector-index` owns graph topologies, disk paging, and ANN data structures (HNSW, IVF, BM25, SPLADE), residing purely in `nucleus/`.
  - `spector-memory` owns cognitive memory models, episodic/semantic/procedural partitions, and high-level reranker pipelines (`ColBERTReranker`, `MmrReranker`, `CognitiveReranker`).
* **Interface Segregation Principle (ISP)**: Separate compute capabilities into fine-grained SPI interfaces (`SimilarityKernel`, `HnswCandidateKernel`, `SvasqDistanceKernel`, `QuantizedDistanceKernel`, `MaxSimKernel`).
* **Strict Downward Layering**: $\text{Synapse} \longrightarrow \text{Memory} \longrightarrow \text{Nucleus}$. No module in `nucleus/` may depend on `memory/` or `synapse/`.
* **Zero-Exception Degradation**: Runtime failures or hardware absence on GPU must transparently fall back to CPU SIMD with zero latency penalty or user disruption.
* **Platform Independence**: Standard Java SE compilation for `spector-core` without mandatory incubator compiler flags.

---

## 3. Considered Options

### Option 1: Status Quo (Keep SIMD in `spector-core`, GPU in `spector-gpu`, Index in `memory/`)
* *Pros*: No new modules required.
* *Cons*: `spector-core` remains polluted with incubator vector code; GPU HNSW/SVASQ kernels remain orphans; non-symmetric architecture; `spector-index` incorrectly depends on `spector-provider-api`.

### Option 2: Merge `spector-index` into `spector-cpu`
* *Pros*: Reduces total module count by 1.
* *Cons*: Severe SRP violation. Conflates graph algorithms with CPU vector intrinsics. Makes HNSW index unable to run cleanly on GPU without depending on CPU SIMD module.

### Option 3 (Selected): Symmetric Nucleus HAL with Dedicated `spector-cpu`, `nucleus/spector-index`, and Cognitive Reranker Extraction
* *Pros*:
  - Completely pure `spector-core` with zero incubator dependencies.
  - Symmetric peer plugins for `spector-cpu` (priority 0) and `spector-gpu` (priority 100).
  - `nucleus/spector-index` becomes a 100% self-contained Nucleus foundation module with zero provider dependencies.
  - `ColBERTReranker` is split: `MaxSimKernel` hardware SPI stays in Nucleus, while the AI `TokenEmbeddingProvider` pipeline lives in `memory/spector-memory`.
  - Extensible to future accelerators (Apple Metal, Intel oneAPI/SYCL, WebGPU) without touching core or indexing.

---

## 4. Architectural Design

### 4.1 Module Layering Matrix

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                               SYNAPSE (Nervous System)                                 │
│                     (spector-synapse, spector-mcp, spector-spring)                     │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                               MEMORY (Cognitive Engine)                                │
│                   (spector-memory, spector-query, spector-ingestion)                   │
│         • Cognitive Rerankers: ColBERTReranker, MmrReranker, CognitiveReranker         │
│         • Partitions: Working, Episodic, Semantic, Procedural, Habituation             │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                              NUCLEUS (Foundation Layer)                                │
│                                                                                        │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│  │                               nucleus/spector-index                              │  │
│  │               (HnswIndex, DiskHnswIndex, QuantizedHnswIndex, BM25, SPLADE)        │  │
│  │                  • 100% Hardware-Agnostic & Zero Provider Dependencies           │  │
│  │                  • Batched Candidate Evaluation via Compute SPIs                 │  │
│  └────────────────────────────────────────┬─────────────────────────────────────────┘  │
│                                           │                                            │
│                                           ▼                                            │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│  │                               nucleus/spector-core                               │  │
│  │               • Pure SPIs: Similarity, HnswCandidate, Svasq, MaxSim              │  │
│  │               • Domain Enums & Mathematical Models                               │  │
│  │               • AcceleratorRegistry & Dynamic Fallback Dispatcher                │  │
│  │               • Zero Incubator / Zero Native Dependencies                        │  │
│  └────────────────────────────────────────┬─────────────────────────────────────────┘  │
│                                           │ (ServiceLoader SPI Discovery)              │
│                        ┌──────────────────┴──────────────────┐                         │
│                        ▼                                     ▼                         │
│  ┌───────────────────────────────────────────┐ ┌────────────────────────────────────┐  │
│  │            nucleus/spector-cpu            │ │        nucleus/spector-gpu         │  │
│  │ • Panama Vector API (AVX-512, AVX2, Neon) │ │ • Panama FFM + CUDA Driver & PTX   │  │
│  │ • CpuSimdAccelerator (Priority: 0)        │ │ • CudaComputeAccelerator (Prio:100)│  │
│  │ • CpuSimdSimilarityKernel                 │ │ • CudaSimilarityKernel            │  │
│  │ • CpuSimdCandidateKernel (HNSW)           │ │ • CudaCandidateKernel (HNSW)      │  │
│  │ • CpuSimdSvasqKernel (SVASQ)              │ │ • CudaSvasqKernel (SVASQ)          │  │
│  │ • CpuSimdMaxSimKernel (ColBERT MaxSim)    │ │ • CudaMaxSimKernel (ColBERT MaxSim)│  │
│  └───────────────────────────────────────────┘ └────────────────────────────────────┘  │
│                                                                                        │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│  │      nucleus/spector-storage   •   nucleus/spector-config   •   spector-commons      │  │
│  └──────────────────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Compute Kernel SPI Definitions (`spector-core`)

All compute kernels extend the `ComputeKernel` marker interface:

```java
package com.spectrayan.spector.core.spi;

public interface ComputeKernel {}
```

### 5.1 `SimilarityKernel` (Pairwise & Batch Dense Distance)
```java
public interface SimilarityKernel extends ComputeKernel {
    float cosineSimilarity(float[] a, float[] b);
    void cosineSimilarity(float[] query, float[][] vectors, float[] outScores);

    float dotProduct(float[] a, float[] b);
    void dotProduct(float[] query, float[][] vectors, float[] outScores);

    float euclideanDistance(float[] a, float[] b);
    void euclideanDistance(float[] query, float[][] vectors, float[] outDistances);
}
```

### 5.2 `HnswCandidateKernel` (Batch Neighbor Evaluation in Graph Traversal)
```java
public interface HnswCandidateKernel extends ComputeKernel {
    void evaluateCandidates(
        float[] query,
        float[] candidateVectorsFlat,
        int candidateCount,
        int dimensions,
        SimilarityFunction function,
        float[] outScores
    );
}
```

### 5.3 `SvasqDistanceKernel` (Asymmetric Quantized FWHT Distance)
```java
public interface SvasqDistanceKernel extends ComputeKernel {
    void computeDistances(
        float[] rotatedQuery,
        byte[] quantizedVectorsFlat,
        float[] codebookScales,
        int vectorCount,
        int dimensions,
        float[] outDistances
    );
}
```

### 5.4 `MaxSimKernel` (ColBERT Late-Interaction Token Scoring)
```java
public interface MaxSimKernel extends ComputeKernel {
    float maxSim(float[][] queryTokens, float[][] docTokens);
    void maxSimBatch(float[][] queryTokens, float[][][] docTokensBatch, float[] outScores);
}
```

---

## 6. Consequences

### Positive
* **Architectural Cleanliness**: `spector-core` is 100% standard Java 25.
* **Strict Downward Layering**: `nucleus/spector-index` only depends on `nucleus/*` modules; zero leakage of provider APIs into the foundation layer.
* **Orphan Kernels Resolved**: CUDA kernels for HNSW, SVASQ, and MaxSim are directly wired to index traversal and reranking pipelines.
* **MaxSim Acceleration**: ColBERT token late-interaction is accelerated on both CPU SIMD and CUDA GPU.
* **Safe Fallback**: Zero downtime or exceptions on machines without CUDA GPUs.

### Neutral / Trade-offs
* Total module count in `nucleus/` increases to accommodate `nucleus/spector-cpu` and `nucleus/spector-index`.
* Relocation of `ColBERTReranker` requires updating import statements in `spector-memory` recall pipelines.
