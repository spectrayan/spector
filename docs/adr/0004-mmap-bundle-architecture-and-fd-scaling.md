# ADR-0004: Mmap Bundle Architecture & File Descriptor Scaling

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-04 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

Spector memory partitions store multiple off-heap data regions: episodic records, semantic vectors, procedural memories, forward and reverse index slots, text bodies, and cognitive graph topologies. In early versions, each region was allocated as an independent file and mapped via separate Panama `MemorySegment` instances. As partition counts and tenant namespaces scaled, operating system file descriptor limits (`nofile`) and virtual memory area (VMA) thresholds (`vm.max_map_count`) became critical system bottlenecks.

## 2. Problem Statement

Spreading a single cognitive partition across 8–12 distinct disk files created severe operational failure modes:
1. **File descriptor exhaustion**: With 1,000 active namespaces and 5 partitions each, the engine consumed > 50,000 concurrent file descriptors.
2. **Crash inconsistency across split files**: A system crash during a partition append could flush the index file while the record file was still buffered in kernel page caches, causing desynchronization upon restart.
3. **VMA fragmentation**: Hundreds of thousands of small mmap areas degraded TLB efficiency and exceeded Linux kernel `max_map_count` limits.

## 3. Decision Drivers

- **File Descriptor Efficiency**: Minimize open file handles per partition to support high-density multi-tenancy.
- **Atomic Persistence & Durability**: Partition storage must support crash-consistent flushes without torn states across files.
- **Zero-Copy Sub-Segmenting**: Slicing sub-regions from a single mmap bundle must have zero runtime CPU or garbage collection cost using Java Panama Foreign Function & Memory (FFM) APIs.
- **Predictable Disk Pre-Allocation**: Contiguous virtual memory layouts preventing on-disk fragmentation.

## 4. Considered Options

### Option 1: Multi-File Directory Layout with File Pooling
- **Description**: Retain individual files per tier and implement an LRU cache of open file descriptors.
- **Advantages**: Simple isolation between stores; individual file sizes remain small.
- **Disadvantages**: Severe concurrency bottlenecks on LRU eviction; frequent `mmap`/`munmap` system calls on query paths; does not resolve cross-file crash inconsistency.

### Option 2: Embedded Relational / LSM-Tree Engine (SQLite/RocksDB)
- **Description**: Embed an existing storage engine to manage binary blobs.
- **Advantages**: Mature file handle and crash consistency management.
- **Disadvantages**: Introduces heavy foreign JNI boundaries, on-heap serialization overhead, and destroys Spector's sub-microsecond SIMD direct memory access capabilities.

### Option 3: Unified Single-File Mmap Bundle Architecture (Selected)
- **Description**: Consolidate all partition regions into a single contiguous bundle file (`bundle.mem`). A single file descriptor is opened and mapped into a root `MemorySegment`, which is sliced into typed region arenas (`HEADER`, `INDEX`, `EPISODIC`, `SEMANTIC`, `GRAPH`, `TEXT`) using fixed, aligned byte offsets.
- **Advantages**: Reduces file descriptors per partition from 8 to 1. Single atomic `msync` flush for crash consistency. Zero-copy sub-slicing via `MemorySegment.asSlice()`.
- **Disadvantages**: Requires pre-allocating or expanding contiguous file spans with careful segment alignment.

## 5. Decision Outcome

**Chosen Option**: Option 3 (Unified Single-File Mmap Bundle Architecture).

### Positive Consequences
- Reduces file descriptor consumption by 87.5%, enabling 10x higher namespace density on standard Linux kernels.
- Eliminates cross-file desynchronization during crashes.
- Sub-segment slicing via Panama FFM provides zero-overhead, type-safe access to individual cognitive stores.

### Negative Consequences & Trade-offs
- Bundle file expansion requires managing spare capacity and handling sparse allocation headers.
- Corrupted file headers affect the entire partition bundle rather than an isolated tier file.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Multi-File Pooling** | Modular file isolation | Concurrency lock contention on pool, crash desync |
| **Option 2: Embedded LSM** | Out-of-the-box durability | High JNI latency, breaks Panama SIMD direct memory |
| **Option 3: Unified Bundle** | 1 FD per partition, atomic sync, zero-copy Panama slices | Pre-allocation complexity, single file scope |

## 7. Implementation Plan

1. **Phase 1**: Define `BundleHeader` layout containing magic bytes, version, region offsets, and capacity limits.
2. **Phase 2**: Implement `MmapBundleStore` using Panama `Arena` and `MemorySegment.asSlice()` for region isolation.
3. **Phase 3**: Migrate `PartitionManager` to open and close unified bundle files.
4. **Phase 4**: Implement offline migration tooling to convert legacy multi-file partitions into unified bundles.

## 8. Code Reference & Verification

- **Primary Module(s)**: `memory/spector-kernel`
- **Key Packages**: `com.spectrayan.spector.kernel.bundle`, `com.spectrayan.spector.kernel.layout`
- **Classes**: `BundleLayout.java`, `MmapBundleStore.java`, `MemorySegmentUtil.java`
- **Verification Tests**: `BundleLayoutTest.java`, `MmapBundleStoreTest.java`
