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

- **Status:** Accepted (design ruling — implementation pending)
- **Author:** Architecture Working Group (Systems Architecture)
- **Target repo:** `spectrayan/spector`
- **Module:** `memory/spector-memory`
- **Branch:** `fix/issue-443-partition-recall-fanout` (cut from `main` @ `9413de7`)
- **Related:** epic #431 (kernel tech-debt), #436 (PartitionManager rename), diagnosis by @forge
- **Decision date:** 2026-08-02

---

## Context

DISK-mode cognitive memory rolls to a new colocated partition directory
(`partitions/NNN_epoch/{episodic,semantic,procedural}.mem` + `text.dat`) when a
tier store hits capacity. @forge's read-only trace (ground truth) shows recall
**never spans more than one partition**. Verified against source:

1. **Arena/mmap leak + router staleness.** `PartitionManager.rollPartition()`
   builds fresh tier stores in the new dir, swaps the `volatile cognitiveRouter`,
   and updates only `CognitiveIngestionTarget`. The old (frozen) stores lose
   every reachable reference — never registered, never closed (arena/mmap leak) —
   and `RecallPipeline` holds a `final CognitiveMemoryRouter` set at construction
   with no update path. `buildScanTasks`/`sequentialScan` scan only that one
   router's stores.
2. **Restart darkness.** `PartitionManager.discoverOrCreatePartition` returns only
   the newest partition dir; the factory opens exactly that one. Older partition
   dirs are never mapped on load.
3. **Reverse-key collision.** `IndexRecordMemory.reverseKey = (type.ordinal()<<48)
   | offset` has **no partition dimension**. Offsets restart at 0 in each new
   partition's mmap file, so two records in different partitions collide on the
   same key in the in-memory `reverseIndex` (last writer wins → wrong id/text).
4. **Direct-resolve is active-only.** `inspect`/`forget`/`browse`/`reinforce`/
   `markResolved`/`markUnresolved`/`whyNot`, plus `RecallPipeline` id-hydration,
   profile-ordinal writeback and the LTP listener, all resolve via
   `partitionManager.cognitiveRouter().segmentFor(type)`/`.layoutFor(type)` —
   the **active** router only, ignoring which partition the memory lives in.
5. **IN_MEMORY never rolls** (`rollPartition` no-ops when `basePath == null`), so
   the bug is DISK-only.

### Two facts the diagnosis under-stated (verified, and they change the fix size)

- **`MemoryLocation.partitionIndex` is a misnamed, already-occupied field.**
  `PostIngestSync` writes it as `storeIndex = countFor(SEMANTIC) - 1` (the
  **semantic HNSW / Hebbian graph node slot**), and `-1` for non-semantic tiers.
  It is read as a graph slot by `ReinforcementHandler` (degree centrality),
  `PostIngestSync` (Hebbian/temporal node index), and exported in
  `CognitiveRecord` JSON. It is persisted in the `.midx` slot at byte `[24:4]`.
  **It does not, and never did, identify the colocated partition.** There is
  therefore **no persisted memory→partition mapping anywhere today.**

- **`SemanticRecordMemory` stores full records (header + quantized vector),** same
  `CognitiveRecordLayout` stride as episodic. The separate `SemanticRecallStrategy`
  + HNSW exists purely as an O(log N) speed path; the slab itself is fully
  scorable by `CognitiveScorer` (similarity included). This means **frozen
  semantic partitions can be scored by the SIMD scorer directly** — a
  multi-partition HNSW is *not* required for correctness.

### Consequence for scope

Because no per-memory partition identity is persisted, **restart-correct recall
cannot be achieved without a schema change** to record the colocated partition
per memory. This makes #443 **larger than a hotfix**; it must be phased. The
in-process leak and in-process fan-out can land first (Phase 1) without a format
change; restart correctness (Phase 2) requires `.midx` format v6.

---

## Decisions

### D1 — Partition retention model

Introduce a **partition registry** owned by `PartitionManager`. A partition is
represented by an immutable handle:

```
record PartitionHandle(int seq, Path dir, CognitiveMemoryRouter router,
                       TextAppendMemory text, boolean writable) implements AutoCloseable
```

- The registry is a **`volatile List<PartitionHandle>` snapshot** (immutable list,
  replaced by reference-assignment — copy-on-write semantics). Ordered by `seq`.
  The last element is the single **writable/active** handle; all earlier handles
  are **frozen, read-only, immutable**.
- **On roll:** create the new dir + fresh stores + fresh `text.dat`; mark the
  current active handle frozen (keep it **open, read-only** — this is what fixes
  the leak); build a new snapshot list = `frozen… + newlyFrozen + newActive`;
  publish it with one volatile store; then point the ingestion target at the new
  active router **and** the new text store and the new active `seq`.
- **On load/restart:** enumerate **all** partition dirs (not just newest), open
  each read-only, newest = active/writable. `discoverOrCreatePartition` is
  generalized to `discoverAllPartitions` (still creates `000` when none exist).
- **Lifecycle / close:** frozen handles are closed **only at component `close()`**
  (simplest correct rule), except when consolidation/compaction *deletes* a
  partition, which must `deregister → close` that handle. Working memory stays
  global (unchanged) and is not part of any handle.

**Scale note (FD / memory).** mmap segments are page-cache backed — RAM cost is
lazy, not `N × fileSize`. The real costs are (a) one `Arena` + up-to-3 mappings
per partition (address space + a transient FD during `map`), and (b) recall
latency growing **linearly with partition count** because fan-out scans every
partition. Both are acceptable for a correctness fix at expected partition counts
(tens). Partition pruning (skip by time-range/summary header) and per-partition
HNSW are recorded as **follow-ups**, not blockers.

### D2 — Read fan-out contract (two distinct paths)

Use **both** options from the brief, for different operations:

- **Recall scoring → fan-out (iterate all partitions).** `RecallPipeline`
  builds scan tasks over **every handle** in the snapshot, per tier:
  - Working: global, scanned once.
  - Episodic / Procedural / **Semantic-of-frozen-partitions**: one
    `CognitiveScorer.score(segment,…)` task per partition segment (disjoint →
    zero contention, matches today's per-partition task model). Semantic frozen
    partitions are scored on their slab (vectors present — see Context).
  - Semantic of the **active** partition: keep the HNSW `SemanticRecallStrategy`
    fast path.
- **Direct-resolve → keyed lookup by partition.** All point lookups resolve the
  handle via `partitionManager.handleFor(loc.colocatedPartition())` then call
  `handle.router().segmentFor(type)`/`.layoutFor(type)`.

**`CognitiveMemoryRouter` does NOT gain a `partitionIndex` parameter.** The router
stays a single-partition value object (one per handle). Partition awareness lives
in `PartitionManager` (the registry) and in `MemoryLocation.colocatedPartition`.
This preserves the router's DIP/OCP design and keeps the change localized.

`PartitionManager` exposes:

```
List<PartitionHandle> snapshot();            // volatile read — for fan-out
PartitionHandle handleFor(int seq);          // O(1) — for direct resolve
CognitiveMemoryRouter activeRouter();         // for writes (replaces cognitiveRouter())
```

**Exact call sites to make partition-aware** (all currently active-router-only):

| File | Method(s) | Line(s) (at 9413de7) |
|---|---|---|
| `DefaultSpectorMemory` | `forget` | 697–704 |
| `DefaultSpectorMemory` | `markResolved` / `markUnresolved` | 815–832 |
| `DefaultSpectorMemory` | `whyNot` | 854–862 |
| `DefaultSpectorMemory` | `inspect` | 924–962 |
| `DefaultSpectorMemory` | `browse` | 981–1001 |
| `DefaultSpectorMemory` | `totalMemories` / `memoryCount` | 1051–1054 (aggregate over snapshot) |
| `RecallPipeline` | BM25-only hydration in `fuseBM25Candidates` | `segmentFor(loc.type())` |
| `RecallPipeline` | `writeProfileOrdinalToResults` | `segmentFor(loc.type())` |
| `LtpReconsolidationListener` | header write-back on recall | via `cognitiveRouter` |
| `ReinforcementHandler` | header reads | via router (graph-slot use of `partitionIndex()` is unaffected — see D3) |
| `GraphExpansionStage` | any `segmentFor` on expanded ids | via router |
| `MemoryIndex.text(id)` | per-partition text store resolution | see D3b |

`reflect()` / `consolidate()` receive the active router today; keep that for #443
(see D5).

### D3 — Reverse-index key & the partition dimension (schema change)

- **Reverse key must include the colocated partition.** New in-memory key:
  `reverseKey(partition, type, offset)`. This is a `ConcurrentHashMap` derived at
  `register()`/load — **in-memory only**, low risk *by itself*.
- **But the partition must be known and persisted per memory.** Because the
  existing `partitionIndex` field is occupied by the graph slot, **do not
  overload it**. Instead:
  - Add a dedicated `int colocatedPartition` to `MemoryLocation`, and **rename the
    existing field to `graphSlot`** (semantic/graph node index) to end the
    misnomer. `MemoryLocation` becomes
    `(type, offset, colocatedPartition, graphSlot, textOffset, textLength)`.
  - **`.midx` slot format → v6:** append a 4-byte `colocatedPartition` (stride
    `40 → 44`), bump the format version, keep reading `graphSlot` at `[24:4]`.
    **This is an on-disk format change** → mandate: **golden-file round-trip test,
    explicit version gate, and throw-on-unreadable** for the new version. **V5
    files load with `colocatedPartition = 0`** (correct for genuinely single-
    partition stores) with a one-line WARN; pre-existing multi-partition data is
    already corrupt and is not recoverable — document this, do not pretend to heal
    it.
  - Propagate the active partition seq into ingestion: `rollPartition` must update
    the ingestion target's active partition seq (today hardcoded `0` and never
    updated), and `PostIngestSync` must stamp `colocatedPartition` on the
    `MemoryLocation` it registers.

**D3b — text.dat is also partition-scoped and must be reconciled.** Today
`text.dat` never rolls (the ingest target keeps partition 0's `TextAppendMemory`),
so all text accretes in partition 0 while `.mem` rolls — and on restart the
factory opens only the newest partition's (empty) `text.dat`, so text is
unreadable. Ruling: **roll `text.dat` with the partition**, register each
partition's text store in its handle, and make `MemoryIndex.text(id)` resolve the
text store via `loc.colocatedPartition()` (inject a
`IntFunction<TextAppendMemory>` resolver backed by the registry, replacing the
single `setTextDataStore`). This is required for recall to return correct **text**
across partitions, not just correct headers.

### D4 — Concurrency model

- **Frozen handles are immutable and read-only → no lock required to read them.**
- **Safe publication of a newly-frozen partition:** the registry is a single
  `volatile` reference to an **immutable** snapshot list. A reader takes **one
  volatile read at scan start** and iterates that fixed snapshot — it can never
  observe a half-registered partition, and cannot NPE on a store mid-construction
  (the new active store is fully built *before* the snapshot that contains it is
  published). Writers/rolls continue to hold the existing `partitionRollLock` for
  the create-then-publish sequence.
- **The active (mutating) store** keeps its current SWMR discipline
  (`visibleCount()` acquire-fence) — unchanged. A reader scanning the active
  partition concurrently with a writer sees a consistent visible prefix exactly as
  today.
- **CopyOnWriteArrayList vs volatile-immutable-list:** prefer the
  volatile-immutable-list (one allocation per roll, zero per read; rolls are rare).
  Equivalent correctness, lower read overhead, zero hot-path allocation.

### D4b — RecallPipeline router staleness

**Yes — `RecallPipeline` must stop holding a `final CognitiveMemoryRouter`.** It
holds a reference to `PartitionManager` (or a `Supplier<List<PartitionHandle>>`)
and takes a fresh `snapshot()` at the **start of each `recall()`**. `buildScanTasks`
/`sequentialScan` iterate that snapshot. `GraphExpansionStage` and the direct
`segmentFor` sites in the pipeline resolve handles the same way (by
`loc.colocatedPartition()` for point reads; by snapshot for scans). The
constructor `cognitiveRouter` parameter is replaced by the manager/supplier;
update the factory wiring accordingly.

### D5 — Scope guard

**IN scope for #443 (the minimal correct fix that makes RECALL + direct-resolve
complete, in-process and across restart):**

- Partition registry + retain-frozen-open (leak fix) + open-all-on-load (D1).
- Fan-out recall across all partitions for working/episodic/procedural/semantic,
  active-semantic via HNSW, frozen-semantic via SIMD slab scan (D2).
- `MemoryLocation.colocatedPartition` + partition-aware `reverseKey` + `.midx` v6
  + propagate active seq to ingest (D3).
- `text.dat` rolls + per-partition text resolution (D3b).
- All direct-resolve call sites in the D2 table.
- `RecallPipeline` consults the live registry (D4b).

**DEFERRED (explicit follow-up issues):**

- **Per-partition / multi-partition native HNSW.** For #443, the active partition
  keeps HNSW and frozen semantic partitions are scored via the (correct but O(N))
  SIMD slab scan. A per-partition HNSW merged at query time, or a global HNSW
  whose slot→(partition,offset) map spans partitions, is a **perf follow-up**.
- **`ConsolidationService` / `ReflectDaemon` / `ReflectionOrchestrator`**
  spanning frozen partitions. These promote episodic→semantic and decay graphs;
  recall correctness does not depend on them. They continue to operate on the
  **active** partition for #443. Follow-up: iterate sealed/frozen partitions for
  consolidation. (`IndexRecordMemory.textsByPartition` is currently keyed on the
  misnamed field and should be re-pointed at `colocatedPartition` as part of that
  follow-up.)
- **`CheckpointDaemon`.** Keep it flushing the **active** partition only; frozen
  stores are immutable and were force-flushed at roll — ensure the daemon never
  calls `force()`/writes on a read-only frozen handle.
- **Partition pruning** at query time (time-range/summary gating) to bound
  fan-out latency.

### D6 — Regression test plan (for @forge / @sentinel)

Smallest set that proves the fix and guards the collision:

1. **In-process roll, episodic:** ingest to force ≥2 partitions
   (drive `episodicPartitionCapacity` to trigger `rollPartition`), then `recall`
   a query matching a **pre-roll** record → asserts it is returned. (Fails today.)
2. **In-process roll, semantic:** same, targeting the semantic tier, to prove
   frozen-semantic slab scan returns pre-roll semantic records with a
   similarity-bearing score (not importance-only).
3. **Restart across ≥2 partitions:** build a store with ≥2 partition dirs, close,
   re-open via the factory, `recall` records from **both** the oldest and newest
   partition → both returned with correct **text** (guards D3b).
4. **Reverse-key collision:** place two records in different partitions at the
   **same physical offset** (natural when both partitions start empty), `inspect`
   each by id → asserts each returns its **own** text/header (guards D3 key).
5. **Direct-resolve across partitions:** `inspect` / `forget` / `browse` /
   `reinforce` / `markResolved` a record in a **frozen** partition → asserts the
   op hits the correct frozen store (e.g. `forget` tombstones the right record;
   post-`forget` recall excludes it).
6. **Leak / lifecycle:** after N in-process rolls, assert frozen handles remain
   open (readable) and are all closed exactly once at component `close()` (no
   double-close, no leaked arena). Mirror/extend the existing `PartitionManagerTest`
   concurrency case (pre-roll reader stays consistent through many rolls).
7. **Format v6 golden file:** round-trip `.midx` v6 save/load; a v5 golden file
   loads with `colocatedPartition = 0`; a corrupted/newer-than-known version
   **throws** (throw-on-unreadable).

### D7 — Sequencing & risk (for @forge)

Land as **one branch, two internally-sequenced phases** (Phase 2 can split to a
follow-up issue **only** with the explicit caveat that restart correctness stays
broken until it ships):

- **Phase 1 — in-process correctness + leak (no on-disk change).**
  Registry + retain-frozen-open; `RecallPipeline` consults live snapshot; fan-out
  scans; in-memory partition-aware `reverseKey`; propagate active seq to ingest;
  `text.dat` rolls + per-partition resolution; direct-resolve keyed by partition.
  Risk: **medium** — touches the recall hot path and the ingest→roll handshake.
  Guard with tests 1, 2, 5, 6.
- **Phase 2 — restart correctness (`.midx` v6, schema change).**
  Add `colocatedPartition` to `MemoryLocation` + slot v6; `discoverAllPartitions`
  opens every dir on load; rename `partitionIndex → graphSlot`.
  Risk: **high** — on-disk format. Guard with tests 3, 4, 7 and the golden-file +
  throw-on-unreadable discipline. Coordinate with any downstream `.midx` readers.

**Top risks:** (1) the ingest→roll handshake now updates router **and** text store
**and** active seq atomically — a missed field reintroduces split-brain; (2)
fan-out latency scales with partition count (accepted; pruning is follow-up);
(3) format v6 must not silently mis-read v5 multi-partition data (WARN + document
that such pre-fix data is unrecoverable). Java constraints hold throughout: no
`synchronized` (use the existing `ReentrantLock` for rolls + volatile snapshot),
`AutoCloseable` on every handle, zero hot-path allocation in the scan loop.

---

## Consequences

- Recall becomes correct across partitions both in-process and after restart;
  the arena/mmap leak is closed.
- Recall latency grows linearly with partition count until pruning/HNSW-per-
  partition follow-ups land — an accepted, documented trade-off.
- One on-disk format bump (`.midx` v6), gated and golden-file-tested; pre-fix
  multi-partition data remains corrupt and is explicitly not auto-healed.
- The long-standing `partitionIndex` misnomer is retired in favour of an explicit
  `graphSlot` + `colocatedPartition`, removing a latent trap for future work.

---

## Addendum — Phase 2 `.midx` format decision (CEO-approved 2026-08-02)

The `.midx` file already carries the standard **64-byte kernel `MemoryHeader`** (via `DefaultRecordMemory<IndexEntryLayout>`); only the **per-record slot** changes. Decision:

- **Slot stride 40 → 48** (8-byte aligned): append `colocatedPartition` int at `[40:4]` + `reserved` int at `[44:4]`. Not 44 (unaligned) and not 64 (wasteful for a non-hot slot at 1M entries).
- **Rename `[24:4]` `partitionIndex` → `graphSlot`** (same byte position; it is the semantic-HNSW / Hebbian node slot, never the colocated partition — behaviour unchanged, name only).
- **`IndexEntryLayout` → schemaVersion 6**, `recordStride()` = 48. The header records both, so the loader branches on `MemoryHeader.readSchemaVersion`.
- **Loader:** v6 → read 48-byte slot incl. `colocatedPartition`; **v5 → read 40-byte slot, `colocatedPartition = 0` + one WARN**; unknown/newer → throw `SpectorStorageException`. Golden-file round-trip (v6) + v5-loads-clean + throw-on-unreadable tests.
- **De-hardcode** the `40` literals in `IndexRecordMemory.save` (`entryCount*40`, `new byte[40]`) to `layout.recordStride()`.
- **`discoverAllPartitions`:** open ALL partition dirs on load (newest = writable, rest frozen read-only); resolve each index entry to its partition via the persisted `colocatedPartition` (v6). v5 multi-partition data remains unrecoverable (documented; not auto-healed).
- Reverse key gains a partition dimension (in-memory only).
