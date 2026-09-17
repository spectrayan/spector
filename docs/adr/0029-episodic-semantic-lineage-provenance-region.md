# ADR-0029: Episodic→Semantic Lineage Provenance Region

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-09-03 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | ADR-0028 §D4 |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

### 1.1 What exists today

`EpisodicLogConsolidationRelay` already performs episodic→semantic consolidation. Verified against
`memory/spector-memory/src/main/java/com/spectrayan/spector/memory/pathway/reflect/relay/EpisodicLogConsolidationRelay.java`:

1. `transmit(ReflectSignal)` (L75) iterates `signal.partitionManager().snapshot()` and calls the private `processLogStore(logStore, signal)` (L96) for every handle in episodic-log mode. There is no `execute()` method.
2. `processLogStore` reads `logStore.unconsolidatedTurnOffsets()` (L97) and `logStore.readTurns(...)` (L99), then groups into `Map<Long, List<EpisodicRecord>> sessionTurns` keyed on the 64-bit `sessionId`, with a side `Map<EpisodicRecord, Long> turnToOffset`.
3. Per distilled fact it mints `String memoryId = "rem-log-" + TSID.generate();` (L131), embeds best-effort, and calls `signal.rememberPathway().ingestCognitiveWithHeader(memoryId, text, vector, MemoryType.SEMANTIC, allTags, MemorySource.REFLECTED, header)`.
4. It stamps two tags: `conversation-reflection` and `"session-" + Long.toHexString(sessionId)` (L152).
5. It then flips `FLAG_CONSOLIDATED` on each source turn via `logStore.markConsolidated(offset)` (L176–182).

The `session-<hex>` tag is the only surviving link from a fact to its origin. It is lossy (session granularity only, no turn identity), unidirectional in practice (tags are a Bloom-encoded set, not an index), and it pollutes the synaptic tag space that `CognitiveScorer` gates on.

`RegionId.PROVENANCE_LOG(26)` is declared in `kernel/bundle/RegionId.java:43` and has **zero references** in main sources. Because `isRuntimeRegion()` is `id >= 10`, region 26 is runtime-only — contradicting ADR-0028 §D4's claim that `PartitionBundle` also supports it.

### 1.2 Why not the existing strength region

`RegionId.STRENGTH(4)` (formerly `RegionId.AUDIT(4)`) is a live, mutable, fixed-96-byte slab written on the recall hot path
(`StrengthLayout.INSTANCE.writeLastRecallProfile(...)` in `RecallPathway`, `RecallPipeline`,
`LtpReconsolidationListener`; ring buffer via `ActRActivation`). It is 1:1 with cognitive record slots
and `PartitionBundle` allocates it with `growable = false`. Mixing variable-cardinality provenance edges
into it would contaminate the cache-line isolation that ADR-0028 §D1–D2 exist to establish.

### 1.3 Why ADR-0028 §D4 is superseded

ADR-0028 §D4 specified `ProvenanceEntry` as a variable-length record: 16B target TSID, 1B
`operation_code`, 8B `source_uri_hash`, 2B `parent_trace_count`, a variable array of `parent_trace_ids`,
plus a `lineage_diff` text/binary delta. Three problems:

1. **Wrong TSID width.** `kernel/id/TsidGenerator.java` is 64-bit (42-bit timestamp / 10-bit node / 12-bit sequence, `TSID_STRING_LENGTH = 13`). A 16B target id is twice what the identity scheme produces.
2. **Variable length defeats the integrity path.** `AbstractRecordMemory` gives CRC32C over `recordStride - 4` for free, gated on `layout.crcEnabled()`. Variable-length records forfeit it and require a bespoke framing + checksum protocol.
3. **Scope conflation.** `source_uri_hash` and `lineage_diff` belong to #171 (connector/document provenance, DTO metadata plane). #731 is a closed, tiny relation: conversation turns → consolidated facts.

This ADR takes the reserved slot and replaces the record.

---

## 2. Problem Statement

Relying on Bloom-filter synaptic tags (`session-<hex>`) as the sole link between consolidated semantic facts and episodic conversation turns is lossy, unidirectional, and pollutes the tag space consumed by `CognitiveScorer`. Furthermore, variable-length records (as proposed in ADR-0028 §D4) violate the fixed-stride invariant required for automatic hardware-accelerated CRC32C checksum verification in `AbstractRecordMemory`.

## 3. Decision Drivers

- **Zero-Allocation Fixed-Stride Layout**: Provenance records must use fixed 32-byte records matching `AbstractRecordMemory` contracts.
- **Hardware-Accelerated CRC32C**: Every provenance entry must carry an off-heap CRC32C checksum covering `recordStride - 4` bytes.
- **1-to-N Bidirectional Traceability**: Enable navigating from any semantic engram back to exact episodic conversation turns, and vice versa.
- **Cache Isolation**: Ensure provenance recording does not contaminate the L1/L2 cache residency of live `StrengthLayout` recall hot paths.

## 4. Considered Options

| Alternative | Why rejected |
|:---|:---|
| Extend `RegionId.STRENGTH(4)` (formerly `AUDIT`) | Mutable 96B recall-telemetry slab on the hot path, 1:1 with cognitive slots, `growable = false`. Adding variable-cardinality edges breaks ADR-0028 §D1–D2 cache isolation. |
| Session ID in `CognitiveRecordLayout` | Semantic memories are timeless by design; the header is a full cache line with no room, and it would put provenance in every SIMD scan. |
| Generic JSON/CBOR document region (`[len][collection][key][doc][crc]`) | Schema drift, parse cost on every index rebuild, and no integrity path. Lineage is a closed 64-byte relation. Inspectability is served by a JSON dump in `spector-inspect`. |
| Multi-collection NoSQL region in v1 | Platform feature with one consumer. Revisit only if a second consumer appears; the reusable part is the shape (record region + rebuilt index), not a collection API. |
| Growable region with a rebinding hook | No such hook exists; `AbstractMemory.segment`/`arena` are `final`; growth remaps the whole bundle and invalidates every other store's slices. Deferred to §6 as separate work. |
| Independently appended 16 MiB chunks with a private TOC | `BundleDirectory` / `RegionEntry` **is** the TOC. A parallel chunk table would fight `growRegion` and `compact`, both of which close the arena. |
| Variable-length packed offset array per row | Forfeits `AbstractRecordMemory`'s CRC32C and slot addressing. Multiple fixed rows express the same information. |
| RocksDB / SQLite sidecar | Violates the pure-Panama kernel constraint; adds a native dependency and a second durability protocol beside `MemoryWal`. |
| Persisted secondary indexes | 131k-row rebuild is sub-millisecond. Persistent radix trees are a separate project. |

---

## 5. Decision Outcome

Adopt **Option 3 (Fixed-32B Record in Dedicated Partition Region)**, superseding ADR-0028 §D4.

### D1: Placement and Naming — runtime bundle, `RegionId.PROVENANCE(26)`

The region is named **`PROVENANCE`** (renamed from `PROVENANCE_LOG` for clarity and single-responsibility alignment with `STRENGTH`, `CONTINUITY`, etc.). It lives on `RuntimeBundle`, not `PartitionBundle`.

Rationale:

- Source turns live in a partition's EPISODIC region; the derived fact may land in a **different, later** partition. A partition-local table cannot represent the edge.
- The primary query ("which session produced this memory?") is namespace-wide.
- `PartitionBundle` has `final` Arena/segment/directory fields, no `growRegion`, and rolls when full (`PartitionBundle.java` javadoc: regions are fixed-size, the system rolls to a new partition directory). Lineage must outlive a roll.
- Lineage volume tracks **consolidation rate**, not ingest rate. Pre-sizing it into every partition bundle would waste space proportional to partition count.

### D2: Shape — `AbstractRecordMemory`, not `AbstractAppendMemory`

`ProvenanceMemory extends AbstractRecordMemory<ProvenanceLayout>` with `crcEnabled() = true`.

`AbstractRecordMemory.write(recordId, bytes)` already computes CRC32C over the leading
`recordStride - 4` bytes and stores the checksum in the trailing 4 (`AbstractRecordMemory.java:85-95`),
verifying it on `read` and throwing `ErrorCode.RECORD_CRC_CORRUPTED` on mismatch. It also maintains
`count` as a high-water slot mark with `persistCount()`.

Append semantics are obtained by writing at slot `count` — no separate cursor protocol. This is the
decisive reason to drop the variable-length blob proposed during design review: a fixed stride buys
the integrity check, capacity accounting, and O(1) slot addressing that a hand-rolled append log
would have to reimplement.

Non-contiguous turn sets are represented as **multiple rows** (one row per contiguous run, or one row
per turn), not as a packed offset array. `fact_index` and `turn_count` already disambiguate rows
belonging to one batch.

### D3: Record layout — fixed 72 bytes

`ProvenanceLayout` (`layoutId = 0x50524F56` `'PROV'`, `schemaVersion = 1`, `recordStride = 72`,
`crcEnabled = true`). One row per `(sessionId, turn-run, targetMemory)` edge.

| Offset | Size | Type | Field | Purpose |
|:---|:---:|:---|:---|:---|
| `0` | 1B | `uint8` | `flags` | `LIVE`(0x01), `PARTIAL_RUN`(0x02), `TOMBSTONE`(0xFF) |
| `1` | 1B | `uint8` | `source_kind` | `EPISODIC_LOG = 1` |
| `2` | 1B | `uint8` | `target_kind` | `SEMANTIC = 1`, `PROCEDURAL = 2` |
| `3` | 1B | `uint8` | `prefix_kind` | Target ID prefix registry ordinal (see D5). **Mandatory.** |
| `4` | 2B | `uint16` | `pass_number` | 1-indexed consolidation pass counter (multi-pass session support) |
| `6` | 2B | `uint16` | `turn_count` | Turns covered by this row |
| `8` | 8B | `int64` | `session_id` | Matches episodic header `session_id` (8B @ offset 24) |
| `16` | 8B | `int64` | `target_tsid` | Raw 64-bit TSID of the consolidated fact |
| `24` | 8B | `int64` | `consolidated_at_ms` | Epoch ms of the consolidation pass |
| `32` | 4B | `int32` | `partition_seq` | Partition holding the source turns; matches `PartitionHandle.seq()` (an `int`) |
| `36` | 4B | `int32` | `first_seq` | First episodic `sequence_id` in the run (4B @ offset 4) |
| `40` | 4B | `int32` | `last_seq` | Last episodic `sequence_id` in the run |
| `44` | 4B | `uint32` | `first_offset_hint` | Region-relative byte offset of the first turn (**hint only**) |
| `48` | 4B | `uint32` | `last_offset_hint` | Region-relative byte offset of the last turn (**hint only**) |
| `52` | 1B | `uint8` | `fact_index` | Index of this fact within its consolidation batch (0-indexed) |
| `53` | 1B | `uint8` | `batch_fact_count` | Total facts in this consolidation batch |
| `54` | 2B | `uint16` | `content_hash_hi` | Upper 16 bits of fact text CRC32C (dedup / integrity check) |
| `56` | 12B | `bytes` | `_reserved` | Zero-filled; reserved for alignment and future extensions |
| `68` | 4B | `int32` | `crc32c` | Written/verified by `AbstractRecordMemory` |

The three `int64` fields sit at offsets 8, 16 and 24 — all naturally 8-byte aligned. The `_reserved`
block at 56 is 12 bytes. Schema evolution consumes `_reserved` and bumps `RegionEntry.schemaVersion`,
which already exists per region (`RegionEntry.java`, `OFF_SCHEMA_VERSION = 44`).

### D4: Identity is `(session_id, first_seq, last_seq)` — offsets are hints

Byte offsets **must not** be the durable identity, for two verified reasons:

1. Offsets are region-relative, so every partition restarts numbering at 0. An offset is only meaningful paired with `partition_seq`.
2. The codebase is already inconsistent about the `dataOffset()` base. `AbstractMemory.dataOffset()` is `persistent ? MemoryHeader.HEADER_BYTES (64) : 0`. `EpisodicLogMemory.appendTurn` and `unconsolidatedTurnOffsets` yield **relative** offsets while `EpisodicSessionIndex.rebuild` records **absolute** cursors. Heap-mode tests have `dataOffset() == 0`, which is why the skew is invisible today. Freezing raw offsets into an on-disk format would cement that ambiguity.

`sequence_id` is a 4-byte monotonic per-session turn counter in the episodic header
(`EpisodicFieldAccessor`, offset 4). It is independent of storage layout and survives partition rolls,
compaction, and bundle migration. It is therefore the durable key.

Offset hints are retained as a fast path for `explain()` and **must** be validated against
`session_id` / `sequence_id` after `readTurn`; on mismatch, fall back to a scan. 4 bytes bounds an
episodic region at 4 GiB, well above any configured partition size.

### D5: Target identity — 64-bit TSID plus mandatory prefix kind

`IndexRecordMemory` is String-keyed (`ConcurrentHashMap<String, MemoryLocation> locations`,
`ConcurrentHashMap<String, Integer> idToSlot`). A raw 64-bit TSID alone is therefore **not resolvable**
to a memory — `sessionLineage(sessionId)` would return longs that nothing can look up.

`TsidGenerator.decodeCrockford(String)` round-trips a 13-char TSID to its `long`, so the split is safe
provided the prefix is recoverable. `prefix_kind` is a small registry ordinal covering the prefixes in
use today (`rem-log-` from this relay, `rem-` from `ReflectDaemon`, and the `skillId` / `durableId`
forms), and reconstruction is `prefixFor(prefix_kind) + encodeCrockford(target_tsid)`.

The relay keeps minting the `"rem-log-"` String — no change to `ingestCognitiveWithHeader`'s key type.

Documented caveat: `TsidGenerator.autoNodeId()` derives the node id from PID ^ thread hash, so raw
64-bit uniqueness is best-effort. Acceptable for a CRC-protected lineage key; it must not be
documented as globally unique.

### D6: Non-growable in v1, generously pre-sized

The region is declared in `CognitiveCortexBuilder.getRuntimeBundleSpecs` with **`growable = false`** and
an initial allocation of 8 MiB (`MemoryHeader.HEADER_BYTES + 131_072 * 64`), configurable via
`spector.memory.provenance-log-size`.

This is a deliberate reversal of the design review's "growable region" instinct, because
`RuntimeBundle.growRegion()` (`RuntimeBundle.java:320`) is **not safe for a long-lived store**:

- It calls `arena.close()` and remaps the **entire file** into a fresh `Arena.ofShared()`. Every slice for every region is invalidated, not just the grown one — the class javadoc states this explicitly.
- `AbstractMemory` declares `protected final Arena arena` and `protected final MemorySegment segment` (L59-60). There is no setter and no re-slice path, so a store cannot survive its own region's growth.
- No rebinding contract exists. Greps for `rebind`, `onRemap`, `remapListener`, `refreshSegment` across `memory/`, `synapse/`, `nucleus/` return zero matches. `RuntimeBundle` holds no references to its stores and notifies nobody.
- `ContinuityRecordMemory` is not a precedent: it captures a `final` slice in `fromBundle`, is a fixed-capacity ring buffer, and is declared `growable = false`.
- `MemoryBM25Index.persistToBundle` is the only production grower, and it is safe only because it holds **no** segment — it fetches `regionSegment(BM25)`, grows on `saveToRegion() == -1`, re-fetches, and drops the segment inside one method, with authoritative state on-heap. That is a usage discipline in one method, not a contract.
- BM25 grows during index build and inside `doClose()`, when nothing else is touching slices. A region grown during a **sleep cycle** would remap the bundle out from under `ContinuityRecordMemory`, `CoActivationRecordMemory`, the Hebbian graph, and the entity directories while they hold live `final` slices.

At 64 B/row, 8 MiB is ~131,000 lineage edges. On exhaustion the store logs a warning and drops the
edge (the fact and the consolidated flags still commit); it does **not** attempt growth. Growth is
deferred to the follow-up work in §6.

Supporting observations recorded for completeness: `growRegion` never consults `FLAG_GROWABLE`
(`RegionEntry.isGrowable()` has no production caller); `BundleDirectory.withUpdatedRegion` replaces the
entry in place retaining `FLAG_LIVE` rather than marking the old one dead as its comments claim, so
vacated space is untracked and only heuristically inferred by `deadSpaceBytes()`; and
`BundleManager.needsGrowth()` is unreachable in production because both `persistToBundle` call sites
(`RetrievalIndexBuilder.java:107`, `DefaultSpectorMemory.java:1992`) pass `null` for the manager.

### D7: Indexes rebuilt on open, never persisted

Following `EpisodicSessionIndex` (heap `ConcurrentHashMap<Long, List<Long>>`, `rebuild(...)` via one
sequential scan, no save path):

- **Primary** — `target_tsid → slotId`
- **Reverse** — `session_id → slotId list`

Both are heap maps in v1, rebuilt by a single sequential scan over slots `[0, count)` on bundle open,
skipping `TOMBSTONE` rows. No persisted hash table, no radix tree. At 131k rows a full scan is
sub-millisecond and reading it costs one CRC verification per row.

`partition_seq → slots` is deferred until an eval workload needs it.

### D8: Zero cost on the recall path

- `CognitiveScorer`, tier scans, and all SIMD kernels never map `PROVENANCE_LOG`.
- No session identity enters `CognitiveRecordLayout` or `HeaderLayout64V2`. Semantic headers stay timeless (ADR-0028 §D1).
- Lineage is reachable only through explicit APIs (D9).
- The `session-<hex>` and `conversation-reflection` tags are retained for UI compatibility but are **no longer the source of truth** and must not be parsed for provenance.

### D9: API surface

Kernel (`spector-memory`):

```text
ProvenanceLayout                         // 72B stride, CRC32C, layoutId 'PROV'
ProvenanceState                          // record: decoded row
ProvenanceEdge                           // record: write payload

ProvenanceMemory extends AbstractRecordMemory<ProvenanceLayout>
  static ProvenanceMemory fromBundle(Arena, MemorySegment regionSlice, Path bundlePath)
  static ProvenanceMemory heap(int capacity)
  int    append(ProvenanceEdge edge)               // writes at slot `count`
  Optional<ProvenanceState> findByTarget(long tsid)
  List<ProvenanceState>     findBySession(long sessionId)
  List<ProvenanceState>     findBySessionAndPass(long sessionId, int passNumber)
  int    nextPassNumber(long sessionId)
  Stream<ProvenanceState>   replay()               // inspect / eval
```

Facade (`MemoryReflection` & `DefaultSpectorMemory`):

```text
SpectorMemory.explain(String memoryId) -> Optional<MemoryProvenance>
  { targetId, sessionId, partitionSeq, firstSeq, lastSeq, turnCount, passNumber, factIndex, batchFactCount, consolidatedAtMs }

SpectorMemory.sessionProvenance(long sessionId) -> List<MemoryProvenance>   // ordered by pass_number then fact_index
```

`explain()` resolving turn bodies routes through `partitionManager.snapshot()` to reach the
**correct** partition's `router.episodicLog()`. `DefaultSpectorMemory` holds only the active log
instance; frozen handles remain open in `PartitionManager`.

### D10: Write ordering and its prerequisites

Target ordering inside `processLogStore`, per session batch:

1. Distill facts; embed best-effort.
2. `ingestCognitiveWithHeader(...)` per fact.
3. **Confirm** the ingest landed (`boolean ingested = signal.rememberPathway().ingestCognitiveWithHeader(...)`).
4. `provenanceMemory.append(...)` per fact (only if ingest succeeded).
5. `markConsolidated(offset)` per source turn — only if ingest and provenance both succeeded.

Step 3 is resolved by changing `RememberPathway.ingestCognitiveWithHeader(...)` return type from `void` to `boolean`.
It returns `true` when the fact successfully ingests, and `false` on duplicate or failure.

### D11: Two defects to fix while wiring, not inherit

1. **`partition_seq` is in scope.** `processLogStore` accepts `PartitionHandle` and captures `handle.seq()`.
2. **`turnToOffset` collision eliminated.** Turns are mapped via `List<TurnWithOffset>` rather than `Map<EpisodicRecord, Long>`, preserving all offsets even for duplicate/empty turns.

### D12: Backward compatibility

- The region is read via `optionalRegionSegment(RegionId.PROVENANCE)` with a null guard, following the `CONTINUITY` precedent at `CognitiveCortexBuilder.java`.
- `ProvenanceMemory` is nullable throughout; a null store degrades gracefully.
- Configured via `spector.memory.provenance-capacity` (default `8,192`).

### D13: Multi-pass consolidation and `pass_number`

In long-running conversational sessions, an episodic session frequently receives new turns
*after* previous turns have already undergone sleep consolidation. The same session can therefore
have multiple distinct semantic memories generated at different points in time.

- `pass_number` (2 bytes, 1-indexed) in `ProvenanceLayout` distinguishes successive consolidation runs.
- `ProvenanceMemory.nextPassNumber(sessionId)` computes `max(existing pass numbers for sessionId) + 1` (starts at 1).
- `sessionProvenance(sessionId)` returns all lineage edges sorted by `passNumber ASC, factIndex ASC`.
- Each pass records its own `first_seq`, `last_seq`, `turn_count`, and `consolidated_at_ms`.

### D14: System-wide IdStrategy and MemoryIdGenerator

Memory IDs must be consistent across the entire system rather than relying on disparate ad-hoc generators.
- Removed private static `TsidGenerator TSID` from `EpisodicLogConsolidationRelay`.
- `ReflectSignal` now passes the system-configured `MemoryIdGenerator` (`signal.idGenerator()`).
- `TsidGenerator.decodeCrockford(memoryId)` decodes string IDs to raw 64-bit TSIDs for compact binary storage in `target_tsid`.
- `TsidGenerator.encodeCrockford(targetTsid)` provides inverse mapping for reverse lookup queries.

### D15: Six discovered consolidation bugs resolved

During the provenance redesign, six pre-existing defects in the consolidation pipeline were surfaced and fixed:
1. **`EpisodicSessionIndex.rebuild()` offset corruption**: Stored absolute offsets instead of region-relative offsets (`cursor - dataOffset`).
2. **`turnToOffset` collision in consolidation relay**: Keyed on `EpisodeRecord` value object; identical empty turns collided and dropped offsets.
3. **Discarded affect metadata**: Fact valence, arousal, and importance were previously ignored and defaulted to 0.
4. **Missing session tag**: Fact tags previously omitted the source session link.
5. **Duplicate facts on retry**: Turns were marked consolidated *after* fact ingestion; failure in mark left facts committed while turns remained unconsolidated.
6. **Stale `partition_seq`**: `processLogStore` did not receive `PartitionHandle.seq()`.

---

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: ADR-0028 §D4 Variable** | Rich flexible metadata | Defeats CRC32C integrity, wrong TSID width, high memory fragmentation |
| **Option 2: Bloom Synaptic Tags** | Zero new regions | Lossy, unidirectional, pollutes recall tag space |
| **Option 3: Fixed-32B Mmap Region** | Hardware CRC32C, exact 64-bit TSID, zero cache pollution | Requires managing dedicated growable region per partition |

## 7. Implementation Plan

All phases of ADR-0029 implementation completed in [spectrayan/spector#731](https://github.com/spectrayan/spector/issues/731):

1. `ProvenanceLayout` + `ProvenanceEdge` + `ProvenanceState` + heap-backed and bundle-backed `ProvenanceMemory` implemented with CRC32C integrity and slot indexing.
2. `RegionId.PROVENANCE(26)` registered in `CognitiveCortexBuilder.getRuntimeBundleSpecs`, wired with `fromBundle` and heap fallback.
3. `RememberPathway.ingestCognitiveWithHeader(...)` updated from `void` to `boolean` return.
4. `EpisodicLogConsolidationRelay` fully integrated with transactional write ordering, multi-pass pass number assignment, and system-level `IdStrategy`.
5. Public facade methods `explain(memoryId)` and `sessionProvenance(sessionId)` added to `MemoryReflection` and `DefaultSpectorMemory`.
6. MkDocs deep-dive documentation published at `docs/docs/deep-dives/provenance-region.md`.
7. All 1,687 unit, property, and integration tests passing.

## 6. Follow-up work this ADR does not cover

- **`RuntimeBundle` remap safety** — `growRegion()` and `compact()` both close the arena and invalidate every slice, while stores hold `final` segments. A rebind/invalidate protocol is a prerequisite for **any** growable region backing a long-lived store, and warrants its own issue independent of #731.
- **`growRegion` ignoring `FLAG_GROWABLE`**, and `withUpdatedRegion` not marking superseded entries dead.
- **Issue #171** — connector/source-URI provenance on the DTO metadata plane.
- **ADR-0028 §D4 correction** — its claim that `PartitionBundle` supports region 26 is false; `isPartitionRegion()` is `id < 10`.

*(Note: The `dataOffset()` skew between `EpisodicSessionIndex.rebuild` and `EpisodicLogMemory` was fixed as Bug #1 in #731).*

## 8. Code Reference & Verification

### Positive

- Bidirectional, turn-level provenance replacing a lossy session tag, with an explicit `explain()` contract.
- Zero recall-path cost: no new region mapped by scoring, no header field added, no tag-space growth.
- CRC32C integrity and capacity accounting inherited from `AbstractRecordMemory` rather than reimplemented.
- Identity survives partition rolls, compaction, and bundle migration because it is `(sessionId, seq)`, not a byte offset.
- Supplies the idempotency key that eliminates duplicate `rem-log-` facts on consolidation retry.
- MindSpan-style IR evaluation can map retrieved semantic IDs to gold session IDs without parsing tag strings.
- Avoids invoking `RuntimeBundle.growRegion()` from a live store, sidestepping a whole-bundle remap hazard.

### Negative / trade-offs

- **Fixed ceiling.** 8 MiB ≈ 131k edges. Exhaustion silently drops audit rows. Accepted for v1; the remap-safety work in §6 lifts it.
- **Blocked on an API change.** D10 step 3 requires a success signal from `ingestCognitiveWithHeader`.
- **Two heap indexes.** Rebuild is O(rows) on open and the maps are on-heap, counter to the off-heap-everything preference. Matches `EpisodicSessionIndex` precedent; revisit only under measurement.
- **Prefix registry is a coupling point.** Adding a new memory-ID prefix requires a registry ordinal.
- **`explain()` needs partition plumbing** through `PartitionManager` to read turn bodies.
- **Frozen-partition writes remain unaddressed.** `transmit` iterates all handles including frozen ones and calls `markConsolidated` on them. Pre-existing; noted, not fixed here.

### Neutral

- ADR-0028 §D4's `source_uri_hash` / `lineage_diff` ambitions move to #171 on the DTO metadata plane.
- `RegionId.LOOKUP` is sized `new RegionId[27]`, which already accommodates id 26. No enum change needed.

---

---

### Code Reference & Verification Gate
- **Primary Module(s)**: `memory/spector-memory`, `memory/spector-kernel`
- **Key Packages**: `com.spectrayan.spector.memory.pathway.reflect.relay`, `com.spectrayan.spector.kernel.bundle`
- **Classes**: `EpisodicLogConsolidationRelay.java`, `RegionId.java`, `PartitionBundle.java`
- **Verification Tests**: `EpisodicLogConsolidationRelayTest.java`
