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
    subgraph Clients["Client Interfaces & SDKs"]
        claude["🤖 Claude Desktop"]
        cursor["✏️ Cursor / Windsurf"]
        agents["🦾 Autonomous Agents"]
        py["🐍 Python SDK"]
        ts["🔷 TypeScript SDK"]
        sdk["☕ Java Client SDK"]
        spring["🌱 Spring AI"]
        cli["🖥️ spector CLI"]
        rest["🌐 REST / gRPC"]
    end

    subgraph Transport["Synapse Application Layer"]
        mcp["MCP Server<br/><i>stdio · Streamable HTTP · 37+ tools</i>"]
        armeria["Armeria Gateway :7070<br/><i>REST + gRPC + SSE streaming</i>"]
        persona["Persona Enactment<br/><i>Dual-process cognitive appraisal</i>"]
    end

    subgraph Engine["Spector Memory Engine"]
        subgraph Pathways["Cognitive Pathways"]
            rem["Remember Pathway<br/><i>Surprise · Flashbulb · Dedup</i>"]
            rec["Recall Pathway<br/><i>6-Phase SIMD Fused Scoring</i>"]
            ref["Reflect Pathway<br/><i>Sleep Consolidation · Replay</i>"]
            drm["Dream Pathway<br/><i>Counterfactual Simulation</i>"]
        end

        subgraph Memory["4-Tier Cortex & Graphs"]
            cortex["4-Tier Cortex<br/><i>Working · Episodic · Semantic · Procedural</i>"]
            hebbian["Cognitive Graphs<br/><i>Hebbian · Temporal · HyperEntity</i>"]
            decay["Memory Decay<br/><i>Power-law forgetting · Bjork strength</i>"]
        end

        subgraph Search["Search & Retrieval Stack"]
            hybrid["Hybrid Retrieval<br/><i>BM25 + Dense + SPLADE</i>"]
            hnsw["HNSW Graph Index<br/><i>M=16, ef=200</i>"]
            colbert["ColBERT v2 Reranking<br/><i>Late-interaction MaxSim</i>"]
        end
    end

    subgraph Kernel["⚡ Memory Kernel (spector-kernel — Zero GC)"]
        direction TB
        ns["NamespaceKernel Facade"]
        bundles["V4 Single-VMA Bundles<br/><i>runtime.bundle · partition.bundle · identity.bundle</i>"]
        shapes["8 Sealed Memory Shapes<br/><i>Record · Append · Graph · Chain · Hash · Insula</i>"]
        panama["Panama FFM Storage<br/><i>Shared Arena · MemorySegment · mmap</i>"]
        simd["Hardware SIMD Acceleration<br/><i>Vector API · AVX2 / AVX-512 / NEON</i>"]
        gpu["GPU Acceleration<br/><i>CUDA via Panama FFM</i>"]
    end

    subgraph Observe["Observability & Telemetry"]
        events["TelemetryBus<br/><i>Event streams</i>"]
        metrics["Micrometer<br/><i>Prometheus export</i>"]
        sse["SSE Event Stream<br/><i>Cortex 3D Galaxy</i>"]
    end

    claude & cursor & agents --> mcp
    py & ts & sdk & spring --> armeria
    cli & rest --> armeria
    mcp & armeria & persona --> Pathways

    Pathways --> Memory & Search
    Memory & Search --> ns
    ns --> bundles --> shapes --> panama
    panama --> simd
    gpu -.->|batch compute| simd

    Engine --> events
    events --> metrics & sse

    style Clients fill:#5b6abf,stroke:#e94560,color:#fff
    style Transport fill:#4a6fa5,stroke:#3b82f6,color:#fff
    style Engine fill:#3b82f6,stroke:#7c3aed,color:#fff
    style Kernel fill:#1e293b,stroke:#0f172a,color:#fff
    style Pathways fill:#2563eb,stroke:#1d4ed8,color:#fff
    style Memory fill:#1d4ed8,stroke:#1e40af,color:#fff
    style Search fill:#1e40af,stroke:#1e3a8a,color:#fff
    style Observe fill:#5b6abf,stroke:#7c3aed,color:#fff
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
        custom["🦾 Autonomous Multi-Agents"]
    end

    subgraph MCP["MCP Server — Dual Transport · JSON-RPC 2.0"]
        transport["Transport Layer<br/><i>stdio (stdin/stdout) for CLI agents<br/>Streamable HTTP (/mcp) for remote agents</i>"]
        registry["SpectorToolRegistry<br/><i>37+ tools · dynamic route dispatch</i>"]
        handler["McpToolHandler<br/><i>Base class · thread-safe · virtual threads</i>"]

        subgraph Mem["1. Memory Tier Operations (16 Tools)"]
            m1["memory_remember — Store with importance & tags"]
            m2["memory_recall — Fused SIMD scoring recall"]
            m3["memory_scratchpad — Working-memory scratchpad"]
            m4["memory_reinforce — Outcome feedback (+/-)"]
            m5["memory_forget — Tombstone intentional forgetting"]
            m6["memory_status — Per-tier statistics & health"]
            m7["memory_introspect — Metamemory self-reflection"]
            m8["memory_suppress — Temporary recall suppression"]
            m9["memory_resolve — Mark resolved/unresolved"]
            m10["memory_reminder — Proactive intent triggers"]
            m11["memory_why_not — Explain recall misses"]
            m12["memory_compute_importance — Pre-ingest scoring"]
            m13["memory_inspect — Full cognitive X-ray"]
            m14["memory_export — Bulk JSON memory export"]
            m15["memory_browse — Browse by tag/tier filter"]
            m16["memory_salience — Inspect & tune salience profile"]
        end

        subgraph GraphContext["2. Graph & Multi-Evidence Retrieval (7 Tools)"]
            g1["memory_graph_recall — Spreading activation graph walk"]
            g2["memory_context_pack — Assembled agent prompt pack"]
            g3["memory_fact_history — Temporal chain evolution"]
            g4["memory_persona_context — Soul-aligned contextual injection"]
            g5["memory_multi_evidence_recall — Multi-vector consensus"]
            g6["vector_search — Pure vector cosine similarity"]
            g7["memory_express — Natural language memory synthesis"]
        end

        subgraph NamespaceRBAC["3. Namespace & Multi-Tenancy (9 Tools)"]
            n1["namespace_create — Provision isolated namespace"]
            n2["namespace_list — Enumerate active namespaces"]
            n3["namespace_info — Inspect V4 bundle layout & size"]
            n4["namespace_switch — Set active session namespace"]
            n5["namespace_set_default — Update default namespace"]
            n6["namespace_delete — Safely purge namespace files"]
            n7["namespace_grant — RBAC access delegation"]
            n8["namespace_revoke — Revoke access permissions"]
            n9["namespace_list_grants — Audit security grants"]
        end

        subgraph SoulPolicy["4. Agent Soul & Persona Enactment (5 Tools)"]
            s1["update_agent_soul — Mutate agent persona & dogmas"]
            s2["persona_enact — Dual-process cognitive appraisal"]
            s3["account_introspect — Account & tenant introspection"]
            s4["invoke_connector_route — External data connector dispatch"]
            s5["send_notification — Dispatch proactive agent alerts"]
        end
    end

    subgraph Core["In-Process Engine — Zero Network Overhead"]
        pathways["Cognitive Pathways<br/><i>Remember · Recall · Reflect</i>"]
        kernel["Sealed Memory Kernel<br/><i>V4 Bundles · 8 Shapes · Panama FFM</i>"]
    end

    Agents -->|stdio / HTTP| transport --> registry --> handler
    handler --> Mem & GraphContext & NamespaceRBAC & SoulPolicy
    Mem & GraphContext & NamespaceRBAC & SoulPolicy --> pathways --> kernel

    style Agents fill:#5b6abf,stroke:#e94560,color:#fff
    style MCP fill:#4a6fa5,stroke:#3b82f6,color:#fff
    style Mem fill:#3b82f6,stroke:#2563eb,color:#fff
    style GraphContext fill:#2563eb,stroke:#1d4ed8,color:#fff
    style NamespaceRBAC fill:#1d4ed8,stroke:#1e40af,color:#fff
    style SoulPolicy fill:#1e40af,stroke:#1e3a8a,color:#fff
    style Core fill:#1e293b,stroke:#0f172a,color:#fff
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
    SIMD-->>Agent: 📋 Ranked memories (ultra-fast)

    Agent->>MCP: tools/call {"name": "memory_introspect", ...}
    MCP->>Tools: Route → MemoryIntrospectTool
    Tools->>Runtime: memory().introspect(topic)
    Runtime->>SIMD: Confidence + knowledge-gap analysis over tiers
    SIMD-->>Agent: 🔍 Knowledge report (~0.2ms)
```

### Performance: In-Process vs. External Adapters

| Metric | Spector (in-process) | Typical MCP adapter |
|:---|:---|:---|
| **Architecture** | Engine + MCP in one JVM | Python → HTTP → DB → HTTP → agent |
| **Search latency** | **88µs** (SIMD) | 5–50ms (network round-trip) |
| **Memory recall** | **Ultra-low latency** (fused scoring) | 50–200ms (Mem0/Letta/Zep) |
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

1. **Recall Pathway** receives options (`TextSearchMode`, `RecallMode`, etc.)
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
> For full MCP architecture details and tool schemas, see the dedicated [MCP Integration](mcp-integration.md) page.

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

Every request runs on its own virtual thread. The Armeria server handles HTTP REST, gRPC, and SSE events on a single port. API endpoints are registered via `ApiModule` components, enabling straightforward API versioning (`/api/v1`, `/api/v2`).

### Streaming via SSE

The `/api/v1/search/stream` endpoint uses Server-Sent Events to emit results progressively. The `/api/v1/events` endpoint provides a live event stream where clients can subscribe to search, ingest, cluster, MCP, and engine events with optional category filtering.

---

## 🔗 See Also

- [Core Concepts](core-concepts.md) — Algorithms and data structures in detail

- [Distributed Mode](distributed-mode.md) — Multi-node clustering architecture

- [GPU Acceleration](gpu-acceleration.md) — CUDA kernel integration via Panama

- [Performance Tuning](../operations/performance-tuning.md) — Optimizing for your workload
