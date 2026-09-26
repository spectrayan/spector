# ADR-0043: Single-VMA Bundle Layout Specification

| Field | Value |
|:---|:---|
| **Status** | Superseded by ADR-0004 |
| **Date** | 2026-08-04 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | ADR-0004 |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

In Spector Memory Kernel V3, off-heap storage was organized around individual `.smd` segment files per memory store. The class hierarchy was structured as follows:

## 1. Kernel Class Hierarchy — Current State (V3)

```mermaid
classDiagram
    direction TB

    class Memory~L~ {
        <<interface>>
        +id() MemoryId
        +layout() L
        +arena() Arena
        +segment() MemorySegment
        +headerSegment() MemorySegment
        +size() int
        +capacity() int
        +schemaVersion() int
        +shape() MemoryShape
        +flush() void
        +close() void
        +bindWal(MemoryWal) void
    }

    class MemoryLayout {
        <<interface>>
        +layoutId() int
        +schemaVersion() int
        +recordStride() int
        +crcEnabled() boolean
        +name() String
    }

    class MemoryShape {
        <<enumeration>>
        RECORD
        PARTITIONED
        GRAPH
        CHAIN
        APPEND
        REGISTRY
    }

    class AbstractMemory~L~ {
        #id: MemoryId
        #layout: L
        #arena: Arena
        #segment: MemorySegment
        #capacity: int
        #persistent: boolean
        #count: int
        #fileChannel: FileChannel
        #filePath: Path
        -visibleCount: volatile int
        +AbstractMemory(id,layout,cap,segBytes)
        +AbstractMemory(id,layout,cap,segBytes,filePath)
        +AbstractMemory(id,layout,cap,arena,seg,count,persistent,path,fc)
        +dataOffset() long
        #publishVisible() void
        +visibleCount() int
        #persistCount() void
    }

    class AbstractRecordMemory~L~ {
        +recordOffset(index) long
    }
    class AbstractAppendMemory~L~ {
        #appendCursor: long
    }
    class AbstractGraphMemory~L~ {
        #lock: StampedLock
    }
    class AbstractRegistryMemory {
    }

    class DefaultRecordMemory~L~
    class DefaultAppendMemory~L~
    class DefaultRegistryMemory

    Memory <|.. AbstractMemory : implements
    AbstractMemory <|-- AbstractRecordMemory
    AbstractMemory <|-- AbstractAppendMemory
    AbstractMemory <|-- AbstractGraphMemory
    AbstractMemory <|-- AbstractRegistryMemory
    AbstractRecordMemory <|-- DefaultRecordMemory
    AbstractAppendMemory <|-- DefaultAppendMemory
    AbstractRegistryMemory <|-- DefaultRegistryMemory

    Memory --> MemoryLayout : parameterized by
    AbstractMemory --> MemoryShape : shape()
```

---

## 2. Concrete Store Hierarchy — Current State (V3)

```mermaid
classDiagram
    direction TB

    class AbstractRecordMemory~L~
    class AbstractCognitiveRecordMemory {
        -mmapFile() MmapResult
        +fromBundle(Arena,seg,cap)$
    }
    class SemanticRecordMemory
    class EpisodicRecordMemory
    class ProceduralRecordMemory
    class WorkingRecordMemory

    class AbstractAppendMemory~L~
    class TextAppendMemory

    class AbstractGraphMemory~L~
    class EntityDirectory {
        -entitySegment: MemorySegment
        -adjacencySegment: MemorySegment
        -headerSegment: MemorySegment
        -nameIndex: ConcurrentHashMap
        -lock: StampedLock
        +Init.heap(cap,typeReg)$ Init
        +Init.mmap(path,cap,typeReg)$ Init
    }
    class HebbianGraphMemory
    class HyperEntityGraphMemory
    class TemporalChainMemory

    class DefaultRecordMemory~L~
    class IndexRecordMemory
    class CoActivationRecordMemory

    class DefaultRegistryMemory
    class TypeRegistryMemory

    AbstractRecordMemory <|-- AbstractCognitiveRecordMemory
    AbstractCognitiveRecordMemory <|-- SemanticRecordMemory
    AbstractCognitiveRecordMemory <|-- EpisodicRecordMemory
    AbstractCognitiveRecordMemory <|-- ProceduralRecordMemory
    AbstractCognitiveRecordMemory <|-- WorkingRecordMemory

    AbstractAppendMemory <|-- TextAppendMemory

    AbstractGraphMemory <|-- EntityDirectory
    AbstractGraphMemory <|-- HebbianGraphMemory
    AbstractGraphMemory <|-- HyperEntityGraphMemory
    AbstractGraphMemory <|-- TemporalChainMemory

    DefaultRecordMemory <|-- IndexRecordMemory
    DefaultRecordMemory <|-- CoActivationRecordMemory

    DefaultRegistryMemory <|-- TypeRegistryMemory
```

---

## 2. Problem Statement

Under V3 storage architecture, every cognitive partition and store opened dedicated file descriptors and distinct `MemorySegment` mappings:

- 10+ open file descriptors per active namespace.
- Inability to share a single contiguous Virtual Memory Area (VMA) across related stores.
- High TLB overhead and memory fragmentation when scaling to thousands of concurrent tenants.
- Independent segment growth triggered uncoordinated unmaps and memory reallocations.

## 3. Decision Drivers

- **Single-VMA Consolidation**: Consolidate multiple distinct store regions into a single file descriptor and memory mapping (`RuntimeBundle` and `PartitionBundle`).
- **Zero-Copy Region Slicing**: Slices within the bundle must match `AbstractRecordMemory` and `AbstractAppendMemory` contracts without pointer translation overhead.
- **Coordinated Region Growth**: Controlled virtual memory reservation (`Arena.allocate()`) with automatic remap on exhaustion.
- **Deterministic Lifecycle**: Hard unmap and flush synchronization owned by bundle managers rather than individual store instances.

## 4. Considered Options

### Option 1: Status Quo (Individual `.smd` files per store)

- **Description**: Maintain independent files for Working, Episodic, Semantic, Procedural, and Hebbian stores.
- **Advantages**: Simple isolated file format.
- **Disadvantages**: Severe file descriptor proliferation; TLB miss amplification; uncoordinated I/O flushing.

### Option 2: Archive Container (Tar/Zip)

- **Description**: Package `.smd` files inside an uncompressed container archive.
- **Advantages**: Single file on disk.
- **Disadvantages**: Lacks random-access zero-copy Panama FFM slicing; requires unpacking or custom seekable I/O.

### Option 3: Single-VMA Binary Bundle Layout with Sub-Region Index (Selected)

- **Description**: Multiplex multiple named regions (`RegionId`) within a single contiguous off-heap file mapping governed by a 4KB bundle header and dynamic region allocation table.
- **Advantages**: Exactly 1 file descriptor per bundle; zero-copy sub-segment slicing; unified flush and close lifecycle.
- **Disadvantages**: Requires region resizing protocol and internal alignment padding.

## 5. Decision Outcome

## 3. V4 Bundle Classes — How They Fit the Kernel

> [!IMPORTANT]
> The bundle is **NOT** a `Memory<L>` subclass. It is a **container** that owns a shared `Arena` + master `MemorySegment` and provides region slices to stores. Stores are constructed via the existing **wrapping constructor** pattern (same as `EntityDirectory.Init`).

```mermaid
classDiagram
    direction TB

    class MemoryShape {
        <<enumeration>>
        RECORD
        PARTITIONED
        GRAPH
        CHAIN
        APPEND
        REGISTRY
        BUNDLE
    }

    class BundleLayout {
        <<MemoryLayout>>
        +LAYOUT_ID = 0x42554E44
        +SCHEMA_VERSION = 1
        +REGION_ENTRY_STRIDE = 64
        +layoutId() int
        +schemaVersion() int
        +recordStride() int
        +crcEnabled() boolean
        +name() String
    }

    class RegionId {
        <<enumeration>>
        SEMANTIC(0)
        EPISODIC(1)
        PROCEDURAL(2)
        TEXT(3)
        WORKING(10)
        COACTIVATION(11)
        INDEX_MIDX(12)
        INDEX_IDPL(13)
        HEBBIAN(14)
        TEMPORAL_CHAIN(15)
        TEMPORAL_FACTS(16)
        ENTITY_DIRECTORY(17)
        ENTITY_NAMES(18)
        HYPERGRAPH(19)
        ENTITY_TYPES(20)
        RELATION_TYPES(21)
        BM25(22)
        CHECKPOINT(23)
        +isPartitionRegion() boolean
        +isRuntimeRegion() boolean
    }

    class RegionEntry {
        <<record>>
        +regionId: RegionId
        +flags: short
        +offset: long
        +allocatedSize: long
        +usedSize: long
        +capacity: int
        +stride: int
        +layoutId: int
        +schemaVersion: int
        +ENTRY_BYTES = 64
        +FLAG_LIVE = 0x01
        +FLAG_GROWABLE = 0x02
        +read(seg, entryOffset)$ RegionEntry
        +write(seg, entryOffset, entry)$ void
    }

    class BundleSubHeader {
        +OFFSET = 64
        +SIZE = 64
        -VH_BUNDLE_MAGIC: VarHandle
        -VH_TOTAL_SIZE: VarHandle
        -VH_DIR_CHECKSUM: VarHandle
        -VH_CAPACITY_CONFIG: VarHandle
        -VH_DATA_START: VarHandle
        +write(seg, magic, totalSize, ...)$ void
        +readBundleMagic(seg)$ int
        +readTotalSize(seg)$ long
    }

    class BundleDirectory {
        -entries: List~RegionEntry~
        -header: MemoryHeader
        +HEADER_OFFSET = 0
        +SUB_HEADER_OFFSET = 64
        +ENTRIES_OFFSET = 128
        +read(masterSegment)$ BundleDirectory
        +write(masterSegment) void
        +findRegion(id) RegionEntry
        +liveRegions() List~RegionEntry~
        +markDead(id) void
        +updateRegion(id, newOffset, newSize) void
        +dataStartOffset(maxRegions) long
    }

    class PartitionBundle {
        -arena: Arena
        -masterSegment: MemorySegment
        -directory: BundleDirectory
        -bundlePath: Path
        +Init.mmap(path,semCap,epiCap,procCap,textBytes,dims)$ PartitionBundle
        +Init.open(path)$ PartitionBundle
        +Init.heap(semCap,epiCap,procCap,textBytes,dims)$ PartitionBundle
        +regionSegment(id) MemorySegment
        +arena() Arena
        +directory() BundleDirectory
        +close() void
    }

    class RuntimeBundle {
        -bundlePath: Path
        -arena: volatile Arena
        -masterSegment: volatile MemorySegment
        -directory: volatile BundleDirectory
        -remapLock: StampedLock
        -regionSlices: Map~RegionId, MemorySegment~
        +Init.mmap(path,config)$ RuntimeBundle
        +Init.open(path)$ RuntimeBundle
        +Init.heap(config)$ RuntimeBundle
        +regionSegment(id) MemorySegment
        +arena() Arena
        +growRegion(id) void
        +close() void
    }

    class BundleManager {
        -bundle: RuntimeBundle
        +growRegion(regionId) void
        +regionUsage(id) float
        +compact() void
    }

    class BundleLayoutCalculator {
        +computePartitionLayout(semCap,epiCap,procCap,textBytes,dims)$ List~RegionEntry~
        +computeRuntimeLayout(config)$ List~RegionEntry~
    }

    BundleLayout ..|> MemoryLayout : implements
    PartitionBundle --> BundleDirectory : owns
    PartitionBundle --> BundleSubHeader : reads/writes
    RuntimeBundle --> BundleDirectory : owns
    RuntimeBundle --> BundleSubHeader : reads/writes
    BundleManager --> RuntimeBundle : manages growth
    BundleDirectory --> RegionEntry : contains N×
    BundleDirectory --> MemoryHeader : uses standard SMKM
    BundleLayoutCalculator --> RegionEntry : produces
    RegionEntry --> RegionId : identifies
    PartitionBundle --> MemoryShape : BUNDLE
    RuntimeBundle --> MemoryShape : BUNDLE

    note for PartitionBundle "Follows EntityDirectory.Init pattern:\nInit.mmap() / Init.open() / Init.heap()\nShared Arena.ofShared() for all regions"
    note for RuntimeBundle "volatile arena + StampedLock\nfor concurrent remap during growth"
```

---

## 4. Bundle ↔ Store Wiring (How Stores Get Their Segments)

```mermaid
classDiagram
    direction LR

    class RuntimeBundle {
        +regionSegment(HEBBIAN) MemorySegment
        +regionSegment(ENTITY_DIRECTORY) MemorySegment
        +regionSegment(COACTIVATION) MemorySegment
        +arena() Arena
    }

    class AbstractMemory~L~ {
        +AbstractMemory(id,layout,cap,arena,seg,count,persistent,path,fc)
        note: "Wrapping constructor — accepts\npre-made Arena + MemorySegment\nfrom bundle. Same pattern used\nby EntityDirectory.Init today."
    }

    class HebbianGraphMemory {
        +fromBundle(Arena,seg,cap,maxDeg)$ HebbianGraphMemory
    }
    class EntityDirectory {
        +Init.fromBundle(Arena,edirSlice,namesSlice,cap,typeReg)$ EntityDirectory
    }
    class CoActivationRecordMemory {
        +fromBundle(Arena,seg,cap)$ CoActivationRecordMemory
    }

    RuntimeBundle ..> HebbianGraphMemory : "regionSegment(HEBBIAN)"
    RuntimeBundle ..> EntityDirectory : "regionSegment(ENTITY_DIRECTORY)"
    RuntimeBundle ..> CoActivationRecordMemory : "regionSegment(COACTIVATION)"

    HebbianGraphMemory --|> AbstractMemory : wrapping ctor
    EntityDirectory --|> AbstractMemory : wrapping ctor
    CoActivationRecordMemory --|> AbstractMemory : wrapping ctor

    note for RuntimeBundle "1 Arena, 1 MemorySegment\n14 region slices via asSlice()"
```

---

## 5. On-Disk Format — Bundle File Layout

```mermaid
block-beta
    columns 1

    block:header["MemoryHeader (64B) — Standard SMKM"]
        columns 4
        A["magic: 0x534D4B4D"] B["shape: BUNDLE(6)"] C["layoutId: 0x42554E44 'BUND'"] D["capacity: maxRegions"]
        E["count: liveRegions"] F["stride: 64"] G["created/modified timestamps"] H["CRC32C checksum"]
    end

    block:subheader["BundleSubHeader (64B) — VarHandle accessors"]
        columns 4
        I["bundleMagic: SRTB/SPTB"] J["bundleVersion: 1"] K["totalFileSize: int64"] L["dirChecksum: xxHash64"]
        M["capacityConfig: int32"] N["dataStartOff: int64"] O["reserved padding"] P["---"]
    end

    block:directory["RegionEntry Directory (64B × maxRegions)"]
        columns 3
        Q["Entry 0: WORKING<br/>offset|size|cap|layout=COG"] R["Entry 1: COACTIVATION<br/>offset|size|cap|layout=COAX"] S["Entry 2: INDEX_MIDX<br/>offset|size|cap|layout=MIDX"]
        T["Entry 3: INDEX_IDPL<br/>offset|size|cap|layout=IDPL"] U["Entry 4: HEBBIAN<br/>offset|size|cap|layout=HCSR"] V["..."]
        W["Entry 12: BM25<br/>offset|size|cap|layout=BM25"] X["Entry 13: CHECKPOINT<br/>offset|size|cap|layout=CKPT"] Y["(padding to 4KB)"]
    end

    block:data["Region Data Slabs (page-aligned at 4096B boundaries)"]
        columns 2
        Z["Region 0: WORKING<br/>[64B MemoryHeader][records...]"] AA["Region 1: COACTIVATION<br/>[64B MemoryHeader][records...]"]
        AB["Region 2: INDEX_MIDX<br/>[64B MemoryHeader][slots...]"] AC["Region 3: INDEX_IDPL<br/>[64B MemoryHeader][pool...]"]
        AD["Region 4: HEBBIAN<br/>[16B HGPH header][edge slab]"] AE["Region 5: TEMPORAL_CHAIN<br/>[64B MemoryHeader][links...]"]
        AF["...more regions..."] AG["Region 13: CHECKPOINT<br/>[16B CKPT header][4KB]"]
    end

    header --> subheader
    subheader --> directory
    directory --> data
```

> [!NOTE]
> Each region's internal format is **unchanged** from V3. The region data starts with the same header the store expects (SMKM 64-byte for most, 16-byte HGPH for HebbianGraph, 80-byte EDIR for EntityDirectory). The bundle simply concatenates them into one file.

---

## 6. Sequence: Bundle Creation (`Init.mmap()`)

```mermaid
sequenceDiagram
    participant Builder as SpectorMemoryFactory
    participant Calc as BundleLayoutCalculator
    participant RB as RuntimeBundle.Init
    participant FC as FileChannel
    participant Arena as Arena.ofShared()
    participant Dir as BundleDirectory
    participant MH as MemoryHeader

    Builder->>Calc: computeRuntimeLayout(config)
    Calc-->>Builder: List<RegionEntry> [14 entries with offsets & sizes]

    Builder->>RB: mmap(bundlePath, config)
    RB->>FC: open(bundlePath, CREATE, READ, WRITE)
    RB->>RB: compute totalFileSize from region entries
    RB->>FC: position(totalFileSize - 1), write(0x00)
    Note right of FC: Pre-allocate sparse file

    RB->>Arena: Arena.ofShared()
    RB->>FC: map(READ_WRITE, 0, totalFileSize, arena)
    FC-->>RB: masterSegment

    RB->>FC: close()
    Note right of RB: Option C: FD released immediately

    RB->>MH: write(masterSegment, 0, BUNDLE, BUND, ...)
    Note right of MH: Standard 64-byte SMKM header

    RB->>Dir: new BundleDirectory(entries)
    RB->>Dir: write(masterSegment)
    Note right of Dir: Sub-header + 14 RegionEntry records

    loop For each RegionEntry
        RB->>RB: regionSlices.put(id, masterSegment.asSlice(offset, allocatedSize))
    end

    RB-->>Builder: RuntimeBundle instance
```

---

## 7. Sequence: Store Construction from Bundle

```mermaid
sequenceDiagram
    participant Factory as SpectorMemoryFactory
    participant RB as RuntimeBundle
    participant GraphBuilder as CognitiveGraphBuilder
    participant EDIR as EntityDirectory
    participant AM as AbstractMemory [wrapping ctor]

    Factory->>RB: open(bundlePath) or mmap(bundlePath, config)

    Factory->>GraphBuilder: build(builder, cortex, index, runtimeBundle)

    GraphBuilder->>RB: regionSegment(ENTITY_DIRECTORY)
    RB-->>GraphBuilder: MemorySegment edirSlice (asSlice view)

    GraphBuilder->>RB: regionSegment(ENTITY_NAMES)
    RB-->>GraphBuilder: MemorySegment namesSlice

    GraphBuilder->>RB: arena()
    RB-->>GraphBuilder: shared Arena

    GraphBuilder->>EDIR: Init.fromBundle(arena, edirSlice, namesSlice, cap, typeReg)
    Note right of EDIR: New static factory — follows Init.mmap() pattern

    EDIR->>EDIR: Parse SMKM header from edirSlice[0..64]
    EDIR->>EDIR: Parse sub-header from edirSlice[64..80]
    EDIR->>EDIR: entitySegment = edirSlice.asSlice(DATA_START, entityBytes)
    EDIR->>EDIR: adjacencySegment = edirSlice.asSlice(DATA_START+entityBytes, adjBytes)
    EDIR->>EDIR: headerSegment = edirSlice.asSlice(0, DATA_START)

    EDIR->>AM: super(id, LAYOUT, cap, arena, entitySegment, count, true, bundlePath, null)
    Note right of AM: Wrapping constructor — same pattern as Init.mmap() today<br/>Arena is SHARED from bundle, NOT owned by this store

    EDIR-->>GraphBuilder: EntityDirectory instance
```

---

## 8. Sequence: Region Growth Protocol

```mermaid
sequenceDiagram
    participant Store as EntityDirectory.intern()
    participant BM as BundleManager
    participant RB as RuntimeBundle
    participant Lock as StampedLock
    participant FC as FileChannel
    participant Arena as Arena.ofShared()
    participant Dir as BundleDirectory
    participant MH as MemoryHeader

    Store->>Store: entityCount >= entityCapacity (FULL!)
    Note right of Store: V3: return -1<br/>V4: grow instead

    Store->>BM: growRegion(ENTITY_DIRECTORY)

    BM->>Lock: writeLock() [exclusive]
    Note right of Lock: Brief global pause for this namespace

    BM->>Dir: findRegion(ENTITY_DIRECTORY)
    Dir-->>BM: oldEntry (offset, allocatedSize)

    BM->>BM: newSize = max(2× oldEntry.allocatedSize, minGrowth)
    BM->>BM: newSize = alignToPage(newSize)
    BM->>BM: tailOffset = currentFileSize (page-aligned)

    BM->>RB: arena.close()
    Note right of RB: munmap entire bundle — all slices invalidated

    BM->>FC: open(bundlePath, READ, WRITE)
    BM->>FC: position(tailOffset + newSize - 1), write(0x00)
    Note right of FC: Extend file for new region at tail

    BM->>FC: transferFrom(oldOffset, tailOffset, oldEntry.allocatedSize)
    Note right of FC: Copy old region data to tail position

    BM->>Dir: markDead(ENTITY_DIRECTORY)
    BM->>Dir: updateRegion(ENTITY_DIRECTORY, tailOffset, newSize)
    Note right of Dir: Old entry → DEAD, new entry at tail → LIVE

    BM->>Arena: Arena.ofShared()
    BM->>FC: map(READ_WRITE, 0, newTotalFileSize, newArena)
    FC-->>BM: new masterSegment

    BM->>MH: writeCount(masterSegment, 0, liveRegionCount)
    BM->>Dir: write(masterSegment)
    Note right of Dir: Flush updated directory — crash commit point

    BM->>FC: close()
    Note right of BM: Option C: FD released immediately

    loop For each LIVE region
        BM->>RB: regionSlices.put(id, masterSegment.asSlice(entry.offset, entry.allocatedSize))
    end

    BM->>RB: update arena + masterSegment references
    BM->>Lock: unlockWrite()

    BM-->>Store: growth complete — segment refreshed
    Store->>RB: regionSegment(ENTITY_DIRECTORY)
    RB-->>Store: new (larger) MemorySegment slice
    Store->>Store: re-parse header, update capacity, retry intern()
```

---

## 9. Sequence: Full Lifecycle — Create → Use → Flush → Close

```mermaid
sequenceDiagram
    participant App as Application
    participant DSM as DefaultSpectorMemory
    participant Factory as SpectorMemoryFactory
    participant RB as RuntimeBundle
    participant PB as PartitionBundle
    participant PM as PartitionManager
    participant Persist as PersistenceManager

    Note over App,Persist: ═══ BUILD PHASE ═══

    App->>DSM: DefaultSpectorMemory.builder().build()
    DSM->>Factory: assemble(builder)

    Factory->>Factory: CognitiveCortexBuilder.build()
    Factory->>PB: PartitionBundle.Init.mmap(partitionDir, ...)
    PB-->>Factory: PartitionBundle [1 Arena, 4 region slices]
    Factory->>Factory: Construct Semantic/Episodic/Procedural/Text from bundle slices

    Factory->>RB: RuntimeBundle.Init.mmap(runtimeDir, config)
    RB-->>Factory: RuntimeBundle [1 Arena, 14 region slices]

    Factory->>Factory: MemoryIndexBuilder → IndexRecordMemory.fromBundle(arena, midxSlice, idplSlice)
    Factory->>Factory: CognitiveGraphBuilder → HebbianGraphMemory.fromBundle(arena, hebbianSlice)
    Factory->>Factory: CognitiveGraphBuilder → EntityDirectory.Init.fromBundle(arena, edirSlice, namesSlice)
    Factory->>Factory: BiologicalSubsystemsBuilder → CoActivationRecordMemory.fromBundle(arena, coactSlice)
    Factory->>Factory: RetrievalIndexBuilder → MemoryBM25Index.fromBundle(arena, bm25Slice)

    Factory-->>DSM: SubsystemBundle [all stores wired]

    Note over App,Persist: ═══ USE PHASE ═══

    App->>DSM: remember("Paris is the capital of France")
    DSM->>DSM: CognitiveIngestionTarget.ingestCognitive()
    Note right of DSM: All stores read/write via their region slices

    App->>DSM: recall("What is the capital of France?")
    DSM->>DSM: RecallPipeline.recall()

    Note over App,Persist: ═══ FLUSH & CLOSE PHASE ═══

    App->>DSM: close()
    DSM->>DSM: DaemonSupervisor.close()
    DSM->>DSM: CheckpointDaemon.checkpoint()

    DSM->>Persist: flushAndClose(...)
    
    alt V4 Bundle Mode
        Persist->>RB: Each store calls flush() → regionSlice.force()
        Note right of Persist: Stores flush their own slices,<br/>do NOT close the shared arena
        Persist->>RB: close()
        RB->>RB: directory.write(masterSegment)
        RB->>RB: masterSegment.force()
        RB->>RB: arena.close()
        Note right of RB: Single arena.close() unmaps entire bundle
    else V3 Legacy Mode
        Persist->>Persist: Each store.save() + store.close()
        Note right of Persist: Each store has its own Arena
    end

    DSM->>PM: close()
    PM->>PB: close() [for each partition]
    PB->>PB: masterSegment.force(), arena.close()
```

---

## 10. AbstractMemory — Bundle-Managed Close Behavior

The wrapping constructor today **transfers ownership** of the Arena to the store. In V4, bundle-backed stores must **NOT** close the shared arena — only flush their slice:

```mermaid
classDiagram
    class AbstractMemory~L~ {
        #arena: Arena
        #segment: MemorySegment
        #persistent: boolean
        #bundleManaged: boolean
        +close() void
        +flush() void
    }

    note for AbstractMemory "close() behavior:\nif (persistent && segment != null)\n    segment.force();\nif (!bundleManaged)\n    arena.close();  // owns arena\nelse\n    // skip — bundle owns arena\n\nThe bundleManaged flag is set\nvia the wrapping constructor\nwhen arena comes from a bundle."
```

**Wrapping constructor signature** (updated):

```java
protected AbstractMemory(MemoryId id, L layout, int capacity,
                         Arena arena, MemorySegment segment, int count,
                         boolean persistent, Path filePath,
                         FileChannel fileChannel,
                         boolean bundleManaged)  // NEW parameter
```

> [!NOTE]
> Existing callers (EntityDirectory.Init, AbstractCognitiveRecordMemory) pass `bundleManaged=false` — zero behavioral change. Only `fromBundle()` factories pass `bundleManaged=true`.

---

## 11. PersistenceManager — V4 Integration

```mermaid
sequenceDiagram
    participant DSM as DefaultSpectorMemory.doClose()
    participant PM as PersistenceManager
    participant RB as RuntimeBundle
    participant Store as Each Runtime Store

    DSM->>PM: flushAndClose(persistenceMode, runtimeBundle, stores...)

    alt V4 (runtimeBundle != null)
        loop For each runtime store
            PM->>Store: flush()
            Note right of Store: store.segment.force()<br/>Flushes region slice only<br/>Does NOT close arena
        end
        PM->>RB: flush()
        Note right of RB: directory.write(masterSegment)<br/>masterSegment.force()
        PM->>RB: close()
        Note right of RB: arena.close() — unmaps entire bundle<br/>All region slices become invalid
    else V3 (runtimeBundle == null)
        PM->>PM: Legacy path: save + close each store individually
    end
```

---

---

## 12. Full V4 Package Structure

```mermaid
graph TB
    subgraph "com.spectrayan.spector.memory.kernel"
        Memory["Memory&lt;L&gt;"]
        AbstractMemory["AbstractMemory&lt;L&gt;"]
        MemoryHeader["MemoryHeader"]
        MemoryShape["MemoryShape + BUNDLE"]
        MemoryLayout_IF["MemoryLayout"]
    end

    subgraph "com.spectrayan.spector.memory.kernel.shape"
        AbstractRecordMemory
        AbstractAppendMemory
        AbstractGraphMemory
        AbstractRegistryMemory
    end

    subgraph "com.spectrayan.spector.memory.kernel.layout"
        CognitiveRecordLayout
        EntityDirectoryLayout
        HebbianLayout
        HyperEntityLayout
        TemporalLayout
        IndexEntryLayout
        BundleLayout["BundleLayout ← NEW"]
    end

    subgraph "com.spectrayan.spector.memory.bundle ← NEW PACKAGE"
        BundleLayout2["BundleLayout"]
        RegionId["RegionId"]
        RegionEntry["RegionEntry"]
        BundleSubHeader["BundleSubHeader"]
        BundleDirectory["BundleDirectory"]
        BundleLayoutCalculator["BundleLayoutCalculator"]
        PartitionBundle["PartitionBundle"]
        RuntimeBundle["RuntimeBundle"]
        BundleManager["BundleManager"]
        BundleInspector["BundleInspector"]
    end

    subgraph "com.spectrayan.spector.memory.cortex"
        AbstractCognitiveRecordMemory
        SemanticRecordMemory
        EpisodicRecordMemory
        WorkingRecordMemory
        TextAppendMemory
        MemoryBM25Index
    end

    subgraph "com.spectrayan.spector.memory.graph"
        EntityDirectory
        HyperEntityGraphMemory
    end

    subgraph "com.spectrayan.spector.memory.hebbian"
        HebbianGraphMemory
        CoActivationRecordMemory
    end

    subgraph "Builders (modified)"
        SpectorMemoryFactory
        CognitiveCortexBuilder
        CognitiveGraphBuilder
        BiologicalSubsystemsBuilder
        PartitionManager
        PersistenceManager
    end

    BundleLayout2 -.->|implements| MemoryLayout_IF
    PartitionBundle -->|uses| MemoryHeader
    RuntimeBundle -->|uses| MemoryHeader
    PartitionBundle -->|provides Arena + slices| AbstractCognitiveRecordMemory
    RuntimeBundle -->|provides Arena + slices| EntityDirectory
    RuntimeBundle -->|provides Arena + slices| HebbianGraphMemory
    BundleManager -->|orchestrates growth| RuntimeBundle
    SpectorMemoryFactory -->|creates| RuntimeBundle
    SpectorMemoryFactory -->|creates| PartitionBundle
```

---

## 6. Pros and Cons of the Options

| Alternative | Pros | Cons |
|:---|:---|:---|
| **Option 1: Individual Files (V3)** | Simple isolation | FD explosion, high TLB overhead, uncoordinated I/O |
| **Option 2: Container Archive** | Single file | No zero-copy random access memory mapping |
| **Option 3: Single-VMA Bundle (Selected)** | 1 FD, zero-copy slicing, unified flush/close | Requires internal region allocation table |

## 7. Implementation Plan

1. **Phase 1**: Define `RegionId`, `BundleHeader`, and `RegionAllocationTable` in `memory/spector-kernel/bundle`.
2. **Phase 2**: Implement `RuntimeBundle` and `PartitionBundle` memory segment slicers.
3. **Phase 3**: Refactor `AbstractRecordMemory` and `AbstractAppendMemory` to accept bundle slices.
4. **Phase 4**: Wire into `PersistenceManager` and validate with migration benchmarks.
5. **Phase 5**: Superseded by canonical `ADR-0004` (V4 Mmap Bundle Architecture and FD Scaling).

## 8. Code Reference & Verification

## Summary: Kernel Alignment Checklist

| Kernel Pattern | Bundle Alignment | Where |
|:---------------|:-----------------|:------|
| `MemoryHeader` (64B SMKM, magic `0x534D4B4D`) | ✅ Bundle file starts with standard SMKM header | `BundleDirectory.write()` calls `MemoryHeader.write()` |
| `MemoryHeader.isValid()` validation | ✅ `Init.open()` validates header on load | `RuntimeBundle.Init.open()`, `PartitionBundle.Init.open()` |
| `MemoryLayout` interface (`layoutId`, `stride`, `schemaVersion`) | ✅ `BundleLayout` implements it, `layoutId=0x42554E44` | `com.spectrayan.spector.memory.bundle.BundleLayout` |
| `MemoryShape` enum | ✅ `BUNDLE(6)` appended (ordinal-safe) | `com.spectrayan.spector.memory.kernel.MemoryShape` |
| Sub-header after 64B header | ✅ `BundleSubHeader` at offset 64 (like EntityDirectory's 16B sub-header) | `BundleSubHeader` at offset 64, 64 bytes |
| `VarHandle`-based field accessors | ✅ Sub-header and RegionEntry use VarHandles | `BundleSubHeader.VH_*`, `RegionEntry.read/write()` |
| `Init.heap()` / `Init.mmap()` factory pattern | ✅ Both bundles follow this pattern exactly | `PartitionBundle.Init`, `RuntimeBundle.Init` |
| Wrapping constructor `(Arena, MemorySegment, count, ...)` | ✅ `fromBundle()` factories delegate to wrapping constructor | Each store's `fromBundle()` static factory |
| `Arena.ofShared()` lifecycle | ✅ One shared arena per bundle; `bundleManaged` flag prevents stores from closing it | `AbstractMemory.close()` checks `bundleManaged` |
| `segment.force()` for flush | ✅ Stores flush their region slice; bundle flushes master | `AbstractMemory.flush()`, `RuntimeBundle.close()` |
| SWMR `VarHandle` visibility | ✅ Stores continue using `publishVisible()`; bundle directory uses VarHandle | Unchanged from V3 for store-level visibility |
| `StampedLock` for concurrent access | ✅ `RuntimeBundle.remapLock` for growth; stores keep their own locks | `RuntimeBundle`, `EntityDirectory`, etc. |
| Each region has its own store header | ✅ Region data starts with the store's own header (SMKM, HGPH, EDIR sub-header) | Region slices contain complete store data |
| `RegionEntry.layoutId` stores the store's layout | ✅ Enables `BundleInspector` to decode regions independently | `RegionEntry` record field |
| `PersistenceManager` flush/close ordering | ✅ V4 path: flush stores → flush bundle directory → close bundle | `PersistenceManager.flushAndClose()` V4 branch |
| CRC32C integrity | ✅ Bundle header has CRC32C; directory has xxHash64 | `MemoryHeader.write()`, `BundleSubHeader.dirChecksum` |

---

### Code Reference & Verification Gate

- **Primary Module(s)**: `memory/spector-kernel`
- **Key Packages**: `com.spectrayan.spector.kernel.bundle`, `com.spectrayan.spector.kernel.layout`
- **Classes**: `RuntimeBundle.java`, `PartitionBundle.java`, `RegionId.java`, `PersistenceManager.java`
- **Verification Tests**: `RuntimeBundleTest.java`, `PartitionBundleTest.java`
