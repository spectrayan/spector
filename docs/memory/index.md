---
title: "🧠 Cognitive Memory"
description: "A formal cognitive memory engine implementing MF-001: multi-tier retention, associative graphs, and signal-complete recall at microsecond latency."
---

# 🧠 Cognitive Memory

!!! quote "The Founding Distinction (MF-001)"
    *A database returns what was written. A memory engine reconstructs what is reachable from a cue at this moment — under decay, association, and tier physics — without losing a live trace because the first index was the wrong one.*

    As formalized in the [Memory Fundamentals Specification](https://github.com/spectrayan/memory-fundamentals), the identity of a memory engine is its recall algebra, not its storage topology. Spector implements this model as a single off-heap engram substrate with multiple access paths.

---

## The 4-Tier Memory Architecture

Rather than treating memory as an undifferentiated vector store, Spector organizes traces across four cognitive tiers to address distinct operational retention windows, access frequencies, and consolidation dynamics:

=== "🧪 Working Memory"

    **Biological analog: Prefrontal Cortex**
    
    Volatile, limited-capacity buffer for the current task context. Operates as a circular buffer where the oldest entries are automatically evicted when capacity is reached.
    
    - **Capacity**: Configurable (default: 100 records)
    - **Storage**: In-memory segment (volatile)
    - **Use case**: "What was the user just talking about?"

=== "📝 Episodic Memory"

    **Biological analog: Hippocampus**
    
    Time-stamped event records representing autobiographical history. Partitioned by day and backed by memory-mapped files for persistence across restarts. Supports sleep consolidation into semantic memory.
    
    - **Capacity**: Unbounded (time-partitioned)
    - **Storage**: High-performance memory-mapped partitions (persistent)
    - **Use case**: "What error did we debug yesterday?"

=== "🧬 Semantic Memory"

    **Biological analog: Neocortex**
    
    Distilled, permanent world knowledge and facts. Created by consolidation (sleep cycles) from episodic clusters, or directly by the user. Supports two modes:
    
    - **Partitioned Mode** (default): Rolling partition files with parallel retrieval.
    - **Single-File Mode**: In-memory slab for light deployments.
    
    - **Capacity**: Unbounded in partitioned mode (configurable per-partition, default: 10,000 records)
    - **Recall**: Parallel scan across partitions using virtual threads
    - **Compaction**: Per-partition rebuilds performed live during operation
    - **Use case**: "The user prefers dark mode."

=== "⚙️ Procedural Memory"

    **Biological analog: Basal Ganglia**
    
    Learned procedures, rules, and behavioral guidelines. A small, append-only store for rules that shape the agent's reasoning.
    
    - **Capacity**: Configurable (default: 500 records)
    - **Storage**: In-memory segment (persistent via write-ahead log replay)
    - **Use case**: "Always use exponential backoff for retries."

---

## 🧭 Which Memory Tier & Pathway Do I Need?

Choosing the right memory tier ensures optimal signal-to-noise ratio, hardware cache efficiency, and retention physics. Use this interactive decision tree and comparison matrix to select the right tier and retrieval pathway for your agent:

```mermaid
flowchart TD
    START(["🧠 Ingesting or Recalling Knowledge"]) --> Q1{"What is the operational lifecycle of this trace?"}

    Q1 -->|"Current prompt / dialogue turn<br/>(Volatile buffer)"| T_WM["🧪 Working Memory<br/><b>rememberWorking(...)</b><br/>• Circular volatile buffer<br/>• Sub-microsecond seek<br/>• Auto-evicts oldest entries"]
    Q1 -->|"Timestamped event / agent action<br/>(Autobiographical journal)"| T_EM["📝 Episodic Memory<br/><b>rememberEpisodic(...)</b><br/>• Daily memory-mapped partition<br/>• Power-law temporal decay<br/>• Consolidated during sleep"]
    Q1 -->|"Distilled fact / user preference<br/>(Permanent world knowledge)"| T_SE["🧬 Semantic Memory<br/><b>rememberSemantic(...)</b><br/>• Zero-GC off-heap partitions<br/>• Parallel SIMD vector scan<br/>• Enduring cross-session facts"]
    Q1 -->|"Operational rule / tool constraint<br/>(Behavioral guidelines)"| T_PR["⚙️ Procedural Memory<br/><b>rememberProcedural(...)</b><br/>• Append-only WAL store<br/>• Deterministic constraint check<br/>• Pinned across all sessions"]

    T_WM --> P_SELECT{"How will this trace be queried?"}
    T_EM --> P_SELECT
    T_SE --> P_SELECT
    T_PR --> P_SELECT

    P_SELECT -->|"Associative cue + temporal context"| PW_FUSED["⚡ 6-Phase Fused Recall<br/>Bloom filter + valence + decay + SIMD distance"]
    P_SELECT -->|"Direct pointer / known Engram ID"| PW_DIRECT["🎯 Direct Engram Seek<br/>Zero-allocation offset seek (~150ns)"]
    P_SELECT -->|"Entity relationship or causal flow"| PW_GRAPH["🕸️ Cognitive Graph Recall<br/>Hebbian association + Temporal chains"]
    P_SELECT -->|"Corroboration from multiple events"| PW_MULTI["🔍 Multi-Evidence Recall<br/>Cross-episode fact synthesis & clustering"]

    classDef working fill:#2563eb18,stroke:#3b82f6,stroke-width:1.5px
    classDef episodic fill:#10b98118,stroke:#10b981,stroke-width:1.5px
    classDef semantic fill:#8b5cf618,stroke:#8b5cf6,stroke-width:1.5px
    classDef procedural fill:#f59e0b18,stroke:#f59e0b,stroke-width:1.5px

    class T_WM working
    class T_EM episodic
    class T_SE semantic
    class T_PR procedural
```

### Tier Decision Matrix

| Tier | Substrate & Storage | Retention & Eviction | Latency (p50) | Ingestion Verb | Best-Fit Agent Scenarios |
|:---|:---|:---|:---:|:---|:---|
| **🧪 Working** | Volatile off-heap circular ring | FIFO eviction on capacity overflow | <span class="chip chip-latency">~100ns</span> | `rememberWorking` | Multi-turn chat context, scratchpads, intermediate plan steps |
| **📝 Episodic** | Mapped binary partition files (`.seg`) | Unbounded, time-partitioned, sleep consolidation | <span class="chip chip-latency">&lt;1ms</span> | `rememberEpisodic` | Interaction logs, tool execution traces, temporal user events |
| **🧬 Semantic** | Partitioned zero-GC off-heap slabs | Permanent, compacted during offline/online cycles | <span class="chip chip-latency">1.01ms</span> | `rememberSemantic` | User preferences, distilled facts, codebase knowledge, ontology |
| **⚙️ Procedural** | Persistent append-only WAL segment | Permanent, deterministic ordering, high salience | <span class="chip chip-latency">&lt;200ns</span> | `rememberProcedural` | System prompt constraints, tool policies, safety guidelines |

---

## Cognitive Architecture Mapping

As established in the [Memory Fundamentals Specification (MF-001)](https://github.com/spectrayan/memory-fundamentals), Spector models cognitive subsystems as concrete operational mechanisms addressing specific retrieval and retention requirements:

```mermaid
graph TB
    subgraph "🧠 Spector Memory"
        SM[SpectorMemory<br/>Façade]:::core --> CT[RememberPathway<br/>Pathway: Remember]:::core
        SM --> RP[RecallPathway<br/>Pathway: Recall]:::core
        
        subgraph "Cortex — Tier Stores"
            NK[NamespaceKernel<br/>EngramMemory]:::core --> WM[Working<br/>Prefrontal Cortex]:::working
            NK --> EM[Episodic<br/>Hippocampus]:::episodic
            NK --> SE[Semantic<br/>Neocortex]:::semantic
            NK --> PR[Procedural<br/>Basal Ganglia]:::procedural
        end
        
        subgraph "Synapse — Scoring"
            CS[CognitiveScorer<br/>6-phase SIMD]:::synapse --> STE[SynapticTagEncoder<br/>Bloom Filter]:::synapse
            CS --> DS[DecayStrategy<br/>Temporal Decay]:::synapse
        end
        
        subgraph "Neuromodulators"
            SD[SurpriseDetector<br/>Dopamine]:::synapse --> FP[FlashbulbPolicy]:::synapse
            VT[ValenceTracker<br/>Amygdala]:::synapse
            HP[HabituationPenalty<br/>Anti-filter bubble]:::synapse
            SS[SuppressionSet<br/>Inhibition]:::synapse
        end
        
        subgraph "3-Layer Cognitive Graph"
            HG[HebbianGraph<br/>Layer 1: Association]:::core
            TC[TemporalChain<br/>Layer 2: Causal]:::core
            HEG[HyperEntityGraph<br/>Layer 3: Event-Episode]:::core
            ED[EntityDirectory<br/>Identity Registry]:::core
            CA[CoActivationMemory<br/>STDP Learning]:::core
        end
        
        subgraph "Consolidation"
            RD[ReflectDaemon<br/>Sleep Consolidation]:::core
            TCC[TombstoneCompactor<br/>Synaptic Pruning]:::core
        end
        
        CT --> NK
        RP --> CS
        RP --> NK
        RP --> HG
        RP --> TC
        RP --> ED
        RP --> HEG
    end
```

---

## What Makes This Different

Every AI memory solution today wraps a scripting layer around Postgres/pgvector or a standard vector database. They suffer from:

- **Network latency**: 50-200ms per query (HTTP → DB → HTTP)
- **Global Interpreter Lock**: Sequential embedding and scoring under a lock
- **The truncation trap**: Candidate generation by a single signal (typically cosine top-K) followed by post-filtering, permanently dropping high-importance or emotionally salient memories with lower initial cosine similarity.

Spector Memory collapses the entire cognitive stack onto a **zero-overhead, off-heap memory store** with hardware-accelerated scoring. The result:

| Metric | Traditional Python Layer | **Spector Memory** |
|---|---|---|
| Query latency | 50-200ms | **Ultra-low latency** † |
| GC pauses | Unpredictable | **≤0.01%** (100% off-heap) † |
| Scoring pipeline | Post-filter (lossy) | **Fused SIMD** (lossless) |
| Concurrent queries | Lock-limited | **61,000 QPS** (Virtual Threads) † |
| Memory per record | ~500B (Object wrappers) | **Compact binary header + vector** |

† *Measured on Intel Core Ultra 9 285K, Java 25, AVX2. See [Benchmarks](performance.md).*

---

## Explore the Documentation

<div class="grid cards" markdown>

-   :material-brain:{ .lg .middle } **System Architecture**

    ---

    Package hierarchy, data flow diagrams, and extensibility model

    [:octicons-arrow-right-24: Architecture](architecture.md)

-   :material-lightning-bolt:{ .lg .middle } **Recall Pathway & 6-Phase Scoring**

    ---

    Deep dive into the SIMD hot-loop: tombstone → tags → valence → importance → L2 → fused score

    [:octicons-arrow-right-24: Scoring Engine](scoring-pipeline.md)

-   :material-share-variant:{ .lg .middle } **3-Layer Cognitive Graph**

    ---

    Hebbian association, temporal causal chains, and event-episode hyperedges — three graph structures that augment vector recall with multi-hop reasoning, integrated with a central EntityDirectory

    [:octicons-arrow-right-24: Cognitive Graph](hebbian.md)

-   :material-head-cog:{ .lg .middle } **Cognitive Subsystems**

    ---

    Principled cognitive subsystems mapped to code: Cortex, Hippocampus, Synapse, Dopamine, Amygdala, Habituation, Inhibition

    [:octicons-arrow-right-24: Start with Cortex](cortex.md)

-   :material-speedometer:{ .lg .middle } **Performance & SIMD**

    ---

    Benchmark results, SIMD kernel throughput, optimization techniques, virtual thread scaling

    [:octicons-arrow-right-24: Performance](performance.md)

-   :material-memory:{ .lg .middle } **Off-Heap Panama Design**

    ---

    Zero-GC architecture, MemorySegment lifecycle, mmap partitions, 64-byte CognitiveRecord binary format

    [:octicons-arrow-right-24: Panama Design](panama-design.md)

-   :material-chart-bar:{ .lg .middle } **Cognitive Evaluation**

    ---

    Detailed test methodology, evaluation results, statistical comparisons, and the Mike Thompson dataset

    [:octicons-arrow-right-24: Evaluation & Results](evaluation.md)

-   :material-api:{ .lg .middle } **API Reference**

    ---

    SpectorMemory.Builder, RecallOptions, CognitiveResult, MemoryType — full method signatures

    [:octicons-arrow-right-24: API Reference](api-reference.md)

</div>
