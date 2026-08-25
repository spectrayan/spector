# Modules

Spector is organized as a multi-module Maven project. Each module has a focused responsibility, clear API boundaries, and minimal cross-module coupling.

---

## Architecture Hierarchy

```mermaid
graph TB
    subgraph nucleus["nucleus/ (Foundation & Acceleration)"]
        commons["spector-commons<br/><i>Error codes & utilities</i>"]
        core["spector-core<br/><i>Compute SPIs & quantization</i>"]
        cpu["spector-cpu<br/><i>Java 25 SIMD acceleration</i>"]
        gpu["spector-gpu<br/><i>Panama FFM + CUDA GPU</i>"]
        hdc["spector-hdc<br/><i>Hyperdimensional vectors</i>"]
        index["spector-index<br/><i>HNSW + SpectorIndex + BM25</i>"]
        config["spector-config<br/><i>SpectorConfig + YAML</i>"]
        events["spector-events<br/><i>Telemetry event bus</i>"]
        testsupport["spector-test-support<br/><i>Test harnesses & mocks</i>"]
    end

    subgraph memory["memory/ (Cognitive Memory Engine)"]
        spectormemory["spector-memory<br/><i>Bundle Kernel, Recall Pipeline & Daemons</i>"]
        providerApi["spector-provider-api<br/><i>Provider SPI</i>"]
        providers["spector-providers<br/><i>AI Providers (Ollama, OpenAI, ONNX)</i>"]
        ingestion["spector-ingestion<br/><i>File & sensory ingest pipeline</i>"]
        inspect["spector-inspect<br/><i>Bundle inspection tool</i>"]
        metrics["spector-metrics<br/><i>Micrometer + Prometheus metrics</i>"]
    end

    subgraph synapse["synapse/ (API, Runtime & Gateways)"]
        runtime["spector-runtime<br/><i>Composition root</i>"]
        synapseapp["spector-synapse<br/><i>Armeria REST/gRPC/SSE & Chat Graph</i>"]
        connector["spector-connector<br/><i>Camel data connectors</i>"]
        mcp["spector-mcp<br/><i>MCP Server (stdio/SSE)</i>"]
        cli["spector-cli<br/><i>spectorctl CLI</i>"]
        client["spector-client<br/><i>Java Client SDK</i>"]
        spring["spector-spring<br/><i>Spring AI VectorStore</i>"]
        batch["spector-batch<br/><i>Batch migration engine</i>"]
        dist["spector-dist<br/><i>Fat JAR distribution</i>"]
    end

    subgraph bench["bench/ (Performance & Validation)"]
        spectorbench["spector-bench<br/><i>JMH & cognitive benchmarks</i>"]
    end

    nucleus --> memory --> synapse
    synapse --> bench
    memory -.-> bench
```

---

## Module Dependency Graph

```mermaid
graph TD
    synapseapp["🌐 synapse"] --> runtime["⚡ runtime"]
    synapseapp --> mcp["🤖 mcp"]
    synapseapp --> connector["🔌 connector"]
    synapseapp --> metrics["📈 metrics"]
    synapseapp --> events["📡 events"]
    
    mcp --> runtime
    mcp --> ingestion["📥 ingestion"]
    cli["🖥️ cli"] --> runtime
    cli --> client["📦 client"]

    runtime --> memory["🧠 memory"]
    runtime --> ingestion
    runtime --> providers["🤖 providers"]

    memory --> index["📊 index"]
    memory --> core["🔬 core"]
    memory --> cpu["⚡ cpu"]
    memory --> config["⚙️ config"]
    memory --> providerApi["🧬 provider-api"]

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
    connector --> providerApi

    dist["📦 dist"] --> mcp
    dist --> cli
    dist --> runtime

    spring["🌱 spring"] --> memory
    spring --> metrics
    bench["🧪 bench"] --> memory
    bench --> providers
```

> **Legend:** Solid arrows = compile dependency. Dotted arrows = optional/benchmark dependency.

!!! important "Bundle Kernel Architecture"
    `spector-memory` is backed by the off-heap **Bundle Kernel Architecture** (`PartitionBundle`, `RuntimeBundle`, `CognitiveRecordLayout`). In-memory vector indexes are managed directly by `spector-index`, and SIMD/GPU operations are accelerated by `spector-cpu` and `spector-gpu`.

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

    runtime --> sh["SearchHandler<br/><i>cognitive recall & query</i>"]
    runtime --> ih["IngestionHandler<br/><i>delegates to IngestionPipeline</i>"]

    sh --> memory["SpectorMemory (under memory/)"]
    ih --> pipeline["IngestionPipeline<br/><i>chunk → embed → cognitive store</i>"]
    pipeline --> memTarget["CognitiveIngestionTarget<br/><i>MEMORY mode</i>"]
```

**SpectorRuntime** is a thin composition root — it creates and wires subsystems but contains no business logic. Each handler owns its domain:

| Handler | Responsibility | Routes to |
|---------|---------------|-----------|
| `SearchHandler` | Mode-aware cognitive recall & retrieval | `SpectorMemory` |
| `IngestionHandler` | Delegates to unified `IngestionPipeline` | Pipeline → `CognitiveIngestionTarget` |

---

## Module Overview

### Foundation & Acceleration Layer (`/nucleus`)

| Module | Description |
|:---|:---|
| [spector-bom](spector-bom.md) | Bill of Materials POM managing dependency versions across all 27 modules |
| [spector-commons](spector-commons.md) | Shared utilities, concurrent queues, base exceptions, `ErrorCode` registry |
| [spector-core](spector-core.md) | Core compute SPIs (Similarity, HNSW, SVASQ, MaxSim) and quantization algorithms |
| [spector-cpu](spector-cpu.md) | Java 25 Panama Vector SIMD acceleration kernel implementations |
| [spector-gpu](spector-gpu.md) | Java 25 Panama FFM + CUDA GPU hardware acceleration kernels |
| [spector-hdc](spector-hdc.md) | Hyperdimensional computing vector algebra and operations |
| [spector-index](spector-index.md) | In-memory vector indexes (HNSW, SpectorIndex) and keyword indexes (BM25, Splade) |
| [spector-config](spector-config.md) | Configuration — `SpectorProperties`, `SpectorConfigFactory`, YAML parsing |
| [spector-events](spector-events.md) | Decoupled telemetry event bus (`TelemetryBus`, `TelemetryScope`) |
| [spector-test-support](spector-test-support.md) | Common test harnesses, assertions, and mock providers |

### Cognitive Memory Layer (`/memory`)

| Module | Description |
|:---|:---|
| [spector-memory](spector-memory.md) | Flagship cognitive memory engine — 4-tier memory, bundle kernel, recall pipeline, consolidation daemons |
| [spector-provider-api](spector-provider-api.md) | Model-agnostic LLM and embedding provider SPI |
| [spector-providers](spector-providers.md) | Out-of-the-box LLM/embedding providers (Ollama, OpenAI, Google, Anthropic, ONNX) |
| [spector-ingestion](spector-ingestion.md) | Unified ingestion pipeline — chunking, sensory extractors (PDF, audio, images), metadata extraction |
| [spector-inspect](spector-inspect.md) | Binary inspection tool for partition bundles and runtime bundles |
| [spector-metrics](spector-metrics.md) | Micrometer + Prometheus telemetry and distributed tracing instrumentation |

### Nervous System & Gateways (`/synapse`)

| Module | Description |
|:---|:---|
| [spector-runtime](spector-runtime.md) | Composition root — wires cognitive memory and ingestion into unified handlers |
| [spector-synapse](spector-synapse.md) | API gateway and central nervous system — Armeria REST/gRPC/SSE server and agentic chat graph |
| [spector-connector](spector-connector.md) | Enterprise data connector subsystem powered by Apache Camel |
| [spector-mcp](spector-mcp.md) | Model Context Protocol server exposing Spector memory via stdio and SSE |
| [spector-cli](spector-cli.md) | `spectorctl` CLI with remote HTTP and local runtime modes |
| [spector-client](spector-client.md) | Java client SDK for programmatic REST/SSE API access |
| [spector-spring](spector-spring.md) | Spring AI VectorStore integration and auto-configuration |
| [spector-batch](spector-batch.md) | Batch migration and re-indexing engine |
| [spector-dist](spector-dist.md) | Distribution packaging for standalone single-jar deployments |

### Benchmarks (`/bench`)

| Module | Description |
|:---|:---|
| [spector-bench](spector-bench.md) | JMH micro-benchmarks and end-to-end cognitive memory evaluation harness |

