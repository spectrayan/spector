# ADR-0004: V4 Mmap Bundle Architecture & File Descriptor Scaling

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

> **ADR Number**: ADR-0004
> **Authors**: Technical Lead · Architecture Working Group (Systems Architecture)
> **Date**: 2026-08-04
> **Status**: Proposed — Awaiting Final Approval
> **Replaces**: —
> **Issue**: TBD (Standalone issue, per CEO decision)

---

## Context

Spector Memory V3 layout creates **13 mmap files per namespace** in `runtime/` plus **4 files per colocated partition** in `partitions/`. Every file holds an open `FileChannel` for its entire lifetime (`AbstractMemory.java:186-197`), consuming both a **file descriptor** and a **Virtual Memory Area (VMA)** simultaneously.

At the CEO-mandated target of **10,000 concurrent users**, this architecture will **catastrophically fail** due to kernel resource exhaustion.

### CEO Decisions (2026-08-04)

| Question | Decision |
|:---------|:---------|
| Priority vs. epic #431 | **Standalone issue** |
| Target scale | **10,000 concurrent users** |
| Schema version V4 | **Approved** |
| Backward compatibility | **One-time migration CLI** (not in-place read) |
| Option C (close FCs) as P0 | **Approved** |
| Option E (shared cross-user files) | **REJECTED** — data isolation is a core selling point |
| Option D (lazy mapping) | **Already implemented** via `UserMemoryRegistry` LRU |
| All-growable runtime stores | **Approved** — no silent data loss; grow instead of reject |
| Pre-allocation strategy | **Use existing YAML capacity config** (`SPECTOR_MAX_MEMORIES`) to drive region sizes |

---

## Current State — Verified File Inventory

Source: `StorageLayout.java`

### Runtime Files (Global Per Namespace): 13 mmap files

| # | File | Managing Class | Current "Full" Behavior | V4: Growable |
|:--|:-----|:---------------|:------------------------|:-------------|
| 1 | `working.mem` | `WorkingRecordMemory` | Circular FIFO overwrite | Yes (configurable) |
| 2 | `coactivation.tracker` | `CoActivationRecordMemory` | Stops tracking | Yes |
| 3 | `index.midx` | `IndexRecordMemory` | Stops indexing | Yes |
| 4 | `index.idpl` | `IndexRecordMemory` | Stops indexing | Yes |
| 5 | `hebbian.graph` | `HebbianGraphMemory` | Evicts weakest edges | Yes |
| 6 | `temporal.chain` | `TemporalChainMemory` | Stops linking | Yes |
| 7 | `temporal-facts.tfacts` | `TemporalKnowledgeGraph` | Append full | Yes |
| 8 | `entity-directory.edir` | `EntityDirectory` | Rejects entities (silent) | Yes |
| 9 | `hypergraph.hyeg` | `HyperEntityGraphMemory` | Rejects (returns -1) | Yes |
| 10 | `entity-types.treg` | `TypeRegistryMemory` | Rejects new types | Yes |
| 11 | `relation-types.treg` | `TypeRegistryMemory` | Rejects new types | Yes |
| 12 | `bm25.bidx` | `MemoryBM25Index` | Rebuilt periodically | Yes |
| 13 | `checkpoint.meta` | `CheckpointDaemon` | Fixed 4KB | Fixed (no growth needed) |

> **Note**: `entity-directory-names.idx` is a **serialized Java file** (standard `OutputStream`), NOT mmap. Does not consume FD/VMA. Actual mmap count is 13.

### Partition Files (Per Partition): 4 mmap files

| # | File | Managing Class | Growth Strategy |
|:--|:-----|:---------------|:----------------|
| 1 | `semantic.mem` | `SemanticRecordMemory` | Fixed -> partition roll when full |
| 2 | `episodic.mem` | `EpisodicRecordMemory` | Fixed -> partition roll when full |
| 3 | `procedural.mem` | `ProceduralRecordMemory` | Fixed -> partition roll when full |
| 4 | `text.dat` | `TextAppendMemory` | Fixed -> partition roll when full |

### Existing Config-Driven Sizing

Capacities are **already configurable** via `application.yml` / env vars. The existing wiring in `SpectorAutoConfiguration` (lines 78-86):

```yaml
# application.yml
spector:
  memory:
    capacity: ${SPECTOR_MAX_MEMORIES:10000}   # drives ALL per-store capacities
```

```java
// SpectorAutoConfiguration.java — existing wiring
var builder = DefaultSpectorMemory.builder()
    .semanticCapacity(memoryProps.getCapacity())     // from YAML
    .hebbianGraphCapacity(memoryProps.getCapacity())  // from YAML
    .temporalChainCapacity(memoryProps.getCapacity()) // from YAML
    .entityGraphCapacity(memoryProps.getCapacity());  // from YAML
```

Additional per-store defaults in `SpectorMemoryBuilder`:

| Property | Default | Env Override |
|:---------|:--------|:-------------|
| `capacity` (drives semantic, hebbian, temporal, entity) | 10,000 | `SPECTOR_MAX_MEMORIES` |
| `workingCapacity` | 100 | — |
| `episodicPartitionCapacity` | 1,000 | — |
| `proceduralCapacity` | 1,000 | — |
| `entityGraphCapacity` | 50,000 | `SPECTOR_MAX_MEMORIES` (overridden) |
| `hebbianMaxDegree` | 24 | — |
| `entityMaxDegree` | 16 | — |

**For V4 bundle sizing**: Region sizes are computed as `capacity × stride`, using the SAME config values. No new configuration is needed — the existing `SPECTOR_MAX_MEMORIES` env var already controls pre-allocation sizes. Users who set `SPECTOR_MAX_MEMORIES=1000000` get 1M-record pre-allocation automatically.

### Existing LRU at Synapse Layer

`UserMemoryRegistry` already implements **user-level LRU eviction** (cap: `spector.auth.memory.max-instances`, default **512**). At most 512 users have open mmap files at any time.

---

## Decision — Approved Options

### Option C: Close FileChannels After Mapping (P0 — Immediate)

Close `FileChannel` immediately after `fc.map()` in `AbstractMemory`. The `MemorySegment` mapping remains valid per POSIX/FFM guarantees.

**Impact**: Eliminates ALL file descriptors. Only VMAs remain.
**Effort**: ~2 hours.

### Option A: Consolidated Runtime Bundle — All-Growable Architecture

Merge 13 runtime files into 1 `runtime.bundle` file per namespace.

**Key design change (CEO directive)**: ALL stores are growable. No store silently rejects or drops data on capacity overflow. Instead, the region grows via relocate-to-tail.

### Option B: Consolidated Partition Bundle

Merge 4 partition files into 1 `partition.bundle` file per partition. All stores are fixed-size (partitions roll on overflow — no growth needed).

### Option E: Shared Cross-User Files (Rejected)

> *"We cannot store all users' data in the same file. Our main selling point is complete data isolation."* — CEO

---

## Architecture: All-Growable Runtime Bundle

### Design Principles

1. **No silent data loss** — every store can grow instead of rejecting
2. **Pre-allocate generously** — use existing `SPECTOR_MAX_MEMORIES` config to drive region sizes
3. **Single mmap + `asSlice()`** — 1 VMA per namespace for normal operation
4. **Grow-and-remap as safety valve** — rare event, not normal operation
5. **80% capacity warnings** — `CheckpointDaemon` monitors per-region usage

### How Region Sizes Are Derived from Config

Region sizes are computed deterministically from the user's capacity config:

```java
// BundleLayoutCalculator — derives region sizes from existing config
public record RegionLayout(RegionId id, long offset, long allocatedSize) {}

public static List<RegionLayout> compute(SpectorMemoryBuilder config) {
    long offset = BUNDLE_HEADER_SIZE;  // 4KB
    List<RegionLayout> regions = new ArrayList<>();

    // Each region: size = STORE_HEADER + capacity × stride
    regions.add(region(WORKING,     offset, config.workingCapacity,     WorkingRecordMemory.STRIDE));
    regions.add(region(COACTIVATION, offset, config.semanticCapacity,   CoActivationRecordMemory.STRIDE));
    regions.add(region(HEBBIAN,     offset, config.hebbianGraphCapacity, HebbianGraphMemory.fileSize(cap, maxDegree)));
    regions.add(region(TEMPORAL,    offset, config.temporalChainCapacity, TemporalChainMemory.STRIDE));
    regions.add(region(ENTITY_DIR,  offset, config.entityGraphCapacity, EntityDirectory.fileSize(cap, adjDegree)));
    regions.add(region(HYPERGRAPH,  offset, config.entityGraphCapacity, HyperEntityGraphMemory.fileSize(cap)));
    regions.add(region(ENTITY_TYPES, offset, TYPE_REGISTRY_DEFAULT,    TypeRegistryMemory.STRIDE));
    regions.add(region(RELATION_TYPES, offset, TYPE_REGISTRY_DEFAULT,  TypeRegistryMemory.STRIDE));
    regions.add(region(CHECKPOINT,  offset, 1,                         4096));
    regions.add(region(TEMPORAL_FACTS, offset, TEMPORAL_FACTS_INITIAL, 1));  // 16MB
    regions.add(region(INDEX_MIDX,  offset, config.semanticCapacity * 10, IndexRecordMemory.SLOT_STRIDE));
    regions.add(region(INDEX_IDPL,  offset, config.semanticCapacity * 10, IDPL_AVG_ENTRY));
    regions.add(region(BM25,       offset, BM25_INITIAL_SIZE,         1));  // 16MB
    return regions;
}
```

**User control**: `SPECTOR_MAX_MEMORIES=1000000` → all regions pre-allocated for 1M records. The same env var that works today drives bundle sizes — zero new configuration needed.

### Single Mapping + asSlice() (Normal Operation)

Since all regions are generously pre-allocated, growth is **extremely rare**. This justifies the simpler single-mapping approach:

```
runtime.bundle (1 mmap, 1 VMA):
  ┌──────────────────────────────────────────────────────────────────────┐
  │ BundleDirectory (4KB)                                               │
  ├──────────────────────────────────────────────────────────────────────┤
  │ Region 0: working.mem          │ Region 1: coactivation.tracker     │
  ├────────────────────────────────┼────────────────────────────────────-│
  │ Region 2: hebbian.graph        │ Region 3: temporal.chain           │
  ├────────────────────────────────┼────────────────────────────────────-│
  │ Region 4: entity-directory.edir                                     │
  ├──────────────────────────────────────────────────────────────────────│
  │ Region 5: hypergraph.hyeg                                           │
  ├──────────────────────────────────────────────────────────────────────│
  │ Region 6-8: type registries + checkpoint (small)                    │
  ├──────────────────────────────────────────────────────────────────────│
  │ Region 9: temporal-facts.tfacts                                     │
  ├──────────────────────────────────────────────────────────────────────│
  │ Region 10-11: index.midx + index.idpl                               │
  ├──────────────────────────────────────────────────────────────────────│
  │ Region 12: bm25.bidx                                                │
  └──────────────────────────────────────────────────────────────────────┘
  └───────────────── ONE MemorySegment, asSlice() per store ──────────────┘
```

**1 VMA. 0 FDs (after Option C). All stores accessible.**

### Region Growth Protocol

When any store fills its pre-allocated region at 100% (extremely rare with generous pre-allocation):

```
Step 1: Store detects overflow
        - AbstractMemory.append() detects count >= capacity
        - Instead of throwing or rejecting: notifies BundleManager

Step 2: BundleManager.growRegion(regionId)
        a. Acquire exclusive remap lock (brief global pause for this namespace)
        b. Compute new region size (2× current, page-aligned)
        c. Close Arena → munmap entire bundle
        d. Extend file: allocate new space at END of file
        e. Copy old region data to new tail location
        f. Update BundleDirectory entry:
           - region.offset = newTailOffset
           - region.allocatedSize = newSize
        g. Re-open FileChannel → remap entire file
        h. Re-slice ALL region handles from new master segment
        i. Close FileChannel (Option C)
        j. Release remap lock

Step 3: Old region space becomes dead space
        - Reclaimable by periodic compaction (CLI or background)
```

**Crash safety**: The BundleDirectory update in step (f) is the atomic commit point. If the JVM crashes before the directory is flushed to disk, the old directory is intact and the system recovers with the old (pre-growth) data. No corruption.

### BundleDirectory Structure (On Disk)

```
Offset 0x0000: BundleHeader (256B)
  +-- magic: "SRTB" (Spector Runtime Bundle) or "SPTB" (Partition Bundle)
  +-- version: uint32 (1)
  +-- layoutVersion: uint32 (4 = V4)
  +-- regionCount: uint32
  +-- totalFileSize: uint64
  +-- checksum: uint64 (xxHash64 of directory)
  +-- created: uint64 (epoch millis)
  +-- modified: uint64 (epoch millis)
  +-- capacityConfig: uint32 (the SPECTOR_MAX_MEMORIES value used at creation)
  +-- reserved: padding to 256B

Offset 0x0100: RegionDirectory (13 x 64B = 832B, total header padded to 4KB)
  Per region entry (64B):
  +-- regionId:      uint16  (enum ordinal: WORKING=0, COACTIVATION=1, ...)
  +-- flags:         uint16  (LIVE=0x01, DEAD=0x00, GROWABLE=0x02)
  +-- offset:        uint64  (byte offset in file, page-aligned)
  +-- allocatedSize: uint64  (total allocated bytes for this region)
  +-- usedSize:      uint64  (actual bytes used / current count × stride)
  +-- capacity:      uint32  (max records this region can hold)
  +-- schemaVersion: uint32  (per-region schema version for forward compat)
  +-- reserved:      16B
```

**On restart**: Read directory → for each region with `flags == LIVE`, map `MemorySegment.asSlice(offset, allocatedSize)`. Dead regions are ignored.

### Dead Space Management

Dead space accumulates when regions relocate to the tail. Three options:

| Strategy | Description | When |
|:---------|:------------|:-----|
| **Accept** | Dead space sits unused until compaction | Default — dead space is minimal |
| **CLI compact** | `spector-inspect compact --namespace <id>` rewrites the bundle, packing all live regions contiguously | Manual maintenance |
| **Auto-compact** | `CheckpointDaemon` triggers compaction when dead_ratio > 30% | Background during low-load |

**Expected dead space**: Near zero. With generous pre-allocation (1M records), most stores never fill up. Growth events are once-in-months at best. Dead space from a single relocation is the size of one old region — a few MB.

### CheckpointDaemon Monitoring (80% Warning)

```java
// CheckpointDaemon — per-region capacity monitoring (NEW in V4)
for (BundleRegion region : bundle.liveRegions()) {
    float usage = (float) region.usedSize() / region.allocatedSize();
    metrics.gauge("spector.region.usage",
        Tags.of("region", region.id().name(), "namespace", namespaceId),
        usage);

    if (usage >= 0.95f) {
        log.error("[{}] CRITICAL: region {} at {}% — growth imminent",
                  namespaceId, region.id(), (int)(usage * 100));
    } else if (usage >= 0.80f) {
        log.warn("[{}] region {} at {}% capacity — consider increasing SPECTOR_MAX_MEMORIES",
                 namespaceId, region.id(), (int)(usage * 100));
    }
}
```

**Observable**: Prometheus/Grafana dashboards can alert on `spector.region.usage > 0.8`.

---

## Partition Bundle (Option B) — Simpler

All 4 partition stores are **fixed-size** (determined by config at partition creation). When full, the partition rolls to a new bundle. **No growth protocol, no dead space, no remap.**

```
partition.bundle (1 mmap, 1 VMA):
  +-- BundleHeader (4KB)
  +-- Region 0: semantic.mem   (capacity x stride, EXACT)
  +-- Region 1: episodic.mem   (capacity x stride, EXACT)
  +-- Region 2: procedural.mem (capacity x stride, EXACT)
  +-- Region 3: text.dat       (32MB or configurable)
```

---

## Pre-allocation Sizing (Config-Driven)

| `SPECTOR_MAX_MEMORIES` | Virtual File Size | Actual Disk (fresh user) | Suitable For |
|:-----------------------|:------------------|:-------------------------|:-------------|
| 10,000 (default) | ~60-80MB | ~1-5MB | Development, small deployments |
| 100,000 | ~200-400MB | ~2-10MB | Medium deployments |
| 1,000,000 | ~1-2GB | ~5-20MB | Production (10K users) |

> **Sparse files**: The OS only allocates physical disk blocks for written pages. A 1GB virtual file with 1K actual records uses ~5MB of real disk space. This is a fundamental property of modern filesystems (ext4, XFS, NTFS, APFS).

### VMA Math at Scale

| Scenario | VMAs per NS | 512 active | 2000 active |
|:---------|:------------|:-----------|:------------|
| **Today** (V3, 3 partitions) | 13 + 12 = 25 | 12,800 | 50,000 (exceeds limit!) |
| **V4 (C + A + B)** | 1 + 3 = 4 | **2,048** | **8,000** |

> With V4 bundle architecture, we can safely raise the LRU cap to 2,000+ active users — well under the 65,530 `vm.max_map_count` kernel limit.

---

## Verification Plan

### Automated Tests
- Golden-file test: serialize V3 layout -> migrate to V4 -> verify byte-for-byte equivalence of all store data
- Capacity overflow tests: verify stores grow instead of rejecting in V4 mode
- Concurrent access stress test: multiple threads reading/writing different regions
- Remap test: force region growth, verify all stores remain accessible after remap
- Dead space test: grow a region, verify directory tracks dead space correctly
- Restart test: grow a region, restart, verify directory points to correct location
- `spector-inspect` CLI extension: dump bundle directory and individual regions

### Manual Verification
- FD count measurement: `lsof -p $PID | wc -l` before and after migration
- VMA count measurement: `cat /proc/$PID/maps | wc -l`
- Performance benchmarks: remember/recall latency comparison V3 vs V4
- Sparse file validation: `du -h` vs `ls -lh` to confirm sparse allocation works

---

## Implementation Sequence

### Phase 0 — Immediate (This Week)
1. Option C: Close FileChannels after mapping
2. Production docs: `ulimit -n 524288` and `vm.max_map_count=1048576`

### Phase 1 — Partition Bundle (Simpler, implement first)
3. `BundleHeader` + `RegionDirectory` classes (shared by A and B)
4. `PartitionBundle` implementation (4 fixed regions, 1 mmap)
5. Migration CLI: V3 partition directories -> V4 partition bundles
6. Tests + golden-file validation

### Phase 2 — Runtime Bundle (All-Growable)
7. `RuntimeBundle` implementation (13 regions, config-driven sizing)
8. `BundleManager.growRegion()` — relocate-to-tail protocol
9. `CheckpointDaemon` — per-region 80%/95% monitoring
10. Migration CLI: V3 runtime directory -> V4 runtime bundle
11. Remap stress tests + restart recovery tests
12. `spector-inspect` bundle support

### Phase 3 — Store Behavior Changes
13. Replace all silent-reject paths with `BundleManager.growRegion()` calls
14. Update `EntityDirectory.intern()`: grow instead of returning -1
15. Update `HyperEntityGraphMemory.addHyperedge()`: grow instead of returning -1
16. Update `TypeRegistryMemory`: grow instead of rejecting
17. Update `TemporalChainMemory`: grow instead of stopping
18. Update `CoActivationRecordMemory`: grow instead of stopping

---

## Alternatives Considered

| Alternative | Verdict | Reason |
|:------------|:--------|:-------|
| Option E: Shared cross-user files | Rejected | Violates data isolation (CEO) |
| Option D: Lazy mapping | Already implemented | `UserMemoryRegistry` LRU (cap=512) |
| Per-region mappings (N VMAs) | Rejected in favor of single mapping | With generous pre-allocation, growth is rare enough to accept full remap |
| Hybrid mapping (fixed + per-region growable) | Deferred | Adds VMA management complexity; single mapping is simpler and sufficient |
| `pread()/pwrite()` for cold stores | Deferred | Requires rewriting store access layer |

---

## Consequences

### Positive
- **No silent data loss** — every store grows instead of rejecting on overflow
- FD count: 12,800 -> **0** (Option C)
- VMA count: 12,800 -> **2,048** for 512 active users (84% reduction)
- 10,000 concurrent users becomes architecturally feasible (LRU cap raisable to 2,000+)
- Existing `SPECTOR_MAX_MEMORIES` config drives all region sizes — zero new config
- Partition bundle: 1 file to copy/backup/migrate instead of 4

### Negative
- V4 layout is a breaking on-disk change (mitigated by one-time migration CLI)
- Remap during growth is a brief stop-the-world per namespace (mitigated by rarity)
- Dead space accumulates on growth (mitigated by CLI compaction)
- Bundle debugging requires `spector-inspect` CLI (mitigated by directory metadata)

### Risks
- **SIGBUS on bundle corruption**: If bundle is truncated, accessing any region causes JVM crash. Mitigated by: BundleHeader checksum, WAL recovery, periodic integrity checks.
- **Virtual memory pressure**: 1M-record pre-allocation means ~1-2GB virtual per namespace. At 512 active users: ~500GB-1TB virtual. 64-bit address space is 128TB — plenty of headroom. Physical usage is sparse.
