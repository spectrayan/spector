# Modules

Spector is organized as a multi-module Maven project. Each module has a focused responsibility, clear API boundaries, and minimal cross-module coupling.

---

## Architecture Hierarchy

```mermaid
graph TB
    subgraph nucleus["nucleus/ (Foundation & Observability)"]
        commons["spector-commons<br/><i>Chunkers, tokenizer</i>"]
        core["spector-core<br/><i>SIMD kernels</i>"]
        config["spector-config<br/><i>SpectorConfig + YAML</i>"]
        storage["spector-storage<br/><i>Panama MemorySegment</i>"]
        events["spector-events<br/><i>Telemetry event bus</i>"]
        metrics["spector-metrics<br/><i>Micrometer + TelemetryBus</i>"]
        testsupport["spector-test-support<br/><i>Integration test harnesses</i>"]
    end

    subgraph memory["memory/ (Cognitive Memory Engine)"]
        spectormemory["spector-memory<br/><i>Cognitive store & search facade</i>"]
        index["spector-index<br/><i>HNSW + IVF-PQ + BM25</i>"]
        query["spector-query<br/><i>Hybrid + RRF + rerank</i>"]
        gpu["spector-gpu<br/><i>CUDA via Panama FFM</i>"]
        providerApi["spector-provider-api<br/><i>Provider SPI</i>"]
        providers["spector-providers<br/><i>AI Providers</i>"]
        ingestion["spector-ingestion<br/><i>File ingest pipeline</i>"]
        rag["spector-rag<br/><i>RAG pipeline</i>"]
    end

    subgraph synapse["synapse/ (API, Runtime & CLI Gateways)"]
        runtime["spector-runtime<br/><i>Composition root</i>"]
        synapseapp["spector-synapse<br/><i>Armeria REST/gRPC/SSE server</i>"]
        mcp["spector-mcp<br/><i>MCP Server (stdio/HTTP)</i>"]
        cli["spector-cli<br/><i>spectorctl CLI</i>"]
        client["spector-client<br/><i>Java SDK</i>"]
        spring["spector-spring<br/><i>Spring AI VectorStore</i>"]
        dist["spector-dist<br/><i>Fat JAR distribution</i>"]
    end

    subgraph cortex["cortex/ (UI Dashboard)"]
        spectorcortex["spector-cortex<br/><i>Angular 22 neural UI</i>"]
    end

    subgraph bench["bench/ (Performance)"]
        spectorbench["spector-bench<br/><i>JMH benchmarks</i>"]
    end

    nucleus --> memory --> synapse
    synapse --> cortex
    memory -.-> bench
```

---

## Module Dependency Graph

```mermaid
graph TD
    synapseapp["🌐 synapse"] --> runtime["⚡ runtime"]
    synapseapp --> mcp["🤖 mcp"]
    synapseapp --> metrics["📈 metrics"]
    synapseapp --> events["📡 events"]
    
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
    metrics --> events

    events --> commons["📄 commons"]

    cortex["🧠 cortex"] -.->|SSE| synapseapp

    ingestion --> config["⚙️ config"]
    ingestion --> embedapi

    rag --> query
    rag --> index
    rag --> storage
    rag --> embedapi

    query --> index
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

> **Legend:** Solid arrows = compile dependency. Dotted arrows = optional/runtime dependency (`gpu` = optional Maven dep, `cortex` = connects via SSE at runtime).

!!! important "Architecture"
    `spector-ingestion` defines the `IngestionPipeline` and `IngestionTarget` interface. `spector-memory` depends on it to implement both `EngineIngestionTarget` and `CognitiveIngestionTarget`. The entry points route requests to `SpectorRuntime`, which acts as the composition root.

---

## Architecture: Entry Points → Runtime → Subsystems

All entry points (MCP, CLI, Server) route through `SpectorRuntime`:

```mermaid
graph TD
    cli["🖥️ spector-cli<br/><i>SpectorCtl</i>"]
    mcp["🤖 spector-mcp<br/><i>SpectorMcpMain</i>"]
    synapseapp["🌐 spector-synapse<br/><i>SynapseApplication (Armeria)</i>"]

    cli --> runtime
    mcp --> runtime
    synapseapp --> runtime

    runtime["⚡ SpectorRuntime<br/><i>Composition Root</i>"]

    runtime --> sh["SearchHandler<br/><i>mode-aware search</i>"]
    runtime --> ih["IngestionHandler<br/><i>delegates to IngestionPipeline</i>"]

    sh --> memory["SpectorMemory (under memory/)"]
    ih --> pipeline["IngestionPipeline<br/><i>chunk → embed → store</i>"]
    pipeline --> engineTarget["EngineIngestionTarget<br/><i>SEARCH mode (under memory/)</i>"]
    pipeline --> memTarget["CognitiveIngestionTarget<br/><i>MEMORY mode (under memory/)</i>"]
```

**SpectorRuntime** is a thin composition root — it creates and wires subsystems but contains no business logic. Each handler owns its domain:

| Handler | Responsibility | Routes to |
|---------|---------------|-----------|
| `SearchHandler` | Mode-aware search & retrieval | SpectorMemory (SEARCH or MEMORY modes) |
| `IngestionHandler` | Delegates to unified `IngestionPipeline` | Pipeline → `EngineIngestionTarget` or `CognitiveIngestionTarget` |

---

## Module Overview

### Foundation Layer

| Module | Description |
|:---|:---|
| [spector-commons](spector-commons.md) | Shared utilities — concurrent primitives, I/O helpers |
| [spector-core](spector-core.md) | Core abstractions — quantization, SIMD, similarity functions |
| [spector-config](spector-config.md) | Configuration — `SpectorProperties`, `SpectorConfigFactory`, YAML loading |
| [spector-storage](spector-storage.md) | Persistent storage — memory-mapped files, arena management |

### Embedding Layer

| Module | Description |
|:---|:---|
| [spector-provider-api](spector-provider-api.md) | LLM and embedding provider SPI — model-agnostic interfaces |
| [spector-providers](spector-providers.md) | Out-of-the-box LLM and embedding providers (Ollama, OpenAI, Google, Anthropic, Mistral, Azure) |

### Search Layer

| Module | Description |
|:---|:---|
| [spector-index](spector-index.md) | Vector indexing — HNSW, IVF, brute-force |
| [spector-query](spector-query.md) | Query processing — parsing, planning, execution |
| [spector-gpu](spector-gpu.md) | GPU acceleration — Panama FFM bindings |

### Intelligence Layer

| Module | Description |
|:---|:---|
| [spector-rag](spector-rag.md) | RAG pipeline — retrieval-augmented generation |
| [spector-ingestion](spector-ingestion.md) | Unified ingestion pipeline — `IngestionPipeline` (builder), `IngestionTarget` interface, `FileDiscoveryService` |
| [spector-memory](spector-memory.md) | Flagship cognitive memory and search engine — off-heap HNSW, BM25 indices, and neuro-inspired scoring/consolidation (incorporates former spector-engine search facade) |

### Runtime Layer

| Module | Description |
|:---|:---|
| [spector-runtime](spector-runtime.md) | Composition root — wires cognitive memory + ingestion pipeline, exposing `SearchHandler` and `IngestionHandler` |
| [spector-mcp](spector-mcp.md) | MCP server — Model Context Protocol integration via stdio/HTTP |
| [spector-synapse](spector-synapse.md) | API Gateway and central nervous system — Armeria HTTP REST + gRPC + SSE events + cluster coordination (incorporates former spector-node) |

### Client Layer

| Module | Description |
|:---|:---|
| [spector-cli](spector-cli.md) | CLI tool — `spectorctl` with remote (HTTP) and local batch (runtime) modes |
| [spector-client](spector-client.md) | Java client — programmatic HTTP API access |
| [spector-spring](spector-spring.md) | Spring AI integration — auto-configuration |

### Infrastructure

| Module | Description |
|:---|:---|
| [spector-events](spector-events.md) | Telemetry — decoupled event bus (`TelemetryBus`, `TelemetryScope`, 12 event types) |
| [spector-metrics](spector-metrics.md) | Metrics — Micrometer + TelemetryBus instrumentation |
| [spector-cortex](spector-cortex.md) | ⚠️ **Moved to [spector-enterprise](https://github.com/spectrayan/spector-enterprise)** — Angular 22 neural dashboard |
| [spector-bench](spector-bench.md) | Benchmarks — JMH performance testing |
| [spector-dist](spector-dist.md) | Distribution — single fat JAR packaging |
