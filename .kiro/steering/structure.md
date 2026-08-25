# Project Structure

## Top-Level Layout

```
spector/
├── nucleus/        — Foundation layer (core primitives, config, storage)
├── memory/         — Cognitive engine (flagship hybrid retrieval + memory)
├── synapse/        — API gateway, agents, runtime wiring
├── cortex/         — Angular 22 UI (separate build, not in Maven reactor)
├── bench/          — JMH benchmarks
├── deploy/         — Docker deployment scripts
├── docs/           — MkDocs Material documentation site
└── .agents/        — AI agent rules, skills, and workflow definitions
```

## Architecture Layers (dependency flows top-down)

| Layer | Directory | Modules | Depends On |
|-------|-----------|---------|------------|
| Foundation & Acceleration | `nucleus/` | bom, commons, core, cpu, gpu, hdc, index, config, events, test-support | Foundation only |
| Cognitive Memory | `memory/` | provider-api, providers, ingestion, inspect, metrics, memory | Foundation |
| Runtime & Gateways | `synapse/` | runtime, synapse, connector, mcp, cli, client, spring, batch, dist | Foundation + Memory |
| Infrastructure & Benchmarks | `bench/`, `deploy/` | spector-bench, Docker | Foundation + Memory + Synapse |

## Module Breakdown

### nucleus/ (Foundation & Acceleration)

| Module | Purpose |
|--------|---------|
| `spector-bom` | Bill of Materials — version alignment |
| `spector-commons` | Shared utilities, exception framework, error codes |
| `spector-core` | Compute SPIs (Similarity, HNSW, SVASQ, MaxSim) and quantization |
| `spector-cpu` | Java 25 Panama Vector SIMD acceleration kernels |
| `spector-gpu` | Java 25 Panama FFM + CUDA GPU hardware acceleration kernels |
| `spector-hdc` | Hyperdimensional computing vector algebra |
| `spector-index` | In-memory vector indexes (HNSW, SpectorIndex) and keyword indexes (BM25, Splade) |
| `spector-config` | Configuration loading (YAML, env, system props) |
| `spector-events` | Internal event bus and telemetry scope |
| `spector-test-support` | Test utilities and fixtures |

### memory/ (Cognitive Engine)

| Module | Purpose |
|--------|---------|
| `spector-provider-api` | Model-agnostic LLM/embedding provider SPI |
| `spector-providers` | Concrete AI providers (Ollama, OpenAI, Google, Anthropic, ONNX) |
| `spector-ingestion` | Document ingestion pipeline and multi-modal sensory extractors |
| `spector-inspect` | Binary bundle inspection CLI |
| `spector-metrics` | Micrometer and Prometheus observability metrics |
| `spector-memory` | Cognitive memory engine (4-tier cortex, Bundle Kernel, Hebbian graphs, consolidation) |

### synapse/ (Runtime & Gateways)

| Module | Purpose |
|--------|---------|
| `spector-synapse` | Spring Boot 4 REST/SSE gateway and agentic chat graph |
| `spector-connector` | Enterprise data connectors powered by Apache Camel |
| `spector-mcp` | MCP server (stdio + SSE, 20 tools) |
| `spector-cli` | Multi-function `spectorctl` CLI and standalone `spector.jar` runner |
| `spector-spring` | Spring Boot / Spring AI VectorStore integration |
| `spector-batch` | Batch migration and re-indexing engine |

## Key Paths

- Engine data: `.spector/index/`
- Memory data: `.spector/memory/`
- WAL: `.spector/memory/wal/`
- Config source of truth: `SpectorConfigFactory.java`
- Memory R&D designs: `memory/spector-memory/RnD/`
- Docs config parameters: `docs/docs/configuration/parameters.md`

## Design Patterns

- **Records** for immutable value objects (`SearchResult`, `NodeInfo`, `PersistenceFiles`)
- **Builder pattern** for configs (`SpectorConfig.builder()`, `SpectorEngine.builder()`)
- **Abstract Factory** for component assembly (`EngineComponentFactory`)
- **Interface-first** design for pluggability (`IngestionTarget`, `EmbeddingProvider`)
- **AutoCloseable** for any class holding native resources or arenas
