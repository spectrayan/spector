---
title: "Offline Consolidation"
description: "How Spector consolidates episodic memories into semantic knowledge during 'sleep' — K-Means clustering, tombstone compaction, and partition rebuild."
---

# Offline Consolidation

---

## The Two Mechanisms

### 1. Offline Consolidation — Episodic → Semantic Promotion

The consolidation daemon performs K-Means clustering on episodic memories to extract semantic knowledge:

```mermaid
sequenceDiagram
    participant RD as Consolidation Daemon
    participant EP as Episodic Store
    participant SE as Semantic Store
    participant HG as Hebbian Graph
    participant EG as Entity Graph

    Note over RD: Circadian trigger (configurable interval)
    RD->>EP: Get sealed partitions

    loop Each sealed partition
        RD->>EP: Read all records
        Note over RD: K-Means clustering on header features
        RD->>RD: Cluster by (tag overlap, importance)

        loop Each cluster (size ≥ threshold)
            Note over RD: Compute centroid header
            RD->>RD: Tags = AND across cluster (common themes)
            RD->>RD: Importance = average, Valence = max
            RD->>SE: Write consolidated semantic record
        end

        RD->>HG: Decay edges (0.9× factor)
        RD->>EG: Decay relations + merge similar entities
        RD->>EP: Mark partition as reflectable
    end
```

**Key behaviors**:

- **Tag merging**: Uses bitwise AND across the cluster — only common tags survive, representing the shared theme
- **Importance averaging**: The consolidated memory inherits the mean importance of its source episodes
- **Minimum cluster size**: Small clusters (noise) are not promoted — only patterns are
- **Cross-layer promotion**: Strong Hebbian edges are promoted to Entity Graph relations
- **Entity maintenance**: Similar entities are merged (Levenshtein distance), stale relations decay

!!! example "Example: Consolidation in Action"
    An agent encounters 15 episodic memories tagged `[database, connection, error]` over a week. The consolidation daemon clusters them and promotes a single semantic memory: *"Database connection issues are recurring — check connection pool sizing and timeout settings."*

---

### 2. Tombstone Compaction — Synaptic Pruning

When records are deleted via `forget()` or `purge()`, tombstones (`FLAG_TOMBSTONE`) are marked in the record header, allowing scorer Phase 1 to skip them in ~1 cycle. However, tombstoned records continue to consume disk and slab capacity until **compaction** reclaims the physical space.

Spector performs physical compaction via `VacuumCompactor`, which can be triggered on demand (`SpectorMemoryAdmin.vacuum(tier, force)` / `POST /api/v1/memory/vacuum`) or during background maintenance when the tombstone ratio exceeds the configured threshold.

```mermaid
flowchart LR
    A["Partition Slab<br/>1000 records<br/>400 tombstoned"] -->|"VacuumCompactor.compact()"| B["Compacted Slab<br/>600 dense records<br/>0 tombstones"]
    A -->|"Physical Delta"| C["Dead Slots Zeroed<br/>Space Reclaimed"]

    style A fill:#e74c3c,color:white
    style B fill:#2ecc71,color:white
    style C fill:#27ae60,color:white
```

**The Compaction Process**:

1. **Census & Threshold Evaluation**:
   The store's tombstone ratio is evaluated against `spector.memory.vacuum.threshold` (default `0.20`). If the ratio is below threshold and `force=false`, a census report (`CompactionResult.census`) is returned without moving records.

2. **Dense In-Place Relocation**:
   Live records are copied sequentially to lower offsets within each partition slab (`compactFixed` for fixed-stride semantic/procedural engrams, `compactEpisodic` for variable-length append logs). Abandoned trailing slots are zeroed out in-place.

3. **Atomic Counter Updates**:
   Header prologues (`visibleCount` and `usedBytes`) are atomically updated and published to readers via Panama MemorySegment stores.

4. **Index Offset Remapping Under Lock**:
   Under `PartitionManager.withRollLock` (the same lock coordinating partition roll), `IndexEntryMemory` updates record locations to their new offsets, guaranteeing continuous ID-to-record resolution across concurrent reads.

5. **Graph Edge Reconciliation**:
   Surviving records maintain stable `graphSlot` mappings, keeping Hebbian associations, temporal chains, and hyperedges intact. Dead records have their `graphSlot`s detached across all four cognitive graph planes (Hebbian CSR, temporal chain, entity directory, hypergraph).

6. **Derived Index Reconciliation**:
   Derived indexes (HNSW, BM25, SPLADE) are re-synchronized via `IndexReconcileEngine.reconcile()`.

7. **Measured Space Reclamation**:
   Reclaimed space is measured directly from the physical difference (`beforeUsedBytes - afterUsedBytes`) rather than computed via an unverified multiplication.

### 3. Deletion Semantics: `forget` vs `purge`

Spector provides two distinct deletion verbs with documented semantics:

| Property | `forget` (Logical Tombstone) | `purge` (Physical Destruction) |
|:---|:---|:---|
| **Mechanism** | Sets `FLAG_TOMBSTONE` (bit 0 of flags byte) | Overwrites payload and content headers with zeros in-place; sets `FLAG_PURGED` (`0x40`) |
| **Payload on Disk** | **Retained verbatim** in partition mmap slab and snapshots | **Destroyed** (zeroed off-heap vector, norm, Bloom filter, centroid ID, turn body, unshared text) |
| **Graph Edges** | Filtered during traversal | **Detached** across all 4 planes: Hebbian CSR rebuild, temporal chain unlink, entity directory unlink, and hyperedges scan |
| **WAL Event** | `RECORD_WRITE` tombstone bit update | `PURGE` opcode recorded before zeroing, re-applied during recovery |
| **Legal Hold** | **Permitted** (payload survives for discovery) | **Refused** (`NamespaceLegalHoldException` / HTTP 409) |
| **Export Behavior** | Omitted unless `--include-tombstones` is passed | Omitted unconditionally |
| **Audit Report** | Status confirmation | Returns `PurgeResult` disclosing unreachable copies (DR exports, replica disks, cold tier) |
| **Space Reclaim** | Reclaimed only when partition compaction runs | Space retained in-place (reclaimed only upon compaction) |
| **Reversibility** | Reversible in principle | **Irreversible** |

When memories are `forget()`'d, they are tombstoned (bit 0 of flags byte set to 1). The scorer skips them in Phase 1 (~1 cycle). When records must be permanently destroyed for privacy or compliance (e.g. GDPR erasure), `purge()` must be used. In either case, `vacuum()` subsequently compacts the partition to physically reclaim disk space.

#### 3.1 Unreachable Copies Disclosure (`PurgeResult.UNREACHABLE_COPIES`)

A `purge()` physically overwrites bytes in the active local memory store. However, an honest erasure report must acknowledge the operational boundaries of the engine. Spector's `PurgeResult.unreachableCopies` explicitly enumerates the six storage domains outside the scope of a local memory purge:

| Unreachable Copy Domain | Explanation & Operational Boundary |
|:---|:---|
| `dr_exports` | Offline archive tarballs and disaster recovery bundles generated prior to the purge. Purging the active store cannot mutate external archive media. |
| `replica_disks` | Passive or unlinked physical replica disks not participating in synchronous WAL replication during the purge window. |
| `cold_tier_objects` | Read-only object-store blobs (e.g. S3 / GCS tiers) containing archived historical partitions. |
| `filesystem_snapshots_and_backups` | Point-in-time filesystem or volume snapshots (e.g. ZFS, EBS, LVM) created before the purge executed. |
| `wal_segments_prior_to_the_last_checkpoint` | Rotated or archived WAL log segments written prior to the latest checkpoint. Active replay is bounded, but raw byte remnants may persist on disk until WAL log reaping. |
| `os_page_cache_and_ssd_wear_levelling_remnants` | Unflushed OS dirty pages or physical NAND flash blocks preserved by SSD wear-leveling algorithms prior to block trimming. |

Every purge audit report includes `PurgeResult.DISCLOSURE_TEXT`, ensuring downstream compliance tooling is explicitly notified that external archives and snapshots require separate lifecycle management.

#### 3.2 Content-Addressable Dedup Text Retention (`hasLocalRetention`)

Spector employs content-addressable storage deduplication for record text bodies in `text.dat`. When multiple memories share identical text content, they reference the same physical byte range:

```mermaid
flowchart TD
    M1["Memory 1 (live)<br/>textOffset: 0x1000"] --> TEXT["Shared text bytes<br/>'System initialized'"]
    M2["Memory 2 (purged)<br/>textOffset: 0x1000"] -.-> TEXT

    style TEXT fill:#e67e22,color:white
```

- When `Memory 2` is purged, its off-heap vector, norm, Bloom filter, centroid reference, and graph edges are zeroed/detached immediately.
- If live co-tenants (such as `Memory 1`) still reference the shared text, zeroing `text.dat` would corrupt the surviving records.
- Consequently, the shared text is preserved until the final co-tenant is purged.
- In this scenario, `PurgeResult.hasLocalRetention()` evaluates to `true`, `textRetainedShared` reports the exact retained byte count, `textSharedWith` indicates the count of surviving co-tenants, and `textDestroyed()` returns `false`.
- Once the remaining co-tenants are purged, the shared byte range is zeroed, and `hasLocalRetention()` returns `false`.

#### 3.3 Retained Audit Header Fields (`PurgeResult.RETAINED_HEADER_FIELDS`)

To ensure that the act of erasure itself remains verifiable and auditable without preserving identifying information or reconstructed text, `PurgeResult` retains structural metadata headers:

- `header_version`, `flags` (with `FLAG_PURGED = 0x40` set and `FLAG_TOMBSTONE` preserved)
- `timestamp_ms`, `importance`, `valence`, `arousal`
- `encoding_profile`, `encoding_alpha`, `encoding_beta`, `soul_version`

No vector payload, embedding, or text body survives in the record slot after a successful purge.

---

## Circadian Trigger

The consolidation daemon runs on a configurable schedule:

```mermaid
flowchart LR
    INGEST["Memory ingested"] --> CHECK{"Time since last<br/>consolidation > interval?"}
    CHECK -->|"No"| SKIP["Continue normally"]
    CHECK -->|"Yes"| REFLECT["Trigger consolidation cycle<br/><i>default: every 24 hours</i>"]

    style REFLECT fill:#9b59b6,color:white
```

The default interval is 24 hours — matching the biological circadian cycle. For testing, it can be set to any duration.

---

## Partition State Machine

```mermaid
stateDiagram-v2
    [*] --> ACTIVE: New day → create partition
    ACTIVE --> SEALED: Day rolls over
    SEALED --> REFLECTABLE: Consolidation processes
    REFLECTABLE --> TOMBSTONED: tombstoneRatio > threshold
    TOMBSTONED --> COMPACTED: VacuumCompactor compacts (dense)

    ACTIVE --> TOMBSTONED: High forget rate during active day

    note right of ACTIVE: Accepting writes
    note right of SEALED: Read-only, awaiting consolidation
    note right of REFLECTABLE: Consolidation complete, eligible for pruning
    note right of TOMBSTONED: Tombstone ratio exceeds threshold
    note right of COMPACTED: Compacted into dense slab with space reclaimed
```

---

## ReflectPathway — 9-Relay Sleep Pipeline

Spector 1.3.0 consolidates all sleep reflection operations into a single composable `ReflectPathway` pipeline with 9 specialized relays:

```mermaid
graph LR
    subgraph "NREM Slow-Wave Sleep (SWS)"
        R1["1. SynapticPruningRelay<br/><i>Downscaling & compaction</i>"]
        R2["2. EpisodicLogConsolidationRelay<br/><i>Systems replay & gist extraction</i>"]
    end
    subgraph "REM Dream Sleep"
        R3["3. SoulDriftRefusionRelay<br/><i>#503 Affective restamping & soul re-fusion</i>"]
        R4["4. ProactiveInterferenceRelay<br/><i>Near-duplicate decay</i>"]
    end
    subgraph "Synaptic Homeostasis & Maintenance"
        R5["5. HebbianHomeostasisRelay<br/><i>Edge decay</i>"]
        R6["6. TemporalPruningRelay<br/><i>Retention decay</i>"]
        R7["7. CrossLayerPromotionRelay<br/><i>Hebbian → Entity promotion</i>"]
        R8["8. EntityMaintenanceRelay<br/><i>Entity merge & graph decay</i>"]
    end
    subgraph "Durability"
        R9["9. WalJournalRelay<br/><i>WAL REFLECT checkpoint</i>"]
    end

    R1 --> R2 --> R3 --> R4 --> R5 --> R6 --> R7 --> R8 --> R9
```

### Systems Consolidation & Soul-Drift Re-Fusion (#503)
Following biological systems consolidation (Diekelmann & Born, 2010), declarative memory replay and gist abstraction occur predominantly during **NREM Slow-Wave Sleep (SWS)**. Emotional restamping and persona realignment occur during **REM Sleep**. 

When an agent's cognitive soul or personality configuration evolves, older memories retained with stale soul version stamps undergo re-fusion during REM reflection. The `SoulDriftRefusionRelay` identifies candidates with `header.soulVersion() < currentSoulVersion`, prioritizes candidates via a max-heap of encoding surprise z-scores, adapts the generative prior mean toward the autobiographical centroid, re-scores importance using current ICNU/salience parameters and header-derived hints, and stamps updated headers in-place.

---

## Consolidation Report

Each consolidation cycle produces a structured `ReflectReport` summarizing the sleep cycle:

| Metric | Description |
|---|---|
| **consolidatedCount** | Number of episodic records / facts promoted to Semantic tier |
| **tombstonedCount** | Number of memories tombstoned during Deep Sleep pruning |
| **compactedPartitions** | Count of partitions compacted and space reclaimed during reflection |
| **temporalPrunedCount** | Stale temporal chain nodes pruned |
| **soulDriftedCount** | Count of memories detected with outdated soul version stamps |
| **soulRefusedCount** | Count of soul-drifted memories re-fused with updated importance |
| **averageImportanceDelta** | Average absolute importance delta after soul re-fusion |
| **logTurnsConsolidated** | Episodic log conversation turns distilled into semantic memories |
| **duration** | Total reflection cycle time |
| **graphHealth** | Graph health metrics snapshot |

This report is logged, monitored, and exposed via the introspection API and Micrometer metrics.

---

## Next Steps

- :material-brain: [**Cortex — Tier Stores**](tiers.md) — the 4-tier architecture
- :material-flash: [**Synapse — Tags & Scoring**](tags.md) — the 64-byte header
- :material-head-cog: [**Dopamine — Surprise Detection**](novelty.md) — auto-importance scoring
