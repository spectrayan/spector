# ADR-0001: Graph Compression Strategy for Entity Graph

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-07-30 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

Spector's cognitive memory architecture maintains an entity-relationship graph to support spreading activation, associative recall, and episodic-semantic grounding. As memory engrams accumulate (e.g. 10,000+ memories yielding 50,000+ entities with 2–3 binary edges per relationship), breadth-first search fan-out across binary edges risks an exponential explosion ($32^{\text{hops}}$) in traversed nodes and memory footprint.

## 2. Problem Statement

Without an explicit graph compression strategy, entity-graph growth degrades query latency, saturates off-heap buffers, and increases garbage collection overhead. We must establish a bounded-error, computationally efficient strategy to control graph density while preserving cognitive recall fidelity and important associative pathways.

## 3. Decision Drivers

- **Bounded Representation Complexity**: Prevent exponential edge growth for high-order n-ary relations.
- **Zero-Allocation Hot Path**: Hot recall traversals must avoid intermediate on-heap allocations (no boxed integers, dense float matrices, or temporary collections).
- **Alignment with Eviction Invariants**: Graph reduction must reinforce, rather than contradict, importance-ranked memory eviction (#68).
- **Algorithmic Correctness**: Mathematical claims regarding spectral properties or effective resistances must be formally verified with measurable error bounds.

## 4. Considered Options

### Option 1: Hypergraphs (`HyperEntityGraphMemory`)

- **Description**: Model n-ary relations directly as hyperedges rather than cliques of binary edges ($C(n,2)$ combinations).
- **Advantages**: Representation complexity reduction is exact and lossless with respect to the underlying relation; already graduated and integrated into `PostIngestSync`, `ReflectionOrchestrator`, and `PersistenceManager`.
- **Disadvantages**: Does not prune redundant binary edges between independent entities.

### Option 2: Spectral Sparsification

- **Description**: Prune edges while preserving the graph Laplacian spectrum and effective resistances within a bounded error.
- **Advantages**: Directly targets edge count (the primary explosion vector) and integrates naturally into the asynchronous `ReflectDaemon` consolidation cycle.
- **Disadvantages**: Requires periodic background spectral analysis.

### Option 3: Kron-Reduction Coarsening (Issue #71)

- **Description**: Eliminate low-degree nodes by folding them into hub clusters via Schur-complement reduction.
- **Advantages**: Reduces total node count.
- **Disadvantages**: Evaluated prototype in `feat/kron-reduction-coarsening-issue-71` was lossy, operated on legacy binary graphs instead of hypergraphs, dropped multi-hop paths, leaked edge weight, and produced a reduced Laplacian that no part of the recall path consumes.

## 5. Decision Outcome

**Chosen Option**: Adopt **Hypergraphs + Spectral Sparsification** as the canonical entity-graph compression path. Shelve Kron-reduction coarsening (#71) and reject the prototype branch.

### Positive Consequences

- Unifies graph compression under a single, coherent spectral strategy (sparsification during sleep reflection).
- Protects rare, informative entities from arbitrary degree-based eviction.
- Avoids shipping dead-end code or allocating 100 MB dense matrices on recall paths.

### Negative Consequences & Trade-offs

- Spector does not gain hierarchical multi-resolution recall ("zoom out to hub clusters") in this release. That capability is deferred until explicitly required by product specifications.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Hypergraphs** | Lossless relation compression, production-ready | Does not prune binary edges |
| **Option 2: Spectral Sparsification** | Prunes edges (the real explosion vector), preserves effective resistance | Requires background reflection pass |
| **Option 3: Kron Coarsening** | Reduces node count | Targets wrong layer, unverified math, no recall consumer |

## 7. Implementation Plan

1. **Phase 1 (Completed)**: Graduate `HyperEntityGraphMemory` into core ingestion and persistence pipelines.
2. **Phase 2 (Roadmapped)**: Implement effective-resistance-based spectral sparsification inside `ReflectDaemon`.
3. **Phase 3**: Close issue #71 with reference to this architectural rationale. Reconsideration criteria established: reopen coarsening only if hierarchical recall becomes a formal requirement and coarsening is applied directly to hypergraphs.

## 8. Code Reference & Verification

- **Primary Module(s)**: `memory/spector-memory`
- **Key Packages**: `com.spectrayan.spector.memory.graph`
- **Classes**: `HyperEntityGraphMemory.java`, `EntityGraphMemory.java`, `ReflectDaemon.java`
- **Verification Tests**: `HyperEntityGraphMemoryTest.java`
