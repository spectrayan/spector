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

## 1. Context

Spector's cognitive engine models associative relationships between extracted entities. Historically, entity relationships were represented using a binary adjacency graph (`EntityGraphMemory`). ADR-0001 established that n-ary hypergraphs (`HyperEntityGraphMemory`) represent multi-entity relationships without clique explosion ($C(n,2)$ edges). However, graduating hypergraphs to completely replace binary graphs required resolving entity identity ownership and single-entity memory linkages.

## 2. Problem Statement

A naive replacement of `EntityGraphMemory` with `HyperEntityGraphMemory` created critical data loss and architectural failure points:

1. **Identity ownership**: `HyperEntityGraphMemory` stored hyperedges and incidence lists, but lacked entity identity allocation (`addEntity`), name-to-ID indices (`entity-names.idx`), and entity type registries (`.treg`).
2. **Single-entity edge truncation**: Hyperedge construction required $\ge 2$ vertices. Memories mentioning only one entity were linked in `EntityGraphMemory`, but were rejected by hyperedge creation, causing single-entity associations to be silently dropped.
3. **Write-Ahead Log (WAL) desynchronization**: While `EntityGraphMemory` was bound to the WAL, `HyperEntityGraphMemory` was never bound, leaving hyperedges vulnerable to process crashes prior to periodic checkpoints.

## 3. Decision Drivers

- **Zero Data Loss**: Both n-ary relations and single-entity memory associations must be reliably preserved.
- **Unified Identity Plane**: A single, definitive entity identity registry must manage string names, dense integer IDs, and entity types.
- **Complete WAL Durability**: All graph modifications must be durably appended to the Write-Ahead Log.
- **Clean Deprecation**: Safely deprecate binary graph representations without breaking active downstream services (`ConsolidationService`, `ReflectionOrchestrator`).

## 4. Considered Options

### Option 1: Immediate Binary Graph Deletion

- **Description**: Drop `EntityGraphMemory` immediately and point all graph interfaces to `HyperEntityGraphMemory`.
- **Advantages**: Fast codebase cleanup.
- **Disadvantages**: Severe regression: loses entity naming indices, drops single-entity memories, and breaks WAL recovery.

### Option 2: Permanent Dual-Graph Architecture

- **Description**: Maintain both binary `EntityGraphMemory` and `HyperEntityGraphMemory` simultaneously in production.
- **Advantages**: Minimal refactoring of legacy consumers.
- **Disadvantages**: Doubles disk I/O and off-heap memory usage; synchronizing binary and hyperedge mutations introduces race conditions.

### Option 3: Phased Identity Extraction & Hypergraph Graduation (Selected)

- **Description**: Extract entity identity management (`EntityNameIndex`, `EntityTypeRegistry`) into a dedicated off-heap identity component. Permit unary hyperedges (1 vertex) for single-entity memories. Bind `HyperEntityGraphMemory` to the WAL dispatcher, and safely retire the legacy binary adjacency store.
- **Advantages**: Retains 100% entity and link fidelity, provides crash-consistent WAL durability, and achieves representation elegance.
- **Disadvantages**: Requires updating 23 production call sites across reflection and consolidation pipelines.

## 5. Decision Outcome

**Chosen Option**: Option 3 (Phased Identity Extraction & Hypergraph Graduation).

### Positive Consequences

- Hypergraph graduation achieves representation compression without losing single-entity associations.
- Crash durability guaranteed by binding hyperedge mutations to `MemoryWalRecovery`.
- Unary hyperedges provide uniform representation for both atomic mentions and complex n-ary relationships.

### Negative Consequences & Trade-offs

- Legacy binary graph methods (`decayAdjacencyWeights`, `compactAdjacency`) must be reimplemented as hyperedge weight updates.
- Historical binary graph files require automated migration or re-ingestion.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Fast Deletion** | Rapid code reduction | Data loss, broken entity names, crash vulnerabilities |
| **Option 2: Permanent Dual** | Low immediate effort | Double memory overhead, dual-write synchronization bugs |
| **Option 3: Phased Graduation** | Zero data loss, WAL-backed, clean architecture | Requires updating 23 call sites and migration tooling |

## 7. Implementation Plan

1. **Phase 1**: Enable unary hyperedges ($k=1$) in `HyperEntityGraphMemory` to capture single-entity memory references.
2. **Phase 2**: Extract `EntityRegistry` from `EntityGraphMemory` as an independent off-heap identity store.
3. **Phase 3**: Register `HyperEntityGraphMemory` with `MemoryWalRecovery` and `WalRecoveryDispatcher`.
4. **Phase 4**: Migrate `ReflectionOrchestrator` and `ConsolidationService` to hyperedge traversal APIs.
5. **Phase 5**: Mark `EntityGraphMemory` as `@Deprecated` and remove from default initialization.

## 8. Code Reference & Verification

- **Primary Module(s)**: `memory/spector-memory`
- **Key Packages**: `com.spectrayan.spector.memory.graph`, `com.spectrayan.spector.memory.wal`
- **Classes**: `HyperEntityGraphMemory.java`, `HyperEntityLayout.java`, `CognitiveGraphBuilder.java`, `MemoryWalRecovery.java`
- **Verification Tests**: `HyperEntityGraphMemoryTest.java`, `GraphWalRecoveryTest.java`
