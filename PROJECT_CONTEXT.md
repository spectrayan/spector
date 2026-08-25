# Spector Project Context

Welcome to the **Spector** repository context guide. This document serves as the high-level onboarding and architectural blueprint for Spector, a state-of-the-art cognitive memory backbone combining dense vector similarity, SIMD-accelerated BM25 text matching, SPLADE sparse retrieval, Hebbian graph structures, and biologically-inspired cognitive memory tiers. It acts as the "bridge" linking the actual source code with our agent rules, skills, and workflows.

---

## 1. Vision & Core Value Proposition

Traditional vector databases are simple document-matching engines. They perform static similarity searches on static embeddings with high latency, large GC overhead, and zero contextual awareness.

Spector reimagines search by mimicking biological cognitive structures:
*   **Volatile & Permanent Tiers**: Working Memory (Prefrontal Cortex) acts as a volatile circular buffer, while Episodic/Semantic layers represent permanent memory storage.
*   **Fused Scoring**: Instead of plain similarity, Spector evaluates `Similarity × Importance × Temporal Decay` in a single pass.
*   **Synaptic Gating**: Uses a 64-bit inline Bloom filter (Synaptic Tags) to eliminate 99% of candidate records before doing vector computations.
*   **Zero-GC Performance**: Built on off-heap Panama FFM and SIMD Vector APIs, processing 1M memories in under **0.13ms**.

---

## 2. Technology Stack & JVM Configuration

The Spector codebase relies on a bleeding-edge Java tech stack, taking full advantage of modern JVM capabilities:

| Technology Domain | Technology / Library | Version / Specs | Purpose |
|---|---|---|---|
| **Core Runtime** | OpenJDK 25 | JDK 25 (with Preview & Incubator) | Panama FFM, SIMD Vector API, Virtual Threads |
| **Build System** | Apache Maven | 22-module Maven Reactor | Modular builds, reproducible JAR outputs |
| **API Gateways** | Armeria / Javalin | Armeria 1.39.1 / Javalin 6.6.0 | Unified gRPC & HTTP on a single port (Netty-backed) |
| **JSON Parser** | Jackson | Jackson 3.x (BOM Jackson 2.x) | Fast off-heap compatible serialization/deserialization |
| **Observability** | Micrometer | 1.14.5 (Core & Prometheus) | Sub-microsecond metrics tracking |
| **Model Context** | Anthropic MCP Java SDK | 2.0.0-M3 (Official) | Native integration with Model Context Protocol |
| **Testing** | JUnit 5 / AssertJ / Mockito | JUnit 5.11.4 / AssertJ 3.27.3 | Fluent assertions and concurrent testing frameworks |
| **Micro-benchmarking** | OpenJDK JMH | 1.37 | Microsecond-accurate performance testing for hot paths |

### JVM Execution Flags
Because of the heavy dependency on Panama FFM and SIMD Vector APIs, compiler and surefire execution requires these exact JVM arguments:
```bash
--add-modules jdk.incubator.vector \
--enable-preview \
--enable-native-access=ALL-UNNAMED \
--add-opens java.base/java.lang=ALL-UNNAMED \
--add-opens java.base/java.lang.reflect=ALL-UNNAMED
```

---

## 3. Module & Layer Architecture

Spector is organized as a strict layered architecture to prevent circular dependencies and maintain clean boundaries:

```mermaid
graph TD
    subgraph infrastructure ["Validation & Distribution"]
        metrics[spector-metrics]
        bench[spector-bench]
        dist[spector-dist]
        spring[spector-spring]
        batch[spector-batch]
    end

    subgraph run_layer ["Nervous System & Gateways (synapse/)"]
        runtime[spector-runtime]
        synapse[spector-synapse]
        connector[spector-connector]
        mcp[spector-mcp]
        cli[spector-cli]
        client[spector-client]
    end

    subgraph intelligence ["Cognitive Memory Layer (memory/)"]
        memory[spector-memory]
        ingestion[spector-ingestion]
        provider_api[spector-provider-api]
        providers[spector-providers]
        inspect[spector-inspect]
    end

    subgraph foundation ["Foundation & Acceleration (nucleus/)"]
        bom[spector-bom]
        core[spector-core]
        cpu[spector-cpu]
        gpu[spector-gpu]
        hdc[spector-hdc]
        index[spector-index]
        commons[spector-commons]
        config[spector-config]
        events[spector-events]
        testsupport[spector-test-support]
    end

    %% Dependency mappings
    run_layer --> intelligence
    run_layer --> foundation
    intelligence --> foundation
    infrastructure --> intelligence
    infrastructure --> run_layer
    infrastructure --> foundation

    %% Boundary rules
    style memory fill:#33a02c,stroke:#111,stroke-width:2px,color:#fff
    linkStyle 0,1,2,3,4,5 stroke:#999,stroke-width:1px;
```

### Module Responsibilities

1.  **Foundation & Acceleration Layer (`nucleus/`)**
    *   `spector-bom`: Central bill of materials POM defining module dependency constraints.
    *   `spector-commons`: Central utilities, concurrent queues, standard exceptions, and `ErrorCode` enum registry.
    *   `spector-core`: Compute SPIs (Similarity, HNSW, SVASQ, MaxSim) and vector quantization algorithms.
    *   `spector-cpu`: Java 25 Panama Vector SIMD hardware-accelerated kernels.
    *   `spector-gpu`: Java 25 Panama FFM + CUDA GPU hardware-accelerated kernels.
    *   `spector-hdc`: Hyperdimensional computing vector algebra.
    *   `spector-index`: Distance indexes (in-memory HNSW, SpectorIndex, BM25, Splade).
    *   `spector-config`: Central configuration manager (`SpectorConfigFactory.java`, `SpectorProperties.java`).
    *   `spector-events`: Decoupled telemetry event bus (`TelemetryBus`, `TelemetryScope`).
    *   `spector-test-support`: Test fixtures, mocks, and integration test base classes.
2.  **Cognitive Memory Layer (`memory/`)**
    *   `spector-memory`: Off-heap biologically-inspired 4-tier cognitive memory (Working, Episodic, Semantic, Procedural), Bundle Kernel (`PartitionBundle`, `RuntimeBundle`), Hebbian co-activation graph, and multi-stage recall pipeline.
    *   `spector-provider-api`: Model-agnostic LLM and text-to-vector embedding SPI.
    *   `spector-providers`: Concrete implementations connecting to Ollama, OpenAI, Google, Anthropic, and ONNX.
    *   `spector-ingestion`: Document chunking, multi-modal sensory extractors, and ingestion routing.
    *   `spector-inspect`: Binary inspection utility for partition and runtime bundles.
    *   `spector-metrics`: Micrometer and Prometheus observability instrumentation.
3.  **Nervous System & Gateways (`synapse/`)**
    *   `spector-runtime`: Core integration runtime and composition root.
    *   `spector-synapse`: Unified API gateway, Armeria REST/gRPC/SSE server, and agentic chat graph.
    *   `spector-connector`: Enterprise data connectors powered by Apache Camel.
    *   `spector-mcp`: Model Context Protocol server exposing Spector memory via stdio/SSE.
    *   `spector-cli` / `spector-client`: `spectorctl` CLI and Java client SDK.
    *   `spector-spring`: Spring AI VectorStore integration starter.
    *   `spector-batch`: Batch migration engine.
    *   `spector-dist`: Distribution packaging for standalone single-jar deployments.
4.  **Performance & Benchmarks (`bench/`)**
    *   `spector-bench`: JMH micro-benchmarks and end-to-end cognitive memory evaluation harness.

---

## 4. Agent Tooling Alignment (Rules & Workflows)

All agents working on this codebase must understand how our `.agents/` tooling maps to this layout:

### File System Rules (`.agents/rules/rules.md`)
*   **Virtual Threads Safe Concurrency**: Since Spector is built on virtual threads, agents are forbidden from using the `synchronized` keyword. You must use `ReentrantLock` or other non-pinning concurrency utilities.
*   **Platform-agnostic SIMD**: Lane widths cannot be hardcoded (e.g. AVX-512 vs AVX2); agents must use `FloatVector.SPECIES_PREFERRED` inside `spector-core`, `spector-cpu`, `spector-index`, or `spector-memory`.
*   **Bundle Kernel Architecture**: Storage and persistence are fully encapsulated within `spector-memory` using zero-copy Panama FFM memory layouts (`PartitionBundle`, `RuntimeBundle`, `CognitiveRecordLayout`). Vector indexes are managed directly in-memory by `spector-index`.

### Automated Agent Workflows (`.agents/workflows/`)
Each workflow matches a specific slash command or task trigger. Use them sequentially as listed:

1.  **/feature-development** (`feature-development.md`): End-to-end framework for feature implementation. Guarantees that changes in foundation layers occur prior to search/intelligence modifications, and wraps everything in rigorous testing.
2.  **/exception-hardening** (`exception-hardening.md`): Audits code safety. Ensures all throw and catch sites inside a module throw domain-specific exceptions (e.g. `SpectorHebbianException` or `SpectorGraphPersistenceException`) registered in `ErrorCode.java`.
3.  **/dataset-generation** (`dataset-generation.md`): Dedicated workflow for the Cognitive Benchmark dataset. Calibrates emotional valence and importance tags under `datasets/cognitive-benchmark/`.
4.  **/module-lifecycle** (`module-lifecycle.md`): Guidelines on how to add, remove, or rename any modules inside the 27-module Maven reactor without breaking the reactor.
5.  **/pr-review** (`pr-review.md`): Quality gate. Automates compile checks, JaCoCo thresholds, and JMH benchmark runs before making a pull request.
6.  **/release-prep** (`release-prep.md`): Guides versions bumping, changelog additions, and GPG release signing profiles.

---

## 5. Quick Directory Map

*   **Runtime Config**: `spector-local.yml` (overrides default options).
*   **On-Disk Storage**: `.spector/` (ignored via `.gitignore` - do not delete or commit).
*   **Biologically-Inspired Design**: `spector-memory/RnD/` holds raw design math for cognitive memory mechanisms.
*   **Documentation Site**: `docs/` (built via MkDocs Material: `python -m mkdocs build --clean`).

---

## 6. Spector Enterprise

The full-stack enterprise product features have been extracted to a separate repository: [spector-enterprise](https://github.com/spectrayan/spector-enterprise).

**This repository (spector)** is the **core engine** — which includes the **Cortex dashboard** (Angular 22 neural visualization and chat UI) located in `cortex/spector-cortex/` as a core OSS module.

**spector-enterprise** adds:
*   Enterprise data connectors (Apache Camel) — template-driven ingestion from Kafka, S3, Salesforce, Confluence, etc.
*   Multi-tenant namespace manager & access control
*   Corporate management APIs for clustering, monitoring, and scaling

Enterprise **depends on** `spector-synapse` and always starts the core engine.
