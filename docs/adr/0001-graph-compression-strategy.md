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

- **Status**: Accepted
- **Date**: 2026-07-30
- **Deciders**: Titan (Architecture), Nova (Product), Technical Lead
- **Module**: `spector-memory` → `com.spectrayan.spector.memory.graph`
- **Related Issues**: #70 (HyperEntityGraph, ✅ Closed), #71 (Kron-reduction coarsening, this ADR closes), #80 (tangent-space projection, research)
- **Related Branch**: `feat/kron-reduction-coarsening-issue-71` (to be shelved, not merged)
- **Supersedes / references**: `spector/docs/docs/labs/roadmap.md` → "Hypergraphs & Spectral Sparsification"

---

## Executive Summary

Spector needs a strategy to prevent **node and edge explosion** in the entity graph (10K memories → 50K+ entities, 2–3 binary edges per relationship, BFS fan-out at 32^hops). Three techniques were on the table: **hypergraphs** (representation compression), **spectral sparsification** (edge pruning), and **Kron-reduction coarsening** (node reduction, proposed in #71).

**Decision: pursue hypergraphs + spectral sparsification as the graph-compression path. Shelve Kron-reduction coarsening (#71) and do not merge the current branch.** Coarsening is not conceptually invalid, but as scoped and implemented it targets the wrong layer, duplicates the effective-resistance work already roadmapped as sparsification, contradicts Spector's own importance-ranked eviction, and produces an artifact (a reduced Laplacian) that no part of the recall path consumes.

---

## 1. Context

The three techniques address **different axes** of the same problem:

| Technique | Reduces | Lossy? | Status |
|---|---|---|---|
| **Hypergraphs** (`HyperEntityGraphMemory`) | Representation complexity — an n-ary relation becomes 1 hyperedge instead of C(n,2) binary edges | Lossless w.r.t. the relation | ✅ Graduated, wired into `PostIngestSync`, `ReflectionOrchestrator`, `PersistenceManager`, `CheckpointDaemon` |
| **Spectral sparsification** | Edge count — prunes edges while preserving the graph spectrum (effective resistance) | Bounded-error | 🔬 Roadmapped research; belongs in the `ReflectDaemon` consolidation cycle |
| **Kron-reduction coarsening** (#71) | Node count — eliminates low-degree nodes, folds them into hub clusters, preserves effective resistance between kept nodes | Lossy | Prototype branch under review |

The first two are already the documented strategy in `docs/labs/roadmap.md` ("Hypergraphs ✅ Graduated", "Spectral Sparsification 🔬 Research"). Kron reduction appears in **no** design document; it was introduced directly as issue #71.

---

## 2. Assessment of the #71 Branch

The review of `feat/kron-reduction-coarsening-issue-71` found the following. These are the concrete reasons the technique was evaluated and rejected *for now* — not merely style nits.

### 2.1 The mathematics does not match the claim
- The issue and the `GraphCoarsener` Javadoc claim exact Schur-complement Kron reduction — `L_reduced = L_CC − L_CF · L_FF⁻¹ · L_FC` — which "preserves **exact** effective resistances."
- The implementation instead performs a **per-node star-mesh redistribution over kept neighbours only**, dividing by each node's *total* degree. This equals the exact Schur complement **only when the eliminated set F is an independent set** — a condition that never holds at realistic keep ratios (e.g. 0.2).
- Consequences: **multi-hop paths through F are dropped** (`hub — f₁ — f₂ — hub` yields no connection), and **weight leaks** (dividing by total degree while redistributing only to kept neighbours under-weights surviving edges).
- The returned `maxEffectiveResistanceError` is a **hardcoded literal `0.0001`**, not a measured value. The unit test asserting `≤ 0.05` therefore verifies nothing about the core guarantee. **An unverified mathematical claim is presented as verified.**

### 2.2 Wrong layer
- #71 specified Kron reduction on the `HyperEntityGraph`. The branch coarsens the legacy binary `EntityGraphMemory` instead — i.e. it builds hierarchical summarization on the structure the hypergraph layer is intended to supersede.

### 2.3 Degenerate importance signal
- `EntityGraphMemory.coarsen()` passes `nodeWeights[e] = max(1, degree)`; the scorer then computes `score = degree × nodeWeight`, i.e. effectively **degree²**.
- It ignores `EdgeImportance`, salience, and recency — the very signals Spector maintains. Degree-based elimination discards **rare, low-degree entities**, which in episodic/semantic memory are often the most informative. This is the *opposite* of the importance-ranked eviction shipped in #68.

### 2.4 No consumer, not integrated
- `EntityGraphMemory.coarsen()` has **no caller**; `GraphHealthMetrics.recordCoarsening()` is **never invoked**. The output `CoarsenedGraph` (a reduced **Laplacian** in CSR) is not a form the recall path — which traverses adjacency / spreading activation — can use. It is effectively dead code.

### 2.5 Kernel/standards fit
- Respects licensing (BSL 1.1 header), SLF4J logging, `ReentrantLock`, and off-heap segment reads consistent with `EntityGraphMemory`.
- But violates the zero-allocation / no-boxing hot-path standard heavily (`List<Integer>`, `List<Float>`, `Integer[]`, boxed `Arrays.sort`, `mapToInt`), and builds a **dense `float[C][C]`** intermediate (100 MB on-heap at keepRatio 0.5 on 10K nodes) — reintroducing the explosion it aims to prevent.
- The `< 100 ms` wall-clock perf test is O(F·E) plus dense allocation, hardware-dependent, and will flake in CI. A performance claim of this kind requires JMH.

---

## 3. Decision

1. **Adopt hypergraphs + spectral sparsification** as the entity-graph compression strategy, per the existing roadmap.
   - Hypergraphs are graduated; the next investment is *exploiting* them (cluster-aware recall, hyperedge eviction/decay tuning), not adding a parallel scheme beside them.
   - Spectral sparsification is the coherent next spectral step: it is effective-resistance-based, prunes edges (the actual explosion vector), and lives inside the `ReflectDaemon` consolidation cycle that already exists. **One** effective-resistance implementation, in the pipeline designed to hold it.
2. **Shelve Kron-reduction coarsening (#71).** Do not merge `feat/kron-reduction-coarsening-issue-71`. Close #71 with a pointer to this ADR.

---

## 4. Consequences

**Positive**
- A single, coherent spectral effort (sparsification) instead of two uncoordinated effective-resistance implementations.
- Avoids shipping a lossy node-elimination pass that would discard rare/specific entities and contradict importance-ranked eviction (#68).
- Avoids a dead-end API and a dense-matrix allocation on the hot path.

**Negative / accepted trade-offs**
- Spector gains **no hierarchical / multi-resolution recall** capability from this decision. That is accepted: it is not a current product requirement.
- The math prototyping effort in the #71 branch is not carried forward (the star-mesh code is not reusable for a correct implementation).

**Neutral**
- Effective resistance as an invariant is not discarded — it is retained via sparsification, where it is tied to a concrete consolidation goal (edge pruning) rather than imported abstractly.

---

## 5. Reconsideration Criteria — when to revisit coarsening

Node coarsening is **not permanently rejected.** Re-open the question only if **all** of the following hold:

1. **Hierarchical / multi-resolution recall becomes a real product requirement** ("zoom out to hub clusters, then drill into members").
2. The design coarsens the **`HyperEntityGraphMemory`**, not the binary `EntityGraphMemory`.
3. Cluster selection is driven by **real importance** (salience, recency, `EdgeImportance`), never by degree alone.
4. There is a **concrete consumer in the recall path** before implementation begins.
5. The implementation either performs **true multi-node Schur elimination with a measured error bound**, or is honestly labelled *approximate* (no "exact effective resistance" claim), with a test that actually computes resistance error against a reference.

At that point it is a fresh design on the hypergraph layer, not a revival of this branch.

---

## 6. Close-out Comment for Issue #71 (draft)

> Post as **Titan** (architecture) after CEO sign-off; see `.agents/skills/github-account-management`.

```markdown
Closing #71 as **superseded** — see ADR-0001 (`spectrayan/RnD/adr-0001-graph-compression-strategy.md`) for the full rationale.

**Decision:** Spector's entity-graph compression path is **hypergraphs (✅ graduated) + spectral sparsification (roadmapped)**. Kron-reduction coarsening is being shelved, and the `feat/kron-reduction-coarsening-issue-71` branch will not be merged.

**Why (summary):**
1. **Wrong layer** — #71 asked for Kron reduction on the `HyperEntityGraph`; the branch coarsens the legacy binary `EntityGraphMemory`, the structure hypergraphs are meant to supersede.
2. **Math doesn't match the claim** — the implementation is a per-node star-mesh over kept neighbours, not the Schur complement. It's exact only when the eliminated set is independent (never true at keepRatio 0.2); it drops multi-hop paths through eliminated nodes and leaks edge weight. The reported `maxEffectiveResistanceError` is a hardcoded `0.0001`, so the "within 5%" acceptance criterion is never actually tested.
3. **Duplicates roadmapped work** — coarsening and the planned spectral sparsification are both effective-resistance-based. We want one spectral implementation, in the `ReflectDaemon` consolidation cycle, pruning edges (the real explosion vector).
4. **Contradicts our own eviction philosophy** — node scoring collapses to degree² and ignores salience/recency/EdgeImportance, so it discards exactly the rare, low-degree entities that #68's importance-ranked eviction protects.
5. **No consumer** — `EntityGraphMemory.coarsen()` and `GraphHealthMetrics.recordCoarsening()` are never called, and a reduced Laplacian isn't a form the adjacency/spreading-activation recall path can use.

**Not permanently rejected:** coarsening can return if hierarchical/multi-resolution recall becomes a product requirement — as a fresh design on the hypergraph layer, importance-driven, with a real consumer and either true Schur elimination or an honest "approximate" label. See §5 of the ADR for the exact reconsideration criteria.

Thanks to the contributor — the effective-resistance direction is preserved via spectral sparsification, which is where we'll invest next.
```
