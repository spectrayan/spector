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
| Foundation | `nucleus/` | bom, commons, core, config, storage, events, metrics, test-support | Each other only |
| Cognitive Engine | `memory/` | provider-api, providers, index, query, gpu, ingestion, memory | Foundation |
| Runtime/API | `synapse/` | runtime, mcp, cli, client, spring, synapse, dist | Foundation + Memory |
| UI | `cortex/` | spector-cortex (Angular) | Synapse REST APIs |
| Infrastructure | `bench/`, `deploy/` | spector-bench, Docker | Any |

**Key rule:** `spector-memory` and `spector-engine` are independent peers — they never depend on each other. They are wired only at `SpectorRuntime`.

## Module Breakdown

### nucleus/ (Foundation)

| Module | Purpose |
|--------|---------|
| `spector-bom` | Bill of Materials — version alignment |
| `spector-commons` | Shared utilities, exception framework, error codes |
| `spector-core` | SIMD kernels, distance functions, data structures |
| `spector-config` | Configuration loading (YAML, env, system props) |
| `spector-storage` | Off-heap storage, Panama FFM arenas, WAL |
| `spector-events` | Internal event system |
| `spector-metrics` | Micrometer observability integration |
| `spector-test-support` | Test utilities and fixtures |

### memory/ (Cognitive Engine)

| Module | Purpose |
|--------|---------|
| `spector-provider-api` | Embedding provider SPI |
| `spector-providers` | Provider implementations (Ollama, etc.) |
| `spector-index` | HNSW index, quantization (SVASQ-8/4, IVF-PQ) |
| `spector-query` | Hybrid retrieval, RRF fusion, scoring pipeline |
| `spector-gpu` | Optional CUDA acceleration via Panama FFM |
| `spector-ingestion` | Document ingestion pipeline |
| `spector-memory` | Cognitive memory (4-tier cortex, Hebbian graphs, consolidation) |

### synapse/ (Runtime & APIs)

| Module | Purpose |
|--------|---------|
| `spector-runtime` | Runtime orchestration, component assembly |
| `spector-mcp` | MCP server (stdio + Streamable HTTP, 16 tools) |
| `spector-cli` | Command-line interface |
| `spector-client` | Java client SDK |
| `spector-spring` | Spring Boot / Spring AI integration |
| `spector-synapse` | Armeria-based REST/gRPC gateway (built with `-Psynapse` profile) |
| `spector-dist` | Distribution fat JAR |

### cortex/ (UI — Angular 22, separate from Maven)

| Module | Purpose |
|--------|---------|
| `spector-cortex` | Three.js neural dashboard, real-time memory visualization |

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
