---
title: "Hippocampus — Sleep Consolidation"
description: "How Spector consolidates episodic memories into semantic knowledge during 'sleep' — K-Means clustering, tombstone compaction, and partition rebuild."
---

# 🛏️ Hippocampus — Sleep Consolidation

> **Biological Analog**: During sleep, the **hippocampus replays** episodic memory traces to the neocortex, gradually transferring knowledge from episode-specific to generalized semantic form. This is called **systems consolidation**. Simultaneously, **synaptic pruning** weakens unused connections — the brain's garbage collector.

---

## The Two Mechanisms

### 1. Sleep Consolidation — Episodic → Semantic Promotion

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

When memories are `forget()`'d, they are tombstoned (bit 0 of flags byte set to 1). The scorer skips them in Phase 1 (~1 cycle). But tombstoned records still consume disk space.

When the tombstone ratio in a partition exceeds a threshold (default: 30%), a **partition rebuild** is triggered:

```mermaid
graph LR
    A["Old Partition<br/>1000 records<br/>400 tombstoned<br/>(40% ratio)"] -->|"Compact"| B["New Partition<br/>600 records<br/>0 tombstoned<br/>(dense)"]
    A -->|"Atomic swap"| C["Closed & Deleted"]

    style A fill:#e74c3c,color:white
    style B fill:#2ecc71,color:white
    style C fill:#95a5a6,color:white
```

**The rebuild process**:

1. Allocate a new partition file
2. Sequentially copy only live (non-tombstoned) records
3. Atomically swap the new partition in (CAS operation — readers see old or new, never torn)
4. Close and delete the old partition

!!! warning "Concurrent Safety"
    The swap uses a CAS (compare-and-swap) operation. Readers that are mid-scan on the old partition complete safely because the old memory segment remains valid until close. New scans use the compacted partition.

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
    REFLECTABLE --> TOMBSTONED: tombstoneRatio > 30%
    TOMBSTONED --> COMPACTED: Compactor rebuilds

    ACTIVE --> TOMBSTONED: High forget rate during active day

    note right of ACTIVE: Accepting writes
    note right of SEALED: Read-only, awaiting consolidation
    note right of REFLECTABLE: Consolidation complete, eligible for pruning
    note right of TOMBSTONED: Queued for compaction
    note right of COMPACTED: Rebuilt as dense partition
```

---

## Consolidation Report

Each consolidation cycle produces a report summarizing what happened:

| Metric | Description |
|---|---|
| **partitionsProcessed** | Number of sealed partitions scanned |
| **memoriesConsolidated** | Episodic records that matched a cluster |
| **semanticMemoriesCreated** | New semantic records written |
| **hebbianEdgesDecayed** | Hebbian edges weakened |
| **entitiesMerged** | Near-duplicate entities merged |
| **crossPromotions** | Hebbian → Entity promotions |
| **temporalNodesPruned** | Stale temporal chain nodes removed |
| **durationMs** | Total cycle time |

This can be logged, monitored, or exposed via the introspection API for observability.

---

## Next Steps

- :material-brain: [**Cortex — Tier Stores**](cortex.md) — the 4-tier architecture
- :material-flash: [**Synapse — Tags & Scoring**](synapse.md) — the 64-byte header
- :material-head-cog: [**Dopamine — Surprise Detection**](dopamine.md) — auto-importance scoring
