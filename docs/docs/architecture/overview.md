---
title: "Architecture Overview — System Architecture & Data Flow"
description: "Spector architecture: SIMD-accelerated search pipeline, cognitive memory, off-heap Panama storage, MCP agent integration, and REST/gRPC/SSE APIs."
---

# 🏗️ Architecture Overview

> **Spector is a SIMD-accelerated AI memory backbone** with built-in MCP server, hybrid search, and biologically-inspired cognitive memory. This page covers the system architecture, data flows, threading model, and memory architecture that make sub-millisecond, agent-native search possible.

---

## System Architecture

```mermaid
graph TB
    subgraph Clients["Client Interfaces"]
        claude["🤖 Claude Desktop"]
        cursor["✏️ Cursor / AI IDEs"]
        agents["🦾 Autonomous Agents"]
        sdk["☕ Java SDK"]
        spring["🌱 Spring AI"]
        cli["🖥️ spectorctl CLI"]
        rest["🌐 REST / gRPC"]
    end

    subgraph Transport["Transport Layer"]
        mcp["MCP Server<br/><i>stdio · Streamable HTTP · 21 tools (6 search + 15 memory)</i>"]
        armeria["Armeria Server :7070<br/><i>REST + gRPC + SSE streaming</i>"]
    end

    subgraph Engine["Spector Engine"]
        runtime["SpectorRuntime<br/><i>Composition Root</i>"]

        subgraph Search["Search Pipeline"]
            hybrid["Hybrid Search<br/><i>Mode auto-detection</i>"]
            hnsw["HNSW Index<br/><i>M=16, ef=200</i>"]
            bm25["BM25 Index<br/><i>Inverted + analyzers</i>"]
            rrf["RRF Fusion<br/><i>+ LLM reranking</i>"]
        end

        subgraph Memory["Cognitive Memory"]
            cortex["4-Tier Cortex<br/><i>Working → Episodic → Semantic → Procedural</i>"]
            hebbian["Hebbian Graph<br/><i>Co-activation associations</i>"]
            decay["Memory Decay<br/><i>Power-law forgetting</i>"]
            consolidation["Sleep Consolidation<br/><i>Hippocampal replay + pruning</i>"]
        end

        subgraph Ingest["Ingestion Pipeline"]
            chunking["Document Chunking<br/><i>Sentence · Paragraph · Semantic</i>"]
            embedding["Embedding<br/><i>Ollama · Provider SPI</i>"]
            indexing["Index Writer<br/><i>Batch + streaming</i>"]
        end
    end

    subgraph Platform["Platform Layer (Zero GC)"]
        simd["SIMD Kernels<br/><i>AVX2 / AVX-512 / NEON</i>"]
        panama["Panama Storage<br/><i>Off-heap MemorySegment · mmap</i>"]
        quant["SVASQ Quantization<br/><i>INT8 · INT4 · IVF-PQ</i>"]
        gpu["GPU Acceleration<br/><i>CUDA via Panama FFM</i>"]
    end

    subgraph Observe["Observability"]
        events["TelemetryBus<br/><i>12 event types</i>"]
        metrics["Micrometer<br/><i>Prometheus export</i>"]
        sse["SSE Event Stream<br/><i>Real-time telemetry</i>"]
    end

    claude & cursor & agents --> mcp
    sdk & spring --> Engine
    cli & rest --> armeria
    mcp & armeria --> runtime

    runtime --> Search & Memory & Ingest

    Search --> simd & panama & quant
    Memory --> simd & panama
    Ingest --> embedding

    runtime --> events
    events --> metrics & sse

    gpu -.->|optional| simd

    style Clients fill:#5b6abf,stroke:#e94560,color:#fff
    style Transport fill:#4a6fa5,stroke:#3b82f6,color:#fff
    style Engine fill:#3b82f6,stroke:#7c3aed,color:#fff
    style Platform fill:#7c3aed,stroke:#e94560,color:#fff
    style Observe fill:#5b6abf,stroke:#7c3aed,color:#fff
    style Search fill:#4a6fa5,stroke:#3b82f6,color:#fff
    style Memory fill:#4a6fa5,stroke:#3b82f6,color:#fff
    style Ingest fill:#4a6fa5,stroke:#3b82f6,color:#fff
```

### High-Level Data Flow

```mermaid
graph LR
    subgraph Ingest["Ingest"]
        docs["📄 Documents"]
        files["📁 Files"]
        api["🌐 API Data"]
    end

    subgraph Process["Process"]
        chunk["✂️ Chunk"]
        embed["🧬 Embed"]
        quantize["🗜️ Quantize"]
    end

    subgraph Store["Store"]
        vectors["📊 Vector Index<br/><i>HNSW · IVF-PQ</i>"]
        text["📝 Text Index<br/><i>BM25</i>"]
        memory["🧠 Cognitive Store<br/><i>4-tier cortex</i>"]
    end

    subgraph Query["Query"]
        search["🔍 Hybrid Search"]
        recall["💭 Memory Recall"]
        rag["🤖 RAG Pipeline"]
    end

    docs & files & api --> chunk --> embed --> quantize
    quantize --> vectors & text & memory
    vectors & text --> search --> rag
    memory --> recall --> rag

    style Ingest fill:#5b6abf,stroke:#e94560,color:#fff
    style Process fill:#4a6fa5,stroke:#3b82f6,color:#fff
    style Store fill:#3b82f6,stroke:#7c3aed,color:#fff
    style Query fill:#7c3aed,stroke:#e94560,color:#fff
```

### Deployment Modes

```mermaid
graph LR
    subgraph Embedded["Embedded Mode"]
        lib["SpectorEngine API<br/><i>In-process · zero-network · drop-in JAR</i>"]
    end

    subgraph Standalone["Standalone Mode"]
        jar["java -jar spector.jar<br/><i>Engine + MCP + REST/gRPC + SSE</i>"]
    end

    subgraph Distributed["Distributed Mode"]
        coord["Coordinator<br/><i>Query routing · fan-out</i>"]
        s1["Shard 1"] & s2["Shard 2"] & s3["Shard N"]
        coord --> s1 & s2 & s3
    end

    style Embedded fill:#4a6fa5,stroke:#3b82f6,color:#fff
    style Standalone fill:#3b82f6,stroke:#7c3aed,color:#fff
    style Distributed fill:#7c3aed,stroke:#e94560,color:#fff
```

---

## 🤖 MCP Architecture — Agent-Native Engine

Spector's MCP server runs **in-process** — the agent's tool calls go directly into SIMD kernels with zero network hops, zero serialization, and zero GC pressure. This is the architectural advantage over adapters that wrap a database behind an HTTP API.

### Tool Registry

```mermaid
graph TB
    subgraph Agents["AI Agents"]
        claude["🤖 Claude Desktop"]
        cursor["✏️ Cursor / Windsurf"]
        cline["🔧 Cline / Aider"]
        custom["🦾 Custom Agents"]
    end

    subgraph MCP["MCP Server — Dual Transport · JSON-RPC 2.0"]
        transport["Transport Layer<br/><i>stdio (stdin/stdout) for CLI agents<br/>Streamable HTTP (/mcp) for remote agents</i>"]
        registry["SpectorToolRegistry<br/><i>21 tools · auto-registration</i>"]
        handler["McpToolHandler<br/><i>Base class · thread-safe · virtual threads</i>"]

        subgraph Engine["Engine Tools — 6"]
            e1["engine_search — Semantic vector search"]
            e2["engine_hybrid_search — Vector + BM25 + RRF"]
            e3["engine_rag — RAG with context assembly"]
            e4["engine_ingest — File/text ingestion"]
            e5["engine_delete — Document removal"]
            e6["engine_status — Index stats & health"]
        end

        subgraph Mem["Cognitive Memory Tools — 15"]
            m1["memory_remember — Store with importance & tags"]
            m2["memory_recall — Fused SIMD scoring recall"]
            m3["working_memory_scratchpad — Reasoning scratch space"]
            m4["memory_reinforce — Outcome feedback +/-"]
            m5["memory_forget — Intentional forgetting"]
            m6["memory_status — Per-tier statistics"]
            m7["memory_introspect — Self-reflection"]
            m8["memory_suppress — Temporary suppression"]
            m9["memory_resolve — Conflict resolution"]
            m10["memory_reminder — Proactive reminders"]
            m11["memory_why_not — Explain recall misses"]
            m12["memory_compute_importance — Pre-ingestion scoring"]
            m13["memory_inspect — Full cognitive X-ray"]
            m14["memory_export — Bulk memory export"]
            m15["memory_browse — Browse by tag/tier"]
        end
    end

    subgraph Core["In-Process Engine — Zero Network Overhead"]
        runtime["SpectorRuntime<br/><i>Engine + Memory + Ingestion</i>"]
        simd["SIMD Kernels<br/><i>AVX2/512 · ~100µs per search</i>"]
        panama["Panama Off-Heap<br/><i>Zero GC · mmap storage</i>"]
    end

    Agents -->|stdio / HTTP| transport --> registry --> handler
    handler --> Engine & Mem
    Engine & Mem --> runtime --> simd --> panama

    style Agents fill:#5b6abf,stroke:#e94560,color:#fff
    style MCP fill:#4a6fa5,stroke:#3b82f6,color:#fff
    style Engine fill:#3b82f6,stroke:#7c3aed,color:#fff
    style Mem fill:#7c3aed,stroke:#e94560,color:#fff
    style Core fill:#5b6abf,stroke:#e94560,color:#fff
```

### Agent Interaction Flow

```mermaid
sequenceDiagram
    participant Agent as 🤖 AI Agent
    participant MCP as 📡 MCP Server
    participant Tools as 🔧 ToolRegistry
    participant Runtime as ⚡ SpectorRuntime
    participant SIMD as 🔬 SIMD (off-heap)

    Note over Agent,SIMD: Single JVM process — no HTTP, no gRPC, no serialization

    Agent->>MCP: tools/call {"name": "memory_remember", ...}
    MCP->>Tools: Route → MemoryRememberTool
    Tools->>Runtime: memory().remember(text, tags, importance)
    Runtime->>SIMD: Embed → HNSW insert → tier assign
    SIMD-->>Agent: ✅ memoryId + tier (~1ms)

    Agent->>MCP: tools/call {"name": "memory_recall", ...}
    MCP->>Tools: Route → MemoryRecallTool
    Tools->>Runtime: memory().recall(query, topK)
    Runtime->>SIMD: Fused scoring: sim × importance × decay
    SIMD-->>Agent: 📋 Ranked memories (~0.13ms)

    Agent->>MCP: tools/call {"name": "engine_hybrid_search", ...}
    MCP->>Tools: Route → EngineHybridSearchTool
    Tools->>Runtime: search().hybridSearch(text, topK)
    Runtime->>SIMD: Parallel HNSW + BM25 → RRF
    SIMD-->>Agent: 🔍 Ranked results (~88µs)
```

### Performance: MCP-Native vs. Adapter Pattern

| Metric | Spector (in-process) | Typical MCP adapter |
|:---|:---|:---|
| **Architecture** | Engine + MCP in one JVM | Python → HTTP → DB → HTTP → agent |
| **Search latency** | **88µs** (SIMD) | 5–50ms (network round-trip) |
| **Memory recall** | **0.13ms** (fused scoring) | 50–200ms (Mem0/Letta/Zep) |
| **Tools** | **21** (6 engine + 15 cognitive) | 3–5 basic CRUD |
| **GC pressure** | **Zero** (Panama off-heap) | Full GC overhead |
| **Deployment** | `java -jar spector.jar` | Python + pip + DB + config |

> [!TIP]
> For full MCP integration details, tool schemas, and Claude Desktop configuration, see the dedicated [MCP Integration](mcp-integration.md) page.

---

## 📦 Module Diagram

```mermaid
graph LR
    subgraph "🔬 Core Layer"
        core["spector-core<br/><i>SIMD kernels</i>"]
        commons["spector-commons<br/><i>Config, chunkers, tokenizer</i>"]
    end

    subgraph "💾 Storage Layer"
        storage["spector-storage<br/><i>Panama MemorySegment stores</i>"]
    end

    subgraph "📊 Index Layer"
        index["spector-index<br/><i>HNSW + IVF-PQ + BM25</i>"]
    end

    subgraph "🔍 Query Layer"
        query["spector-query<br/><i>Hybrid orchestrator + RRF</i>"]
    end

    subgraph "🧠 Intelligence"
        embedapi["spector-embed-api<br/><i>EmbeddingProvider SPI</i>"]
        embedollama["spector-embed-ollama<br/><i>Ollama provider</i>"]
        gpu["spector-gpu<br/><i>Panama FFM + CUDA</i>"]
    end

    subgraph "📥 Pipelines"
        ingestion["spector-ingestion<br/><i>Ingest orchestration</i>"]
        rag["spector-rag<br/><i>RAG pipeline</i>"]
    end

    subgraph "⚡ Runtime & Interfaces"
        runtime["spector-runtime<br/><i>Unified context (memory + ingestion)</i>"]
        synapse["spector-synapse<br/><i>Armeria REST/gRPC/SSE server</i>"]
        mcp["spector-mcp<br/><i>MCP Server — Agent-native</i>"]
        cli["spector-cli<br/><i>spectorctl CLI</i>"]
        client["spector-client<br/><i>Java client SDK</i>"]
        spring["spector-spring<br/><i>Spring AI VectorStore</i>"]
    end

    subgraph "🧠 Cognitive Memory"
        memory["spector-memory<br/><i>Biologically-inspired agent memory</i>"]
    end

    subgraph "📈 Distribution"
        bench["spector-bench<br/><i>JMH benchmarks</i>"]
        dist["spector-dist<br/><i>Single fat JAR</i>"]
    end
```

> [!NOTE]
> **Index sub-modules:** `hnsw/` (graph-based ANN), `ivf/` (inverted file + posting lists), `pq/` (product quantizer, K-Means++, ADC), `bm25/` (keyword scoring + analyzers)

---

## 🔗 Dependency Graph

```mermaid
graph TD
    synapse["🌐 synapse"] --> runtime["⚡ runtime"]
    synapse --> mcp["🤖 mcp"]
    synapse --> metrics["📈 metrics"]
    mcp --> runtime
    mcp --> ingestion["📥 ingestion"]
    cli["🖥️ cli"] --> runtime
    cli --> client["📦 client"]

    runtime --> memory["🧠 memory"]
    runtime --> ingestion

    memory --> query["🔍 query"]
    memory --> index["📊 index"]
    memory --> storage["💾 storage"]
    memory --> embedapi["🧬 embed-api"]
    memory -.-> gpu["🎮 gpu"]
    memory --> rag["🤖 rag"]
    memory --> core["🔬 core"]

    metrics --> memory

    ingestion --> config["⚙️ config"]
    ingestion --> embedapi

    rag --> query
    rag --> index
    rag --> storage
    rag --> embedapi
    rag --> commons["📄 commons"]

    query --> index
    query --> commons
    index --> storage
    index --> config
    storage --> config
    storage --> core
    config --> core

    embedapi --> commons
    gpu --> core
    gpu --> storage

    dist["📦 dist"] --> mcp
    dist --> cli
    dist --> runtime

    spring["🌱 spring"] --> memory
    spring --> metrics
    bench["🧪 bench"] --> memory
```

> **Legend:** Solid arrows = compile dependency. Dotted arrow (`gpu`) = optional dependency.

**Dependency rules:**

| Path | Description |
|------|-------------|
| `runtime → memory + ingestion` | Composition root — wires all subsystems |
| `cli → runtime + client` | CLI with local batch (runtime) and remote (client) modes |
| `synapse → runtime` | Unified Armeria node: REST + gRPC + SSE + cluster coordination (incorporates former spector-node) |
| `mcp → runtime + ingestion` | MCP agent entry point (in-process, zero network) |
| `memory → ingestion` | Houses both `EngineIngestionTarget` and `CognitiveIngestionTarget` |
| `memory → rag` | RAG context assembly pipeline |
| `memory -.-> gpu` | Optional GPU acceleration |
| `memory → index, storage, core, embed-api` | Cognitive memory and HNSW/BM25 storage foundations |
| `dist → mcp + cli + runtime` | Fat JAR distribution |

!!! important
    **No circular dependencies.** `spector-memory` contains both engine search facades and cognitive stores. `SpectorRuntime` acts as the single composition root, keeping the API gateway (`spector-synapse`) decoupled from low-level storage.

---

## 📥 Data Flow: Ingest Path

```mermaid
sequenceDiagram
    participant Client as 👤 Client (CLI/MCP/REST)
    participant Runtime as ⚡ SpectorRuntime
    participant Handler as 📥 IngestionHandler
    participant Pipeline as 🔄 IngestionPipeline
    participant Embed as 🧠 ParallelEmbeddingPipeline
    participant Target as 💾 IngestionTarget
    participant Store as 💾 Storage (mmap)

    Client->>Runtime: runtime.ingestion().ingest(dir, pattern)
    Runtime->>Handler: Pre-configured pipeline + target
    Handler->>Handler: FileDiscoveryService.discover()
    loop Each file
        Handler->>Pipeline: pipeline.ingest(id, content)
        Pipeline->>Pipeline: TextChunker.chunk(content)
        Pipeline->>Embed: embed(chunkTexts) via virtual threads
        Embed-->>Pipeline: List<vector>
        loop Each chunk
            Pipeline->>Target: target.ingest(id, text, vector)
            Target->>Store: VectorStore + VectorIndex + KeywordIndex
        end
    end
    Store-->>Client: ✅ Indexed
```

1. **Client** calls `runtime.ingestion().ingest()` — all entry points use this
2. **IngestionHandler** delegates to a pre-configured `IngestionPipeline`
3. **IngestionPipeline** handles chunking (from config) and parallel embedding
4. **IngestionTarget** receives pre-embedded chunks — `EngineIngestionTarget` for SEARCH, `CognitiveIngestionTarget` for MEMORY
5. Each target handles its own downstream storage (VectorStore/HNSW or Quantize/TierRoute/WAL)

> [!TIP]
> `FileDiscoveryService` can be used independently for file discovery without any engine or runtime dependency.

---

## 🔍 Data Flow: Search Path

```mermaid
sequenceDiagram
    participant Client as 👤 Client
    participant Engine as ⚡ SpectorEngine
    participant QB as 🧭 Query Builder
    participant BM25 as 📝 BM25 Search
    participant HNSW as 🧠 HNSW Search
    participant RRF as 🧬 RRF Fusion
    participant LLM as 🤖 LLM Reranker

    Client->>Engine: Search (text + vector + topK)
    Engine->>QB: Auto-detect mode
    Note over QB: text only → KEYWORD<br/>vector only → VECTOR<br/>both → HYBRID
    par Parallel search on virtual threads
        QB->>BM25: Keyword search
        QB->>HNSW: Vector search
    end
    BM25->>RRF: Ranked results
    HNSW->>RRF: Ranked results
    RRF->>LLM: Fused top candidates
    LLM-->>Client: ✨ Final ranked results
```

1. **Query Builder** determines search mode from provided fields
2. **BM25** and **HNSW** searches run in parallel on virtual threads
3. **RRF Fusion** merges both ranked lists using `1/(k + rank)` scoring
4. Optional **LLM Reranker** rescores top candidates via Ollama

---

## 🤖 Data Flow: MCP Agent Path

```mermaid
sequenceDiagram
    participant Agent as 🤖 AI Agent (Claude/Cursor)
    participant MCP as 📡 MCP Transport (stdio / Streamable HTTP)
    participant Handler as 🔧 McpToolHandler
    participant Runtime as ⚡ SpectorRuntime
    participant Engine as 🔧 SpectorEngine
    participant SIMD as 🔬 SIMD Kernels

    Agent->>MCP: tools/call {"name": "engine_search", "arguments": {"query": "..."}}
    MCP->>Handler: EngineSearchTool.execute(runtime, args)
    Handler->>Runtime: runtime.search().query(text, topK)
    Runtime->>Engine: engine.search(query, topK)
    Engine->>SIMD: HNSW traversal (off-heap MemorySegment)
    SIMD-->>Engine: ScoredResult[] (~100µs)
    Engine-->>Runtime: SearchResponse
    Runtime-->>Handler: SpectorResult[]
    Handler-->>MCP: CallToolResult
    MCP-->>Agent: JSON-RPC response with search results
```

The MCP path routes through `SpectorRuntime` — the single composition root that holds both the search engine and optional cognitive memory. The MCP server wraps runtime handler calls with JSON-RPC transport. There is **zero network overhead** because everything runs in the same JVM process.

> [!TIP]
> For full MCP architecture details, tool schemas, and design patterns, see the dedicated [MCP Integration](mcp-integration.md) page.

---

## 🧵 Threading Model: Virtual Threads

Spector is designed from the ground up for Java virtual threads:

> [!TIP]
> **No `synchronized` blocks** anywhere in the codebase. All coordination uses `ReentrantLock` to avoid virtual thread pinning.

| Operation | Threading Strategy |
|-----------|-------------------|
| REST request handling | One virtual thread per request |
| Hybrid search | Parallel BM25 + HNSW via `StructuredTaskScope` |
| Bulk ingest | Virtual thread per document |
| Embedding generation | Batched across virtual threads |
| HNSW construction (>10K) | Virtual threads per core for parallel insertion |
| Distributed fan-out | Virtual thread per shard query |

### 📈 Scaling Results

At 50K docs with hybrid search (384-dim, production-realistic):

| Virtual Threads | Throughput | Scaling |
|-----------------|-----------|---------|
| 1 | 3,739 ops/s | 1.0× |
| 4 | 10,317 ops/s | **2.8×** |
| 8 | 11,812 ops/s | **3.2×** |
| 16 | 14,022 ops/s | **3.7×** |

> [!NOTE]
> Scaling depends on vector dimensions and workload type. 384-dim shows ~3.7× at 16 threads due to higher per-query memory bandwidth. Individual HNSW queries are inherently sequential (graph traversal data dependencies) — scaling comes from concurrent queries sharing CPU cores.

---

## 💾 Memory Model: Panama Off-Heap

All vector data lives off-heap using the Panama Foreign Function & Memory API:

```mermaid
graph TB
    subgraph "☕ JVM Heap (minimal)"
        HG["HNSW Graph<br/>(adjacency lists)"]
        BM["BM25 Index<br/>(inverted index)"]
        ES["Engine State<br/>(config, lifecycle)"]
    end

    subgraph "🧊 Off-Heap (Panama MemorySegment)"
        VS["Vector Store<br/>Contiguous float32, SIMD-aligned<br/>Zero-copy reads, no GC pressure"]
        QS["Quantized Store<br/>INT8 or PQ codes"]
        GM["GPU Device Memory<br/>CUDA via FFM"]
    end

    HG -.-> VS
    BM -.-> VS
    ES -.-> QS
    ES -.-> GM
```

**Benefits:**

- ✅ **Zero GC pressure** — Vectors never touch the garbage collector

- ✅ **Instant startup** — Memory-mapped files load via `mmap` syscall, no deserialization

- ✅ **SIMD-friendly layout** — Contiguous float32 arrays ready for Vector API operations

- ✅ **Explicit lifecycle** — `Arena`-scoped memory with deterministic cleanup

- ✅ **Memory efficiency** — Store billions of vectors limited only by disk/address space

### 📊 Storage Types

| Store | Location | Use Case |
|-------|----------|----------|
| `InMemoryVectorStore` | Off-heap (Arena) | Development, small datasets |
| `MmapVectorStore` | Memory-mapped file | Production, persistence |
| `QuantizedVectorStore` | Off-heap (INT8) | Memory-constrained deployments |
| `IvfPqStore` | Off-heap (PQ codes) | Billion-scale (32× compression) |

---

## 🌐 API Layer

```mermaid
graph TD
    subgraph "SpectorNode - Armeria Server, single port"
        CORS["CorsService decorator"]
        Auth["API Key decorator"]
        COMPRESS["EncodingService - gzip/brotli"]
        subgraph "ApiModule Registration"
            SE["🔍 SearchEndpoint"]
            IE["📥 IngestEndpoint"]
            RE["🤖 RagEndpoint"]
            DE["🗑️ DocumentEndpoint"]
            STE["📊 StatusEndpoint"]
            ESE["📡 EventStreamEndpoint"]
        end
        gRPC["gRPC Service<br/>inter-node fan-out"]
        HEALTH["💚 /health"]
        PROM["📊 /metrics"]
    end

    subgraph "Service Facades"
        SS["SearchService"]
        IS["IngestService"]
        RS["RagService"]
    end

    SE --> SS
    IE --> IS
    RE --> RS
    SS & IS --> EB["SpectorEventBus<br/>17 event types"]
    SS --> ENGINE["⚡ SpectorEngine"]
```

Every request runs on its own virtual thread. The Armeria server handles HTTP REST, gRPC, and SSE events on a single port. API endpoints are registered via the `ApiModule` factory pattern, enabling straightforward API versioning (`/api/v1`, `/api/v2`).

### Streaming via SSE

The `/api/v1/search/stream` endpoint uses Server-Sent Events to emit results progressively. The `/api/v1/events` endpoint provides a live event stream where clients can subscribe to search, ingest, cluster, MCP, and engine events with optional category filtering.

---

## 🔗 See Also

- [Core Concepts](core-concepts.md) — Algorithms and data structures in detail

- [Distributed Mode](distributed-mode.md) — Multi-node clustering architecture

- [GPU Acceleration](gpu-acceleration.md) — CUDA kernel integration via Panama

- [Performance Tuning](../operations/performance-tuning.md) — Optimizing for your workload
