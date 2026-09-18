# ADR-0002: Multi-Partition Recall Fan-Out & Frozen Retention

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-07-31 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

In `DISK` mode, Spector cognitive memory stores data in partitioned off-heap memory-mapped directories (`partitions/NNN_epoch/{episodic,semantic,procedural}.mem` + `text.dat`). When an active partition store reaches its configured entry capacity, a partition roll is triggered via `PartitionManager.rollPartition()`. A code audit of the partition lifecycle revealed that recall scans were restricted strictly to the single active partition, ignoring frozen historic partitions.

## 2. Problem Statement

Prior to this decision, multi-partition operation suffered from four core architectural defects:

1. **Arena/mmap leak & router staleness**: `PartitionManager.rollPartition()` constructed fresh tier stores in the new partition directory and swapped the active router, but old frozen stores lost reachable references without being properly closed or registered.
2. **Restart darkness**: `PartitionManager.discoverOrCreatePartition` returned only the newest partition directory, failing to map older partitions upon node restart.
3. **Reverse-key collision**: `IndexRecordMemory.reverseKey` was composed of `(type.ordinal() << 48) | offset` without a partition dimension. Because record offsets reset to 0 in each new partition, identical keys collided in the reverse index.
4. **Direct-resolve active-only**: Direct record retrieval operations (`inspect`, `forget`, `browse`, `reinforce`) resolved only against the active router, unable to access records in frozen partitions.

## 3. Decision Drivers

- **Complete Historical Recall**: Queries must seamlessly fan out across both active and frozen partitions.
- **Resource Safety**: File descriptors and off-heap memory segments for frozen partitions must be bounded and explicitly managed without leaks.
- **Collision-Free Addressing**: Global record identifiers must uniquely identify both partition epoch and slab offset.
- **Zero-GC Hot Path**: Partition fan-out must leverage virtual threads and Panama off-heap memory without allocating temporary collections on query hot paths.

## 4. Considered Options

### Option 1: Monolithic Partition Re-Indexing
- **Description**: Merge frozen partition records into a single global index and rebuild HNSW graphs on roll.
- **Advantages**: Simple single-point query interface.
- **Disadvantages**: Prohibitive write amplification and compaction pauses during rolls; breaks append-only storage immutability.

### Option 2: Active-Only Search with Background Compaction
- **Description**: Keep only the active partition queryable; asynchronously compact frozen partitions into cold archives.
- **Advantages**: Minimal changes to active query routing.
- **Disadvantages**: Destroys episodic continuity; memories in recently frozen partitions become invisible until cold compaction completes.

### Option 3: Two-Tier Multi-Partition Recall Routing (Selected)
- **Description**: Maintain an active partition for writes and concurrent reads, and a registry of read-only frozen partitions with distinct partition IDs. Multi-partition recall fans out across active and frozen stores using virtual threads, resolving reverse-key collisions by embedding the partition index into global memory addresses.
- **Advantages**: Instant partition rolls (zero compaction pause), complete historical recall visibility, bounded descriptor pools, and deterministic off-heap memory management.
- **Disadvantages**: Requires parallel fan-out aggregation across multiple memory-mapped slabs.

## 5. Decision Outcome

**Chosen Option**: Option 3 (Two-Tier Multi-Partition Recall Routing).

### Positive Consequences
- Bounded partition files with zero data loss across rolls.
- Fully parallel recall scans across active and frozen stores.
- Global memory addressing incorporates partition identifiers, eliminating reverse-key collisions.
- Explicit resource tracking guarantees that frozen partition arenas are closed upon namespace unload.

### Negative Consequences & Trade-offs
- Query fan-out requires score normalization and top-$K$ merging across multiple partition results.
- File descriptor usage scales with partition count, requiring file descriptor pooling and LRU partition mapping under high tenant density.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Re-indexing** | Single index lookup | Expensive compaction pauses, write amplification |
| **Option 2: Active-Only** | Low query complexity | Cold historical darkness, broken temporal continuity |
| **Option 3: Two-Tier Routing** | Zero roll pause, full visibility, clean resource bounds | Merging overhead across partition slabs |

## 7. Implementation Plan

1. **Phase 1**: Embed partition identifiers into global memory addresses and `MemoryLocation` representations.
2. **Phase 2**: Refactor `PartitionManager` to maintain a concurrent registry of active and frozen partition stores.
3. **Phase 3**: Update `RecallPipeline` and `CognitiveMemoryRouter` to fan out scan tasks across all registered partitions.
4. **Phase 4**: Implement frozen partition discovery on node startup in `discoverOrCreatePartition`.

## 8. Code Reference & Verification

- **Primary Module(s)**: `memory/spector-memory`
- **Key Packages**: `com.spectrayan.spector.memory.store`, `com.spectrayan.spector.memory.router`
- **Classes**: `PartitionManager.java`, `CognitiveMemoryRouter.java`, `RecallPipeline.java`, `MemoryLocation.java`
- **Verification Tests**: `PartitionRollIntegrationTest.java`, `MultiPartitionRecallTest.java`
