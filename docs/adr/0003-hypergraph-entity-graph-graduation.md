# ADR-0003: Completing Hypergraph Entity-Graph Graduation

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-03 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

**Target repo**: `spectrayan/spector`, module `memory/spector-memory` (BSL-1.1 flagship)
**Supersedes decisions in**: `RnD/adr-hypergraph-graduation.md` (Migration Plan Phases 1–4 are refined here)
**Related**: ADR-0001 (graph-compression strategy), #431/#435 (kernel substrate), #417 (graph/Hebbian WAL never bound), #432/#443 (format-migration discipline)

---

## 1. Context and ground truth

The 2026-08-01 ADR accepted graduating `HyperEntityGraphMemory` as the sole entity-graph implementation and retiring the binary `EntityGraphMemory`. That ADR framed the work as "point `GraphExpansionStage` at the hypergraph, stop instantiating the binary graph, delete it." **That framing is incomplete and, if followed literally, loses data.** This ADR is the concrete completion design after reading the code.

### 1.1 What is actually true today (verified against source)

- `HyperEntityGraphMemory` is on the #435 kernel substrate (`AbstractGraphMemory<HyperEntityLayout>`, SMKM v2 header, `StampedLock` SWMR). It stores n-ary hyperedges (`[edgeId,type,weight,vertexCount,vertexOffset,memoryIdx,timestamp]`) + `(entityId, roleId)` vertex entries + rebuilt-on-load incidence lists. It is wired into recall via `GraphExpansionStage`.
- `EntityGraphMemory` (legacy `AbstractGraphMemory<EntityLayout>`, `entity.graph` / `FILE_ENTITY`, SMKM `EGMM` records + `entity-names.idx` sidecar + `EGPH` legacy) is **still instantiated** in `CognitiveGraphBuilder` (~L161–176), is **not** `@Deprecated`, and is referenced by **23 production files**.

### 1.2 The identity crux (why the ADR's "just delete it" is wrong)

`HyperEntityGraphMemory` **does not own entity identity.** Concretely:

| Capability | Owner today | HyperEntity has it? |
|---|---|---|
| Entity id allocation (`addEntity(name,type)` → dense `entityId = entityCount++`) | `EntityGraphMemory` | No — it consumes ids as vertices |
| Name→id index (`nameIndex()`, `findEntity()`, `entity-names.idx` sidecar) | `EntityGraphMemory` | No |
| Entity type (`entityType(id)`, `entityTypeRegistry()`) | `EntityGraphMemory` + `.treg` | No |
| Entity→memory adjacency (`memoriesForEntity`, `memoryRefAt/Count`, `linkEntityToMemory`) | `EntityGraphMemory` | Partially derivable, see below |
| n-ary relationships (hyperedges) | `HyperEntityGraphMemory` | Yes |

The facade proves the coupling. `CognitiveGraphFacade.topologyStats()`, `collectEntityEdges()`, `bfsEntityNeighbors()`, and `entityNamesForMemory()` all **pull the name index and type from `entityGraph`** and only the hyperedges from `hyperEntityGraph`:

```java
var nameIndex = entityGraph.nameIndex();            // identity from EntityGraph
String entityType = safeEntityType(entityId);       // entityGraph.entityType(id)
var hEdges = hyperEntityGraph.findHyperedgesForEntity(entityId);  // topology from Hyper
```

`GraphExpansionStage.expandEntity()` branches the same way: it resolves `entityGraph.findEntity(name)` and `entityGraph.fanFactor(id)`, then calls `hyperEntityGraph.collectMemories(id, hops)`.

### 1.3 The structural gap (single-entity memories)

`PostIngestSync.populateEntities()` only creates a hyperedge when a memory yields **≥ 2** entities (`addHyperedge` rejects `< 2` vertices). The entity→memory link for that memory is written to **`EntityGraphMemory.linkEntityToMemory`** unconditionally, but a hyperedge is created **only for the ≥2 case**. Therefore:

> A memory that mentions exactly one entity has its entity→memory adjacency **only** in `EntityGraphMemory`, never as a hyperedge. Deleting `EntityGraphMemory` without a replacement silently drops all single-entity adjacency **and** all name/type identity.

This is the reason the graduation cannot be a one-line pipeline swap. **Something must own identity + single-entity adjacency before the binary graph can go away.**

### 1.4 Secondary couplings found

- **`TemporalKnowledgeGraph`** is constructed with `entityGraph.relationTypeRegistry()` as its predicate registry (`CognitiveGraphBuilder`). Deleting the entity graph orphans TKG's predicate ids.
- **`ConsolidationService`** and **`ReflectionOrchestrator`** use binary-relation-only APIs that have **no hypergraph equivalent**: `addRelation`, `boostEdgeWeight`, `edges`, `traverse`, `decayEdges`, `decayAdjacencyWeights`, `compactAdjacency`, `mergeSimilarEntities`, `fanFactor`, plus identity walks (`entityCount`, `memoryRefCount`, `memoryRefAt`).
- **WAL (#417 confirmed here):** the factory binds `entityGraph.bindWal(wal)` and `hebbian(hgm).bindWal(wal)` — **HyperEntity is never bound.** `MemoryWalRecovery` registers `entityGraph.id()` but **not** `hyperEntityGraph.id()`, and `WalRecoveryDispatcher` has recovery cases only for `EntityGraphMemory` (`GRAPH_ADD_NODE`, `GRAPH_LINK_MEMORY`, `ADJ_ADD_EDGE`). So `addHyperedge`'s `if (wal != null && !bypassWal)` write path is **dead in production and never replayed.** Hyperedges today survive only via checkpoint/`save()`, not WAL.
- **Flags:** `SpectorMemoryBuilder.hyperEntityGraphEnabled = true` (default) gates hyper instantiation (`CognitiveGraphBuilder` ~L181). `useHypergraphRecall` referenced by the original ADR is **already absent** from production source (only lives in the ADR text / possibly bench) — treat as a no-op removal.

---

## 2. Decisions

### Decision 1 — Identity ownership: a dedicated kernel-substrate companion `EntityDirectory`

**Decision.** Do **not** absorb identity into `HyperEntityGraphMemory`. Introduce a small kernel-substrate companion, **`EntityDirectory`** (working name; `EntityRegistry` acceptable), that owns the three things the hypergraph lacks: (a) the **name↔id index**, (b) the **entity type id** per entity, and (c) the authoritative **entity→memory adjacency** (including single-entity memories). `HyperEntityGraphMemory` remains a pure hyperedge store over an externally-owned dense entity-id space.

**Rationale.** Absorbing name index + per-entity memory adjacency into `HyperEntityGraphMemory` would (1) couple identity lifecycle to hyperedge lifecycle — hyperedge eviction (`evictWeakestHyperedge`, `decayHyperedges`) must never evict an entity's identity or its single-entity adjacency; (2) bloat the hyperedge hot-path segments with variable-length name data, breaking the zero-alloc/off-heap contiguity the substrate is built for; and (3) force the hypergraph to violate its own ≥2-vertex invariant to represent single-entity links. A dedicated companion is single-responsibility, mirrors the existing separation (`TypeRegistryMemory`, `HebbianGraphMemory`, `TemporalChainMemory` are all peers wired at the builder), and cleanly captures the single-entity adjacency that hyperedges structurally cannot. It is also the smallest possible surface: `EntityDirectory` is essentially `EntityGraphMemory` **minus** the binary edge/adjacency-relation machinery (edges, `traverse`, `addRelation`, bridge scoring), keeping only identity + memory-links.

**Concrete shape.**

- **Kernel identity/layout:** `MemoryId.of("graph", "entity-directory")`, new `EntityDirectoryLayout` in `kernel/layout/` following the #435 pattern (record stride constants, `SUB_OFF_*`, `DATA_START = HEADER_BYTES + subheader`). Reuse the region-doubling entity→memory adjacency mechanics already proven in `EntityLayout`/`EntityGraphMemory` (the `ADJ_OFF_*` block, `ensureAdjSegmentCapacity`, `compactAdjacency`) — this is the well-tested part of the legacy graph.
- **Segments (substrate arena):** (1) entity node slab `[typeId:4][nameHash:8][adjOffset:4][adjCount:4][adjCapacity:4]` (drop the binary-edge fields `EDGE_START`/`DEGREE`); the kernel `segment()` = this slab. (2) region-doubling entity→memory adjacency segment `[memoryIdx:4][weight:4]`. Name index stays an on-heap `ConcurrentHashMap<String,Integer>` persisted as the `entity-names.idx` sidecar (unchanged format — reuse `EntityGraphSerializer`'s name-index reader/writer, extracted into the directory).
- **Type registries** (`entity-types.treg`, `relation-types.treg`) become **standalone** `TypeRegistryMemory` instances owned by `CognitiveGraphBuilder` (they already persist independently) and are shared by `EntityDirectory`, `HyperEntityGraphMemory` (relation-type ids on hyperedges), and `TemporalKnowledgeGraph` (predicate registry). This severs the TKG↔EntityGraph coupling.
- **Ingestion population** (`PostIngestSync.populateEntities`): `int eid = entityDirectory.intern(name, type)` (was `entityGraph.addEntity`), then `entityDirectory.linkEntityToMemory(eid, memoryIdx)` **always** (this is what preserves single-entity adjacency); then, for ≥2 entities, `hyperEntityGraph.addHyperedge(entityIds, roles, …)` **unchanged**. The entity-id space is now owned by `EntityDirectory` and consumed by `HyperEntity` exactly as it consumes `EntityGraph`'s ids today — a drop-in id provider swap.

`EntityDirectory` exposes the read API the facade/expansion already call: `nameIndex()`, `findEntity(name)`, `entityType(id)`, `memoriesForEntity(id)`, `memoryRefCount/At`, `entityCount()`, `fanFactor(id)` (derivable from adjacency degree), and `mergeSimilarEntities(maxEditDistance)` (identity-level dedup, moved verbatim from `EntityGraphMemory`).

### Decision 2 — On-disk format and migration

**Decision.** Two on-disk artifacts survive graduation, both already substrate-native SMKM containers:

1. `hypergraph.hyeg` (`FILE_HYPERGRAPH`, SMKM v2, layoutId `HYEG`) — unchanged.
2. **new** `entity-directory.edir` (`FILE_ENTITY_DIRECTORY`, new SMKM container) + its existing `entity-names.idx` sidecar + the `.treg` registries.

The legacy `entity.graph` (`FILE_ENTITY`; SMKM `EGMM` records or legacy `EGPH`/`EGMM-v2` migrated in-class) is **retired**. Its binary **edge/relation** segment is intentionally **not** migrated — those edges are (a) reflection-generated `RELATED_TO`/`CONTRADICTS` artifacts that decay, and (b) superseded by hyperedges. Only **identity + entity→memory adjacency + name index + type registries** are migrated.

**Migration mechanism: one-way CLI (per ADR Phase 3), not on-startup.** A standalone `EntityGraphMigrationCli` reads a legacy persistence dir and writes `entity-directory.edir` (+ sidecar) alongside the already-present `hypergraph.hyeg`:

```
legacy entity.graph (EGMM/EGPH)  ──►  extract {nameIndex, entityType(id), memoriesForEntity(id)}  ──►  entity-directory.edir + entity-names.idx
```

- **Rationale for CLI over startup:** matches the accepted ADR (pre-1.0 alpha, no production deployments, explicit over hot-path complexity). Startup auto-migration would add branchy detection to the boot path and risk silent partial migration.
- **Format discipline is mandatory (#432/#443 class):** (1) write a **golden-file test asserting the exact current bytes** of a fixture `entity.graph` (both `EGMM` SMKM and legacy `EGPH`) *before* touching any migration code; (2) migration is a **single in-class authority** (mirror `EntityGraphMemory.load`'s SMKM-open / `EGMM`-migrate / `EGPH`-migrate / absent-fresh / **present-but-unreadable→throw `SpectorGraphPersistenceException`** dispatch); (3) preserve the original as `<name>.bak.egmm`/`.bak.egph`; (4) assert `.bak == original bytes` + data fidelity (entity count, name-index round-trip, `memoriesForEntity` equality) in the migration test.
- **User's existing `entity.graph`:** on the graduated build, if `entity-directory.edir` is absent but `entity.graph` is present, the loader **fails loud** with a message directing the operator to run the CLI (never silently discards). During the transition (Phase 1), the directory is instead **derived in-memory** from the loaded `EntityGraphMemory` (see Phase 1) so no user action is required until the binary graph is excised.

### Decision 3 — Pipeline cutover (make recall/ingest/expansion hyper-only)

**Decision.** Cut the identity dependency to `EntityDirectory` first, then make traversal hyper-only. Concrete changes, in dependency order:

1. **`CognitiveGraphFacade`** — replace the `EntityGraphMemory entityGraph` field with `EntityDirectory entityDirectory`. Repoint `nameIndex()`, `entityType()`, `memoriesForEntity()`, `entityNamesForMemory()`, `topologyStats()`, `collectEntityEdges()`, `bfsEntityNeighbors()` to the directory for identity and to `hyperEntityGraph` for topology (they already read hyperedges). `graphStats()` entity node/edge counts come from `entityDirectory.entityCount()` + `hyperEntityGraph.totalHyperedges()`.
2. **`GraphExpansionStage`** — replace `entityGraph.findEntity` → `entityDirectory.findEntity`, `entityGraph.fanFactor` → `entityDirectory.fanFactor`, and drop the `else entityGraph.collectMemories(...)` binary fallback so traversal is **unconditionally** `hyperEntityGraph.collectMemories(...)`. Update the `hasSubsystems` gate to test `entityDirectory` (identity availability) rather than `entityGraph`.
3. **`CognitiveIngestionTarget` / `PostIngestSync`** — swap the id provider (`entityDirectory.intern` + `linkEntityToMemory`) and keep the hyperedge write. Remove the `EntityGraphMemory entityGraph` constructor param from both once step 1–2 land. **This stops the dual-write** (today identity → EntityGraph, topology → Hyper; after, identity → Directory, topology → Hyper — no binary edges written at ingest).
4. **`CognitiveGraphBuilder`** — construct `EntityDirectory` (load `edir` / derive-from-legacy / fresh) and standalone type registries; stop passing `entityGraph` into the facade, pipeline, reflection, checkpoint, persistence. Remove the `EntityGraphMemory` instantiation **in Phase 4** (kept in Phase 1–2 for derive-on-load).
5. **Flags** — delete `SpectorMemoryBuilder.hyperEntityGraphEnabled` (hyper becomes unconditional when entity extraction is enabled) and remove any residual `useHypergraphRecall` reference (already gone from prod; sweep bench/tests).

**Deprecate vs delete, safe order:** In Phase 1–2, mark `EntityGraphMemory`, `EntityGraphSerializer`, `EntityLayout`, `CognitiveGraphFacade.entityGraph()`, `SpectorMemoryAdmin.entityGraph()`, `DefaultSpectorMemory.entityGraph()`, `SpectorMemoryBuilder.hyperEntityGraphEnabled()` as `@Deprecated(forRemoval=true)` and stop reading from them. Delete only in Phase 4 after the CLI exists and Docker data is migrated.

### Decision 4 — Excision scope and gate

**Decision.** The final phase deletes, in `memory/spector-memory`:

- `graph/EntityGraphMemory.java`, `graph/EntityGraphSerializer.java` (its name-index reader/writer is first **extracted** into `EntityDirectory`/its serializer, then the file is removed).
- `kernel/layout/EntityLayout.java` and its references in `kernel/codec/Codecs.java` (the `EGMM` codec entry) — note `EntityLayout`'s adjacency mechanics are **first copied** into `EntityDirectoryLayout`, not deleted blind.
- `StorageLayout.FILE_ENTITY` + `entityGraphRuntime()` + the deprecated `entityGraph(partitionDir)` resolver. Retain a single `LEGACY_FILE_ENTITY = "entity.graph"` constant **scoped to the CLI** for migration reads.
- Deprecated accessors: `CognitiveGraphFacade.entityGraph()`, `SpectorMemoryAdmin.entityGraph()`, `DefaultSpectorMemory.entityGraph()`, `SpectorMemoryBuilder.hyperEntityGraphEnabled()`.
- WAL recovery cases in `WalRecoveryDispatcher` that `instanceof EntityGraphMemory` — repointed to `EntityDirectory` (Decision 5), not deleted.

**Gate (all must hold):** Phase 1–3 merged; the migration CLI exists with golden-file + fidelity tests green; local Docker `entity.graph` data migrated to `entity-directory.edir` and verified (entity counts, name round-trip, spot-checked `collectMemories`); full `mvn test -pl memory/spector-memory -am` green; and a benchmark re-run on `entity-dense-baseline` + the §5 robustness dataset shows **no nDCG regression** vs the pre-graduation baseline.

### Decision 5 — Concurrency and WAL

**Decision (concurrency).** `EntityDirectory` extends the same kernel substrate and uses the substrate `StampedLock` in **SWMR** mode: all mutators (`intern`, `linkEntityToMemory`, `ensureAdjSegmentCapacity`, `compactAdjacency`, `mergeSimilarEntities`) take the write lock; adjacency readers (`memoriesForEntity`, `memoryRefAt/Count`, `fanFactor`) take a **validated read lock, not optimistic**, because `compactAdjacency`/`ensureAdjSegmentCapacity` **reassign the adjacency segment field** (the documented #435 hazard). Use the public-wrapper + `*Locked` core split (as `EntityGraphMemory` already does) since `StampedLock` is non-reentrant. `HyperEntityGraphMemory`'s existing locking is sufficient for the added write load: ingest of a ≥2-entity memory is one `addHyperedge` write-lock acquisition; `incidenceHeap` is mutated under the write lock and iterated under a validated read lock — no change needed. The new identity hot path (`intern`/`link` on every entity) lands on `EntityDirectory`, not `HyperEntity`, keeping the two write locks independent.

**Decision (WAL) — closes #417 for the graduated path.** WAL binding must change:

- **`EntityDirectory` binds WAL** and emits the identity events currently emitted by `EntityGraphMemory`: `GRAPH_ADD_NODE` (name intern + type) and `GRAPH_LINK_MEMORY` (entity→memory). `WalRecoveryDispatcher`'s `GRAPH_ADD_NODE`/`GRAPH_LINK_MEMORY` cases are **repointed** from `instanceof EntityGraphMemory` to `instanceof EntityDirectory`; `MemoryWalRecovery` registers `entityDirectory.id()` in its recovery map. The `ADJ_ADD_EDGE` case (binary relations) is dropped for the directory — no binary edges exist anymore.
- **`HyperEntityGraphMemory` must be bound and recovered.** Today it is neither (the write path is dead). Because hyperedges are now the **sole** entity-relationship store and are **not** derivable from anything else, they need durability between checkpoints. Add a `HYPEREDGE_ADD` WAL record type, emit it from `addHyperedge` (the buffer is already assembled), add a `WalRecoveryDispatcher` case (`instanceof HyperEntityGraphMemory` → replay `addHyperedge`), register `hyperEntityGraph.id()` in `MemoryWalRecovery`, and add `hyperEntityGraph.bindWal(wal)` in `SpectorMemoryFactory`. **Alternative considered:** rely solely on checkpoint/`save()` (no WAL) — rejected, because a crash between checkpoints would lose all hyperedges written since the last checkpoint, which is unacceptable now that they carry the primary relational signal. This is a distinct, testable change and should be its own issue (it also finally makes #417's intent real for the graph layer).

### Decision 6 — Phasing, prerequisites, and risks

Four independently-shippable phases. **Phase 1 is a hard prerequisite** for everything else: nothing can go hyper-only until identity is absorbed.

| Phase | Scope | Prereq | Risk | Ship gate |
|---|---|---|---|---|
| **P1 — Identity absorption** | Introduce `EntityDirectory` + `EntityDirectoryLayout`; standalone type registries; populate the directory **alongside** `EntityGraphMemory` at ingest; on load, **derive** the directory from the loaded `EntityGraphMemory` when `edir` is absent; repoint `CognitiveGraphFacade` + `GraphExpansionStage` identity reads to the directory. `EntityGraphMemory` still instantiated (for derive + reflection). | none | **Low–Med.** Behavior-preserving; directory is a read-through mirror. Risk = deriving adjacency correctly for single-entity memories. | Tests green; facade/expansion produce byte-identical neighborhoods vs baseline on a fixture corpus. |
| **P2 — Pipeline cutover** | Stop dual-writing (id provider → directory only, no binary edges at ingest); make traversal unconditionally hyper; remove `hyperEntityGraphEnabled` + `useHypergraphRecall`; deprecate binary accessors; port `EntityDirectory` WAL + HyperEntity WAL (Decision 5). | P1 | **Med.** Recall path changes; WAL recovery repointing must not drop identity on restart. | Full suite + WAL crash-recovery test (kill mid-ingest, replay, assert identity + hyperedges intact); benchmark no-regression. |
| **P3 — CLI migration** | `EntityGraphMigrationCli` (`entity.graph` → `edir` + sidecar); golden-file + fidelity tests; graduated loader **fails loud** if `entity.graph` present without `edir`. | P2 | **Med.** Format-touching → #432 discipline mandatory. | Golden-file `.bak==original`; local Docker data migrated + verified. |
| **P4 — Binary excision** | Delete `EntityGraphMemory`/`EntityGraphSerializer`/`EntityLayout`/`FILE_ENTITY`/deprecated accessors; drop derive-on-load; remove `EntityGraphMemory` instantiation from `CognitiveGraphBuilder`; port/retire `ConsolidationService` + `ReflectionOrchestrator` binary ops (see below). | P1–P3 + Docker migrated + no nDCG regression | **Med–High.** Largest blast radius; reflection semantics change. | Gate conditions in Decision 4. |

**Separate follow-up issues (do not inline into the graduation):**

1. **Reflection/consolidation binary-relation port.** `ConsolidationService` (`addRelation("CONTRADICTS")`) and `ReflectionOrchestrator` (`boostEdgeWeight`, `addRelation("RELATED_TO")`, `decayEdges`, `decayAdjacencyWeights`, `compactAdjacency`, `mergeSimilarEntities`, `traverse`) must be re-expressed. Ruling: reflection-generated entity-entity relations become **2-vertex hyperedges** with a relation-type id (`HyperEdge.type` already exists); `mergeSimilarEntities` + `fanFactor` move to `EntityDirectory`; adjacency decay/compaction operate on the directory's memory-adjacency segment. **This must land in P4 (or immediately before) — it is the blocker for deleting `EntityGraphMemory`, not an optional cleanup.**
2. **HyperEntity WAL durability** (Decision 5, second half) — can ship in P2 but is independently testable and traceable to #417.

**Behavior / recall-quality risks to call out explicitly:**

- **Directed binary edges → undirected co-occurrence hyperedges.** `boostEdgeWeight(eA,eB)` / `addRelation(from,to,type)` are **directional** and typed; a 2-vertex hyperedge with SUBJECT/CONTEXT roles is a weaker encoding of direction. Reflection's `CONTRADICTS`/`RELATED_TO` semantics may shift. **Mitigation:** carry the relation-type id on the hyperedge and encode direction via vertex roles (SUBJECT vs OBJECT); validate on the reflection benchmark before P4.
- **`fanFactor` fidelity.** Expansion scoring multiplies by `entityGraph.fanFactor(id)`. The directory must reproduce it exactly (degree-derived) or expansion scores drift. **Mitigation:** unit-test `fanFactor` equality directory-vs-legacy on a fixture.
- **Single-entity adjacency during derive-on-load (P1).** If the derive step mis-handles the legacy adjacency region-doubling layout, single-entity links vanish. **Mitigation:** the P1 ship gate (byte-identical neighborhoods) catches this.
- **`useHypergraphRecall` assumption.** The original ADR lists it as a removal; it is **not present in production source**. Confirm it is only in bench/docs before claiming its removal as done.

---

## 3. Alternatives considered

- **(A) Absorb identity directly into `HyperEntityGraphMemory`** (ADR's implied path). Rejected — see Decision 1: couples identity to hyperedge eviction, bloats hot-path segments, and cannot represent single-entity adjacency without breaking the ≥2-vertex invariant.
- **(B) Keep `EntityGraphMemory` purely as an identity store, delete only its edges.** Rejected — leaves a misnamed, mostly-dead class (`edges`/`traverse`/bridge-scoring) in the flagship module; `EntityDirectory` is the honest, minimal surface.
- **(C) Derive entity→memory adjacency entirely from hyperedges (no directory adjacency segment).** Rejected — single-entity memories have no hyperedge, so their adjacency would be unrecoverable.
- **(D) On-startup auto-migration.** Rejected per the accepted ADR (hot-path complexity; CLI is explicit).

---

## 4. Consequences

**Positive.** Clean single-responsibility split (identity vs topology); the flagship keeps zero-alloc/off-heap hot paths intact; TKG decoupled from the entity graph; WAL durability for hyperedges finally real (#417); ~55% graph-atom reduction and bounded candidate generation realized as the ADR intended.

**Negative / cost.** A new `EntityDirectory` + layout + serializer + WAL record type + recovery case + CLI is more work than the ADR's "delete a class." Reflection/consolidation binary ops require a genuine port (separate issue). A format migration with full #432 golden-file discipline is required.

**Net.** The graduation is sound but was under-scoped; `EntityDirectory` is the missing piece that makes "delete `EntityGraphMemory`" safe. Phase 1 (identity absorption) is the prerequisite that the original ADR omitted.

---

## 5. Verified during analysis (was open, now confirmed)

- **`EntityLayout` adjacency offsets to copy into `EntityDirectoryLayout`** (confirmed against `kernel/layout/EntityLayout.java`): `ENTITY_NODE_BYTES=64`, `ENT_OFF_ADJ_OFFSET=16`, `ENT_OFF_ADJ_COUNT=20`, `ENT_OFF_ADJ_CAPACITY=24`, `ENT_OFF_DEGREE=36`, `ENT_OFF_EDGE_START=40`; adjacency entry `ADJ_ENTRY_BYTES=8` (`ADJ_OFF_MEM_IDX=0`, `ADJ_OFF_WEIGHT=4`). The directory node keeps `typeId`/`nameHash`/`adjOffset`/`adjCount`/`adjCapacity` and drops `ENT_OFF_DEGREE`/`ENT_OFF_EDGE_START` (binary-edge fields). Keeping the 64B stride simplifies migration byte-copy.
- **`useHypergraphRecall`** exists **only** in a test display-name string (`HypergraphRecallSpikeTest.java`), not in any production or `bench/` source. Its "removal" is a no-op beyond deleting/renaming that test assertion.
- **`SpectorMemoryAdmin.entityGraph()` IS reached by the benchmark harness** — `bench/spector-bench`: `BenchmarkSetup.loadEntityGraph(...)`, `GraphSnapshotCollector` (`EntityGraphMemory eg = memory.admin().entityGraph()`), and `CognitiveBenchmarkHarness`. **Excising that accessor breaks the bench harness**, which loads `dataset.entityRelations()` via the binary `addRelation` API. This must be sequenced: the benchmark loader needs an `EntityDirectory`-based path (identity) plus hyperedge construction (topology) before the accessor is removed in P4. No `synapse`/`mcp`/`cortex` consumer reaches it.

## 6. Still to confirm at implementation time

- Whether the bench `entityRelations()` loader should build hyperedges directly (recommended) or reuse the reflection binary→hyperedge port from follow-up issue #1.
- Downstream API-break sequencing for the deprecated accessors across `spector` → `spector-enterprise` → `spector-spring` (dependency order) if any of those repos pin to `SpectorMemoryAdmin`.
