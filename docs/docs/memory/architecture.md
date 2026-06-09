---
title: System Architecture
description: "Package hierarchy, data flow, and extensibility model for Spector Memory."
---

# System Architecture

Spector Memory is organized around a **biological metaphor** where each Java package corresponds to a brain region or cognitive mechanism. This isn't just naming — the architecture genuinely mirrors how biological memory systems interact.

---

## Extensibility

| Component | Extension point | What you can customize |
|---|---|---|
| `SpectorMemory` | Single entry point for all operations | Configure tiers, capacities, embedding providers |
| `TierStore` interface | Add new memory tiers | Implement the interface + register in `TierRouter` — no other changes needed |
| `AbstractTierStore` | Common tier lifecycle | Extend for new off-heap tier stores with Arena/segment management |
| `RecallListener` | Post-recall hooks | Add async listeners for co-activation tracking, logging, metrics |
| `CognitiveIngestionTarget` / `RecallPipeline` | Discrete processing steps | Each step is independently testable and replaceable |

---

## Data Flow: Ingestion

The ingestion pipeline is split across two layers:

- **`IngestionPipeline`** (in `spector-ingestion`) — handles step 1 (embed) and chunking for large documents
- **`CognitiveIngestionTarget`** (in `spector-memory`) — handles steps 2–9 (synaptic encoding → WAL)

```mermaid
sequenceDiagram
    participant App as Application
    participant SM as SpectorMemory
    participant CT as CognitiveIngestionTarget
    participant EP as EmbeddingProvider
    participant SD as SurpriseDetector
    participant FP as FlashbulbPolicy
    participant SQ as ScalarQuantizer
    participant TR as TierRouter
    participant MI as MemoryIndex
    participant WAL as MemoryWal
    participant HG as HebbianGraph
    participant TC as TemporalChain
    participant EG as EntityGraph

    App->>SM: remember(id, text, type, tags)
    SM->>CT: ingestCognitive(id, text, vector, type, tags, ...)
    
    Note over CT: Step 1: Embed (done by unified IngestionPipeline)
    Note over CT: or via CognitiveIngestionTarget.ingestCognitive()
    CT->>EP: embed(text)
    EP-->>CT: float[4096]
    
    Note over CT: Step 2: Encode tags
    CT->>CT: SynapticTagEncoder.encode(tags) → 64-bit Bloom
    
    Note over CT: Step 3: Surprise detection
    CT->>SD: computeImportance(l2Norm)
    SD-->>CT: importance (0.0 – 1.0)
    
    Note over CT: Step 4: Flashbulb check
    CT->>FP: evaluate(zScore)
    FP-->>CT: flashbulb? → pin + max importance
    
    Note over CT: Step 5: Quantize
    CT->>SQ: encode(float[]) → byte[]
    
    Note over CT: Step 6: Build header
    CT->>CT: CognitiveHeader(timestamp, tags, importance, ...)
    
    Note over CT: Step 7: Route & write
    CT->>TR: write(type, header, quantized)
    TR-->>CT: byte offset
    
    Note over CT: Step 8: Index
    CT->>MI: register(id, location, text, source, tags)
    
    Note over CT: Step 9a: WAL
    CT->>WAL: appendRemember(id, quantized)
    
    Note over CT: Step 9b: Hebbian edge strengthening
    CT->>HG: strengthen(currentIdx, previousIdx, 1.0f)
    
    Note over CT: Step 9c: Temporal chain linking
    CT->>TC: link(currentIdx, lastIdx, sessionId)
    
    Note over CT: Step 9d: Entity extraction & graph population
    CT->>EG: addEntity() + linkToMemory() + addRelation()
    
    Note over CT: Step 10: Circadian check
    CT->>CT: triggerReflectIfDue()
```

> [!NOTE]
> When ingestion comes through the unified `IngestionPipeline` (e.g., file ingestion), embedding (step 1) is handled by the pipeline itself. `CognitiveIngestionTarget.ingest()` receives a pre-embedded vector and executes steps 2–9. When called via `SpectorMemory.remember()`, `CognitiveIngestionTarget.ingestCognitive()` handles embedding internally.

> [!NOTE]
> Steps 9b–9d are **gracefully degrading**: if any graph component is null (not configured) or throws, the step is skipped with a `log.warn()` and ingestion continues normally.

---

## Data Flow: Recall

The recall pipeline executes parallel tier scans using Virtual Threads:

```mermaid
sequenceDiagram
    participant App as Application
    participant RP as RecallPipeline
    participant EP as EmbeddingProvider
    participant PS as ProspectiveScheduler
    participant CT as ConcurrentTasks
    participant CS as CognitiveScorer
    participant SS as SuppressionSet
    participant HP as HabituationPenalty
    participant HG as HebbianGraph
    participant TC as TemporalChain
    participant EG as EntityGraph

    App->>RP: recall("query", options)
    
    Note over RP: Step 1: Embed query
    RP->>EP: embed("query")
    EP-->>RP: float[4096]
    
    Note over RP: Step 2: Prospective reminders
    RP->>PS: collectDue()
    PS-->>RP: due reminders
    
    Note over RP: Step 3: Parallel tier scanning
    RP->>CT: forkJoinAll(scanTasks)
    
    par Working Memory
        CT->>CS: score(workingSegment, ...)
    and Episodic Partition 1
        CT->>CS: score(partition1, ...)
    and Episodic Partition 2
        CT->>CS: score(partition2, ...)
    and Semantic Partition 1 (virtual thread)
        CT->>CS: score(semPartition1, ...)
    and Semantic Partition N (virtual thread)
        CT->>CS: score(semPartitionN, ...)
    and Procedural
        CT->>CS: score(proceduralSegment, ...)
    end
    
    CS-->>RP: List<ScoredRecord>
    
    Note over RP: Step 4: Filter suppressed
    RP->>SS: isSuppressed(id)?
    
    Note over RP: Step 5a: Habituation penalty
    RP->>HP: recordAndComputePenalty(id)
    
    Note over RP: Step 5b: STDP causal boost
    RP->>RP: CoActivationTracker.getPredictiveStrength()
    
    Note over RP: Step 5c: Hebbian spreading activation
    RP->>HG: activateNeighbors(seedIdx, depth=2)
    HG-->>RP: graph-activated memory indices
    
    Note over RP: Step 5d: Temporal chain extension
    RP->>TC: followForward/Backward(idx, maxHops=3)
    TC-->>RP: temporally-linked memory indices
    
    Note over RP: Step 5e: Entity graph traversal
    RP->>EG: extract query entities → BFS 2-hop
    EG-->>RP: entity-linked memory indices
    
    Note over RP: Step 6: Merge, dedup, sort → final top-K
    RP-->>App: List<CognitiveResult>
    
    Note over RP: Step 7: Async listeners (Virtual Thread)
    RP->>RP: notify(HebbianListener, LtpListener)
```

---

## Package Dependency Graph

```mermaid
graph LR
    SM[SpectorMemory<br/>Façade] --> CT[pipeline/<br/>CognitiveIngestionTarget]
    SM --> RP[pipeline/<br/>RecallPipeline]
    SM --> TR[cortex/<br/>TierRouter]
    SM --> MI[index/<br/>MemoryIndex]
    
    CT --> EP[embed-api/<br/>EmbeddingProvider]
    CT --> SQ[core/<br/>ScalarQuantizer]
    CT --> SD[dopamine/<br/>SurpriseDetector]
    CT --> TR
    CT --> MI
    CT --> WAL[sync/<br/>MemoryWal]
    CT --> HG[hebbian/<br/>HebbianGraph]
    CT --> TC[temporal/<br/>TemporalChain]
    CT --> EG[graph/<br/>EntityGraph]
    CT --> EX[graph/<br/>EntityExtractor]
    
    RP --> EP
    RP --> CS[synapse/<br/>CognitiveScorer]
    RP --> TR
    RP --> MI
    RP --> SS[inhibition/<br/>SuppressionSet]
    RP --> HP[habituation/<br/>HabituationPenalty]
    RP --> HG
    RP --> TC
    RP --> EG
    
    CS --> SF[core/<br/>SimilarityFunction]
    CS --> DS[synapse/<br/>DecayStrategy]
    
    TR --> WM[cortex/<br/>WorkingMemoryStore]
    TR --> EM[cortex/<br/>EpisodicMemoryStore]
    TR --> SE[cortex/<br/>SemanticMemoryStore]
    TR --> PS2[cortex/<br/>PartitionedSemanticStore]
    TR --> PR[cortex/<br/>ProceduralMemoryStore]
    
    RP -.->|async| HL[pipeline/<br/>HebbianListener]
    RP -.->|async| LL[pipeline/<br/>LtpListener]
    
    style SM fill:#4a90d9,color:white
    style CS fill:#e74c3c,color:white
    style TR fill:#2ecc71,color:white
    style HG fill:#e74c3c,color:white
    style EG fill:#9b59b6,color:white
    style TC fill:#f39c12,color:white
```

---

## The 64-Byte Cognitive Record

Every memory is stored as a fixed-size binary record in off-heap memory. The synaptic header occupies exactly **one CPU cache line** (64 bytes) for optimal sequential scan performance:

### Header Diagram

![64-Byte Synaptic Header Layout](../assets/header-layout-light.svg#only-light){ width="100%" }
![64-Byte Synaptic Header Layout](../assets/header-layout-dark.svg#only-dark){ width="100%" }

### Header Layout (64 bytes — 1× Cache Line)

| Offset | Field | Size | Type | Description |
|:---:|:---|:---:|:---:|:---|
| 0 | `header_version` | 1B | byte | Always `1` |
| 1 | `flags` | 1B | byte | Tombstone, memory type, consolidated, pinned, resolved |
| 2 | `valence` | 1B | signed byte | Emotional coloring (−128 to +127) |
| 3 | `arousal` | 1B | unsigned byte | Emotional intensity (0–255) |
| 4 | `importance` | 4B | float | Base importance score (0.05–10.0) |
| 8 | `timestamp_ms` | 8B | long | Unix epoch ms when memory was formed |
| 16 | `agent_recall_count` | 4B | int | LTP reinforcement counter (agent-explicit) |
| 20 | `exact_norm` | 4B | float | L2 norm of original float vector |
| 24 | `synaptic_tags` | 8B | long | 64-bit inline Bloom filter |
| 32 | `centroid_id` | 2B | short | IVF partition routing ID |
| 34 | `_pad0` | 2B | — | Alignment padding |
| 36 | `storage_strength` | 4B | float | Two-Factor Memory S(t) (Bjork & Bjork) |
| 40 | `spector_recall_cnt` | 4B | int | Auto-LTP passive recall counter |
| 44 | `_reserved_f1` | 4B | float | Reserved for future use |
| 48 | `last_auto_ltp` | 8B | long | Last auto-LTP timestamp |
| 56 | `_reserved_l1` | 8B | long | Reserved (future 128-bit tag upper half) |
| | | **64B** | | **= 1× cache line, 2× AVX2** |

!!! tip "Why 64 bytes?"
    **Cache-line alignment** eliminates split-line reads during sequential scans. When the scorer iterates over 1M records, each header read hits exactly one cache line — no partial line loads, no false sharing. The 16 bytes of reserved space prevent future migration costs when new fields are added.

After the header, the quantized vector payload follows immediately:

| Component | Size | Notes |
|:---|:---:|:---|
| Synaptic Header | 64B | Fixed, cache-line aligned |
| Quantized Vector | N bytes | INT8 values (1 byte per dimension) |
| **Total stride** | **64 + N** | At 768-dim: **832 bytes/record** |


---

## Next Steps

- :material-lightning-bolt: [**6-Phase Scoring Pipeline**](scoring-pipeline.md) — the SIMD hot-loop that makes it fast
- :material-share-variant: [**3-Layer Cognitive Graph**](hebbian.md) — Hebbian, Entity, and Temporal graphs
- :material-brain: [**Cortex — Tier Stores**](cortex.md) — the 4-tier memory architecture
- :material-memory: [**Off-Heap Panama Design**](panama-design.md) — zero-GC binary layout
