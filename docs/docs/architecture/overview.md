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
        mcp["MCP Server<br/><i>stdio · Streamable HTTP · 16 cognitive memory tools</i>"]
        armeria["Armeria Server :7070<br/><i>REST + gRPC + SSE streaming</i>"]
    end

    subgraph Engine["Spector Engine"]
        runtime["SpectorMemory<br/><i>Core Cognitive Engine</i>"]

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
        lib["SpectorMemory API<br/><i>In-process · zero-network · drop-in JAR</i>"]
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
        registry["SpectorToolRegistry<br/><i>16 tools · auto-registration</i>"]
        handler["McpToolHandler<br/><i>Base class · thread-safe · virtual threads</i>"]

        subgraph Mem["Cognitive Memory Tools — 16"]
            m1["memory_remember — Store with importance & tags"]
            m2["memory_recall — Fused SIMD scoring recall"]
            m3["memory_scratchpad — Working-memory scratch space"]
            m4["memory_reinforce — Outcome feedback +/-"]
            m5["memory_forget — Intentional forgetting"]
            m6["memory_status — Per-tier statistics"]
            m7["memory_introspect — Self-reflection"]
            m8["memory_suppress — Temporary suppression"]
            m9["memory_resolve — Mark resolved/unresolved"]
            m10["memory_reminder — Proactive reminders"]
            m11["memory_why_not — Explain recall misses"]
            m12["memory_compute_importance — Pre-ingestion scoring"]
            m13["memory_inspect — Full cognitive X-ray"]
            m14["memory_export — Bulk memory export"]
            m15["memory_browse — Browse by tag/tier"]
            m16["memory_salience — Tune salience profile"]
        end
    end

    subgraph Core["In-Process Engine — Zero Network Overhead"]
        runtime["SpectorMemory<br/><i>Engine + Memory + Ingestion</i>"]
        simd["SIMD Kernels<br/><i>AVX2/512 · ~100µs per search</i>"]
        panama["Panama Off-Heap<br/><i>Zero GC · mmap storage</i>"]
    end

    Agents -->|stdio / HTTP| transport --> registry --> handler
    handler --> Mem
    Mem --> runtime --> simd --> panama

    style Agents fill:#5b6abf,stroke:#e94560,color:#fff
    style MCP fill:#4a6fa5,stroke:#3b82f6,color:#fff
    style Mem fill:#7c3aed,stroke:#e94560,color:#fff
    style Core fill:#5b6abf,stroke:#e94560,color:#fff
```

### Agent Interaction Flow

```mermaid
sequenceDiagram
    participant Agent as 🤖 AI Agent
    participant MCP as 📡 MCP Server
    participant Tools as 🔧 ToolRegistry
    participant Memory as 🧠 SpectorMemory
    participant SIMD as 🔬 SIMD (off-heap)

    Note over Agent,SIMD: Single JVM process — no HTTP, no gRPC, no serialization

    Agent->>MCP: tools/call {"name": "memory_remember", ...}
    MCP->>Tools: Route → MemoryRememberTool
    Tools->>Memory: remember(text, tags, importance)
    Memory->>SIMD: Embed → HNSW insert → tier assign
    SIMD-->>Agent: ✅ memoryId + tier (~1ms)

    Agent->>MCP: tools/call {"name": "memory_recall", ...}
    MCP->>Tools: Route → MemoryRecallTool
    Tools->>Memory: recall(query, topK)
    Memory->>SIMD: Fused scoring: sim × importance × decay
    SIMD-->>Agent: 📋 Ranked memories (~0.13ms)

    Agent->>MCP: tools/call {"name": "memory_introspect", ...}
    MCP->>Tools: Route → MemoryIntrospectTool
    Tools->>Runtime: memory().introspect(topic)
    Runtime->>SIMD: Confidence + knowledge-gap analysis over tiers
    SIMD-->>Agent: 🔍 Knowledge report (~0.2ms)
```

### Performance: MCP-Native vs. Adapter Pattern

| Metric | Spector (in-process) | Typical MCP adapter |
|:---|:---|:---|
| **Architecture** | Engine + MCP in one JVM | Python → HTTP → DB → HTTP → agent |
| **Search latency** | **88µs** (SIMD) | 5–50ms (network round-trip) |
| **Memory recall** | **0.13ms** (fused scoring) | 50–200ms (Mem0/Letta/Zep) |
| **Tools** | **16** (cognitive memory tools) | 3–5 basic CRUD |
| **GC pressure** | **Zero** (Panama off-heap) | Full GC overhead |
| **Deployment** | `java -jar spector.jar` | Python + pip + DB + config |

> [!TIP]
> For full MCP integration details, tool schemas, and Claude Desktop configuration, see the dedicated [MCP Integration](mcp-integration.md) page.

---

## 📦 Module Diagram

```mermaid
graph LR
    subgraph "🔬 Foundation & Acceleration (nucleus/)"
        core["spector-core<br/><i>Compute SPIs & Quantization</i>"]
        cpu["spector-cpu<br/><i>Java 25 SIMD Kernels</i>"]
        gpu["spector-gpu<br/><i>Panama FFM + CUDA GPU</i>"]
        hdc["spector-hdc<br/><i>Hyperdimensional vectors</i>"]
        index["spector-index<br/><i>HNSW + SpectorIndex + BM25</i>"]
        commons["spector-commons<br/><i>Error codes & concurrency</i>"]
        config["spector-config<br/><i>SpectorProperties & YAML</i>"]
        events["spector-events<br/><i>Telemetry event bus</i>"]
        testsupport["spector-test-support<br/><i>Harnesses & mocks</i>"]
    end

    subgraph "🧠 Cognitive Memory Layer (memory/)"
        memory["spector-memory<br/><i>Bundle Kernel, 4-Tier Memory & Daemons</i>"]
        providerapi["spector-provider-api<br/><i>Provider SPI</i>"]
        providers["spector-providers<br/><i>AI Providers (Ollama, OpenAI, ONNX)</i>"]
        ingestion["spector-ingestion<br/><i>Sensory & file ingest pipeline</i>"]
        inspect["spector-inspect<br/><i>Bundle inspection CLI</i>"]
        metrics["spector-metrics<br/><i>Micrometer + Prometheus</i>"]
    end

    subgraph "⚡ Nervous System & Gateways (synapse/)"
        synapse["spector-synapse<br/><i>Spring Boot 4 REST/SSE & Chat Graph</i>"]
        connector["spector-connector<br/><i>Apache Camel connectors</i>"]
        mcp["spector-mcp<br/><i>MCP Server — Agent-native</i>"]
        cli["spector-cli<br/><i>spectorctl CLI & standalone spector.jar</i>"]
        spring["spector-spring<br/><i>Spring AI VectorStore</i>"]
        batch["spector-batch<br/><i>Batch migration engine</i>"]
    end

    subgraph "📈 Performance & Validation (bench/)"
        bench["spector-bench<br/><i>JMH benchmarks & cognitive eval</i>"]
    end
```

> [!NOTE]
> **Index implementations in `spector-index`:** `hnsw/` (graph-based ANN, Quantized HNSW), `spectrum/` (SpectorIndex, multi-tier sharding), `bm25/` (keyword scoring + analyzers), `splade/` (sparse neural representations).

---

## 🔗 Dependency Graph

```mermaid
graph TD
    synapse["🌐 synapse"] --> mcp["🤖 mcp"]
    synapse --> connector["🔌 connector"]
    synapse --> metrics["📈 metrics"]
    synapse --> events["📡 events"]
    synapse --> memory["🧠 memory"]

    mcp --> memory
    mcp --> ingestion["📥 ingestion"]
    cli["🖥️ cli"] --> memory
    cli --> mcp
    cli --> ingestion

    memory --> index["📊 index"]
    memory --> core["🔬 core"]
    memory --> cpu["⚡ cpu"]
    memory --> config["⚙️ config"]
    memory --> providerapi["🧬 provider-api"]

    index --> core
    index --> config
    index --> commons["📄 commons"]

    gpu --> index
    gpu --> core
    gpu --> commons

    cpu --> core
    cpu --> commons

    metrics --> memory
    metrics --> events

    connector --> ingestion
    connector --> providerapi

    spring["🌱 spring"] --> memory
    spring --> metrics
    bench["🧪 bench"] --> memory
    bench --> providers["🤖 providers"]
```

> **Legend:** Solid arrows = compile dependency. Dotted arrow (`bench`) = benchmark execution dependency.

**Dependency rules:**

| Path | Description |
|------|-------------|
| `runtime → memory + ingestion` | Composition root — wires all subsystems |
| `cli → runtime + client` | CLI with local batch (runtime) and remote (client) modes |
| `synapse → runtime` | Unified Armeria node: REST + gRPC + SSE + cluster coordination (incorporates former spector-node) |
| `mcp → runtime + ingestion` | MCP agent entry point (in-process, zero network) |
| `memory → ingestion` | Houses both `EngineIngestionTarget` and `CognitiveIngestionTarget` |
| `memory → index, events, commons` | Cognitive memory and HNSW/BM25 storage foundations |
| `synapse → cli, mcp, spring` | Integration layer (CLI, MCP, Spring AI) |

!!! important
    **No circular dependencies.** `spector-memory` contains both vector search and cognitive memory stores, keeping the API gateway (`spector-synapse`) decoupled from low-level storage.

---

## 📥 Data Flow: Ingest Path

```mermaid
sequenceDiagram
    participant Client as 👤 Client (CLI/MCP/REST)
    participant Pipeline as 🔄 IngestionPipeline
    participant Embed as 🧠 ParallelEmbeddingPipeline
    participant Target as 💾 IngestionTarget
    participant Store as 💾 Storage (mmap)

    Client->>Pipeline: pipeline.ingest(file)
    Pipeline->>Embed: generateEmbeddings()
    Embed-->>Pipeline: dense + sparse vectors
    Pipeline->>Target: target.store(chunk)
    Target->>Store: write to off-heap MemorySegment
    loop Each chunk
        Pipeline->>Pipeline: TextChunker.chunk(content)
        Pipeline->>Embed: embed(chunkTexts) via virtual threads
        Embed-->>Pipeline: List<vector>
        Pipeline->>Target: target.ingest(id, text, vector)
        Target->>Store: VectorStore + VectorIndex
    end
    Store-->>Client: ✅ Indexed
```

1. **Client** calls `pipeline.ingest()` — unified across CLI, MCP, and application code
2. **IngestionPipeline** handles chunking (from config) and parallel embedding
3. **IngestionTarget** receives pre-embedded chunks — storing directly in `SpectorMemory`
4. Downstream storage writes to off-heap memory and indexes with HNSW/BM25

> [!TIP]
> `FileDiscoveryService` can be used independently for file discovery without any engine dependency.

---

## 🔍 Data Flow: Search Path

```mermaid
sequenceDiagram
    participant Client as 👤 Client
    participant Memory as 🧠 SpectorMemory
    participant Pipeline as ⚙️ RecallPipeline
    participant BM25 as 📝 BM25 Search
    participant HNSW as 🧠 Dense HNSW
    participant Sparse as 📈 Sparse (SPLADE)
    participant RRF as 🧬 RRF Fusion
    participant Rerank as 🚀 ColBERT Rerank
    participant Graph as 🔗 Graph Expansion

    Client->>Memory: recall(query, options)
    Memory->>Pipeline: execute(query, options)
    par Parallel first-stage retrieval on virtual threads
        Pipeline->>BM25: exact term matching
        Pipeline->>HNSW: dense semantic search
        Pipeline->>Sparse: learned sparse search
    end
    BM25 & HNSW & Sparse->>RRF: Rank merge
    RRF->>Rerank: Token-level late interaction MaxSim
    Rerank->>Graph: Multi-hop graph expansion & gating
    Graph-->>Client: ✨ Final cognitive memories
```

1. **Recall Pipeline** receives options (`TextSearchMode`, `RecallMode`, etc.)
2. **Dense Vector, BM25, and Sparse (SPLADE)** searches run in parallel on virtual threads
3. **RRF Fusion** merges the ranked lists using reciprocal rank scores
4. **ColBERT v2 Reranking** scores the top candidates using SIMD MaxSim operations
5. **Graph Expansion** traverses Hebbian/Entity/Temporal edges for neighbor expansion

---

## 🤖 Data Flow: MCP Agent Path

```mermaid
sequenceDiagram
    participant Agent as 🤖 AI Agent (Claude/Cursor)
    participant MCP as 📡 MCP Transport (stdio / Streamable HTTP)
    participant Handler as 🔧 McpToolHandler
    participant Memory as 🧠 SpectorMemory
    participant SIMD as 🔬 SIMD Kernels

    Agent->>MCP: tools/call {"name": "memory_recall", "arguments": {"query": "..."}}
    MCP->>Handler: MemoryRecallTool.execute(args)
    Handler->>Memory: recall(query, options)
    Memory->>SIMD: 6-phase scoring + Panama off-heap reads
    SIMD-->>Memory: CognitiveResult[] (~130µs)
    Memory-->>Handler: List<CognitiveResult>
    Handler-->>MCP: CallToolResult
    MCP-->>Agent: JSON-RPC response with recalled memories
```

The MCP path operates directly against `SpectorMemory`. The MCP server wraps tool handler calls with JSON-RPC transport. There is **zero network overhead** because everything runs in the same JVM process.

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

    subgraph "REST Controller Layer"
        MC["MemoryController<br/>/api/v1/memory/*"]
        SC["SystemController<br/>/api/v1/system/*"]
        HC["HealthController<br/>/api/v1/system/*"]
    end

    subgraph "Service Layer"
        MS["MemoryService"]
    end

    subgraph "Core Engine"
        SM["SpectorMemory"]
    end

    MC & SC & HC --> MS
    MS --> SM
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
