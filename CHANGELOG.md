# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0-alpha.2] - 2026-09-11

### Added & Enhanced — Multi-Provider Docker & Production Infrastructure (#812)
- **deploy/docker:** Replaced hardcoded configuration in `spector-docker.yml` with provider-agnostic environment variable bindings (`SPECTOR_EMBEDDING_*`, `SPECTOR_GENERATION_*`).
- **deploy/docker:** Added native Docker secrets loader in `entrypoint.sh` for mounting API keys at `/run/secrets/` and alias mapper (`alias_env`) bridging user-facing short names to canonical config paths.
- **docker-compose:** Added complete provider environment declarations with sensible defaults and created `.env.example` with reference configurations for Ollama, Google Gemini, OpenAI, Anthropic, Mistral, Azure, Bedrock, and ONNX.
- **deploy/helm:** Added Linux kernel tuning for Project Panama FFM off-heap `mmap` (`vm.max_map_count=262144`, `fs.file-max=1048576`) via privileged `init-sysctl` container and pod-level `sysctls`.
- **deploy/helm:** Added configurable high-performance `StorageClass` template (`templates/storageclass.yaml`) with presets for AWS (`gp3`, `io2`), GCP (`hyperdisk-balanced`, `pd-ssd`), Azure (`managed-csi-premium`), and Local NVMe (`spector-nvme-local`).
- **deploy/terraform:** Added provider variables and sensitive credential masking across AWS ECS, Azure Container Apps, and GCP Cloud Run modules. Configured `nofile` ulimits (65536) in AWS ECS.
- **spector-synapse:** Implemented nested backward-compatibility fallback chains in `application.yml` (`${SPECTOR_EMBEDDING_*:${SPECTOR_OLLAMA_*:default}}`).
- **docker-build:** Pinned Node.js build stage to `22.22.3-alpine` for Angular CLI 22 compatibility and added BuildKit cache mount for Maven repository persistence.

### Added — Project Panama Pure Math Kernels (ADR-0033 Phase 0 & 1) (#809, #810)
- **spector-core:** Extracted pure SIMD math kernels into stateless, thread-safe components using Project Panama Vector API (`jdk.incubator.vector`).
- **spector-core & spector-index:** Deduplicated dot-product, cosine similarity, Euclidean distance, and L2 normalization call sites across core and indexing subsystems.
- **spector-core:** Added comprehensive contract tests and safety scaffolding for Project Panama off-heap boundary verification.

### Added — Sealed Kernel Module (spector-kernel) (#793, #796)
- **spector-kernel:** Carved dedicated sealed kernel module enforcing native Panama memory segment isolation and off-heap partition structures.
- **spector-memory:** Decoupled high-level memory cognitive pathways from low-level native arena memory layout and lifecycle management.

### Enhanced — Dual-Plane Concurrency & Concurrency Safety (#783, #784, #786, #787, #788)
- **spector-commons & spector-synapse:** Implemented Dual-Plane Concurrency with virtual thread classification, executor injection (`SpringExecutorProvider`), and in-memory multi-tenant Quartz scheduler routing via virtual thread SPI.
- **spector-commons & spector-memory:** Resolved 253 Java inspection warnings and eliminated deprecated twin classes.

### Fixed — Memory Resilience & Temporal Graph Expansion (#781, #782)
- **spector-memory:** Hardened partition bundle recovery against partial writes, off-heap corruption, and unexpected shutdowns.
- **spector-memory:** Enhanced Temporal Knowledge Graph multi-hop neighborhood expansion and episodic timestamp decoding.

### Added — Persona Enactment Engine (ADR-0032) (#764)
- **spector-memory:** Implemented Dual-Process Cognitive Enactment Engine under `com.spectrayan.spector.memory.aisme.enactment`:
  - `CognitiveAppraisal` grounding in Lazarus & Scherer Cognitive Appraisal Theory with VAD dynamics and agency attribution
  - `PersonaRecall` multi-tier 4-cue retrieval (Constitution, Scars, Habits, Working state) with `GlobalWorkspace` conscious access bottleneck
  - `StanceResolver` continuous Hopfield attractor relaxation and Expected Free Energy (EFE) policy inference
  - `EnactmentEngine` System 1/System 2 bounded deliberation, epistemic tense gating (ADR-0031), and ancestral guardrail vetoes
  - Domain records: `Enactment`, `CognitiveAppraisal`, `PersonaDeliberation`, `TradeOffSelection`, `EngramCitation`, `SituationFrame`, `EnactMode`, `ConfidenceLevel`, `AgencyAttribution`
- **spector-synapse:** Wired `EnactmentService` and added `NodeType.ENACT` to `DynamicGraphBuilder` and `CognitiveState` for LangGraph4j state graphs
- **spector-mcp:** Added declarative tool `persona_enact` (`src/main/resources/mcp/tools/persona_enact.json`, `PersonaEnactTool.java`), expanding tool suite to 23 tools with full JSON schema validation

### Added & Fixed — Cognitive Memory Review Remediation (MR-01 — MR-09) (#661)
- **spector-memory (MR-01):** Implemented simulated-memory binary provenance in `HeaderLayout64` using `FLAG_SIMULATED` (0x20 in consolidation flags, byte 34) and `soulVersion` (bytes 46-47); fixed roundtrip persistence durability and `EncodingHeader.createSynthetic`
- **spector-memory (MR-02):** Resolved fused score formula truth with `ScoreFusionMode` (`MULTIPLICATIVE` vs `ADDITIVE`); validated $\alpha \in [0.0, 1.0]$ in `RecallOptions` and `ScoringOptions`; added `FusedScoreFormulaPropertyTest`
- **spector-memory (MR-03):** Implemented dynamic soul-conditioned scoring regime (FERS: $\alpha \cdot \text{Sim} + \beta \cdot \sigma(\Delta F) + \gamma \cdot \text{Resonance}$) in `FreeEnergyGuidedRelay` and `CognitiveScorer`; added `SoulConditionedWeightProvider` with EMA hysteresis damping and slew rate limits; wired `usePathwayEngine` reachability
- **spector-memory (MR-04):** Implemented dentate-gyrus lateral inhibition & recall interference resolution (`LateralInhibitionRelay`) with single-linkage clustering ($\theta \ge 0.88$), soft rank-ordered redundancy attenuation, multi-factor confidence arbitration, and hard contradiction penalties
- **spector-memory (MR-06):** Implemented early $O(1)$ graph associative prior ($A_g$) in Phase 6 fusion via `CoActivationAssociativePriorProvider` with log1p hub dampening; enforced anti-truncation guarantee (novel memories are never gated out in Phases 1-4)
- **spector-memory (MR-07):** Deepened constructive simulation with multi-candidate sampling, weighted vector recombination, and PCMN prediction error validation; attached `alignSim` metadata and updated `ConstructiveMemoryPersistenceRelay` to threshold on real alignment; reordered `SoulDriftRefusionRelay` prior adaptation before re-fusion loop and preserved header-derived hints and true `MemoryType`
- **spector-memory (MR-08):** Implemented graph compaction telemetry and `GraphStructureHealthSnapshot` on `EntityDirectory` and `HebbianGraphMemory`; added adaptive compaction configuration in `SpectorPropertyConstants`
- **docs (MR-09):** Added `scoring-regimes.md`, `aisme.md`, `constructive-memory.md`; corrected test framework test counts and scoring pipeline formula documentation

### Removed & Refactored — Storage & Query Module Decommissioning (#650)
- **spector-storage & spector-query:** Fully decommissioned and deleted both legacy modules (~4,500 lines of dead code eliminated); reduced reactor from 29 to 27 modules
- **spector-index:** Relocated `SpectorSegmentClosedException` to `com.spectrayan.spector.index.error`; removed dead disk HNSW and sharded index classes (`DiskHnswIndex`, `DiskHnswWriter`, `ShardedDiskHnswIndex`, `ShardedDiskHnswWriter`) and dead `save()`/`load()` methods in `SpectorIndex` and `SpectorShard`
- **spector-memory:** Purged dead legacy constructor and unused `legacyStore` field in `SemanticRecallStrategy.java`
- **spector-gpu:** Updated exception imports to `com.spectrayan.spector.index.error.SpectorSegmentClosedException` and migrated dependency from `spector-storage` to `spector-index`

### Refactored — RecallPipeline SRP Decomposition & Builder Unification (#487)
- **spector-memory:** Decomposed `RecallPipeline` into 4 phase components (`RecallCandidateGatherer`, `CognitiveReranker`, `GraphExpander`, `SalienceAndHabituationScorer`) and extracted 9 segment scanning inner types into `com.spectrayan.spector.memory.pipeline.scan`
- **spector-memory:** Unified builder architecture on `RecallPipelineBuilder` (`RecallPipeline.builder()`) and consolidated telescopic constructors
- **spector-gpu & spector-index:** Standardized SLF4J log parameterization and expanded wildcard exception imports

### Refactored — Virtual Thread Locking & Exception Governance (#485)
- **spector-memory:** Refactored `IndexRecordMemory` and `AbstractRegistryMemory` from `synchronized` monitor blocks to `ReentrantLock` (enforcing ADR-005 virtual thread concurrency safety)
- **spector-gpu:** Refactored `GpuCapability` detection lock from `synchronized (GpuCapability.class)` to `ReentrantLock`
- **spector-synapse:** Refactored `SqlQueryTool` schema caching from `synchronized (this)` to `ReentrantLock`
- **spector-mcp:** Hardened `MemoryRememberTool` with diagnostic warning logs on metadata parsing / importance estimation fallbacks; wrapped raw `RuntimeException` rethrows in `McpToolHandler` with domain `SpectorInternalException`

### Changed — Enterprise Extraction
- **spector-cortex:** Migrated to [spector-enterprise](https://github.com/spectrayan/spector-enterprise) — the `spector-cortex/` directory has been removed from this repository
- **spector-node:** Core engine remains headless/embeddable — enterprise edition wraps it with management APIs, connectors, LLM providers, and the Cortex UI on a single Armeria port
- **docs:** Cortex documentation page updated with migration notice pointing to spector-enterprise

### Added — spector-events (Telemetry Event Bus)
- **spector-events:** New module — decoupled telemetry event bus for real-time observability
- **spector-events:** `TelemetryBus` — instance-based, thread-safe event router (not static, HA-safe)
- **spector-events:** `TelemetryScope` — per-query scope that accumulates telemetry and flushes on close
- **spector-events:** `TelemetryEvent` — sealed event hierarchy (SIMD, GPU, query trace, graph pulse, memory diagnostic, cluster topology, etc.)
- **spector-events:** 12 telemetry event types mapping 1:1 to Cortex dashboard cards

### Added — spector-cortex (Neural Dashboard)
- **spector-cortex:** SIMD Panel — 16-lane hardware visualization with intensity (speed) and utilization (fill level) color-coded bars
- **spector-cortex:** Cognitive Profile Radar — hexagonal radar chart with animated dot displacement toward dominant profile corner
- **spector-cortex:** Vector Space layer controls — Query dot, k-NN lines, Axes grid, and Labels toggles (matching Neural Graph pattern)
- **spector-cortex:** Vector Space 3D axes grid — RGB-tinted X/Y/Z axis lines with concentric ring markers at r=10/20/30
- **spector-cortex:** Vector Space dimension labels — billboard sprites (`dim₀`/`dim₁`/`dim₂`) that face the camera
- **spector-cortex:** Vector Space tier legend — Working/Episodic/Semantic/Procedural color legend overlay
- **spector-cortex:** `ThemeService.getCanvasColor()` — resolves Angular Material 21 M3 CSS variables (oklch) to canvas-compatible hex via off-screen div probe
- **spector-cortex:** `VectorLayerToggles` and `toggleVectorLayer()` in `CortexStateService`

### Changed — spector-metrics (Unified Decorator)
- **spector-metrics:** `MeteredSpectorEngine` now accepts optional `TelemetryBus` — single decorator for both Micrometer timers and event-bus telemetry
- **spector-metrics:** Eliminated need for separate telemetry decorator layer

### Fixed — spector-cortex (Angular 21 Compatibility)
- **spector-cortex:** Moved all `effect()` calls from `ngAfterViewInit` to constructors — Angular 21 requires injection context (NG0203)
- **spector-cortex:** Added initialization guards to constructor effects to prevent accessing uninitialized THREE.js scenes/canvas contexts
- **spector-cortex:** Replaced `THREE.Clock` (deprecated r183+) with `THREE.Timer` in NeuralGraphComponent and VectorSpaceComponent
- **spector-cortex:** Fixed canvas rendering across all 9 canvas components — oklch() color space silently ignored by Canvas 2D API

### Added
- **spector-core:** SIMD-accelerated kernels for DotProduct, CosineSimilarity, and EuclideanDistance using Java Vector API
- **spector-core:** `VectorOps` utility (magnitude, normalize, scale, add, subtract) — all SIMD-accelerated
- **spector-core:** `SimilarityFunction` enum with pluggable strategy dispatch
- **spector-core:** `SimdCapability` runtime ISA detection and reporting
- **spector-core:** Scalar INT8 quantization (`ScalarQuantizer`, `QuantizedDotProduct`, `QuantizedCosineSimilarity`)
- **spector-commons:** `TextChunker` for character-level overlapping chunk splitting
- **spector-commons:** `TokenChunker` for token-level chunk splitting with precise token limits
- **spector-commons:** `StreamingChunker` for bounded-memory streaming ingestion of large files
- **spector-commons:** `ContentExtractor` for XML/JSON/Java object text extraction
- **spector-commons:** `WordTokenizer` and `TextUtils` text processing utilities
- **spector-storage:** Off-heap `InMemoryVectorStore` backed by Panama `MemorySegment` + `Arena`
- **spector-storage:** File-backed `MappedVectorStore` via memory-mapped I/O
- **spector-storage:** `QuantizedVectorStore` for INT8-quantized vector storage
- **spector-storage:** `VectorStoreLayout` for contiguous vector memory arithmetic
- **spector-storage:** `DocumentStore` for metadata (title, content, tags) with delete support
- **spector-storage:** `IndexFileFormat` for HNSW disk serialization format
- **spector-index:** HNSW approximate nearest-neighbor index with multi-layer graph
- **spector-index:** `QuantizedHnswIndex` — HNSW with scalar INT8 quantization (4× memory reduction)
- **spector-index:** `DiskHnswIndex` — read-only memory-mapped HNSW for datasets larger than RAM
- **spector-index:** `DiskHnswWriter` — serializes in-memory HNSW to disk format
- **spector-index:** `NeighborQueue` bounded binary heap for candidate tracking
- **spector-index:** BM25 inverted index with Okapi BM25 scoring (k1=1.2, b=0.75) and document deletion
- **spector-index:** `StandardAnalyzer` text pipeline (tokenize → lowercase → stop words)
- **spector-index:** `StemmingAnalyzer` with simplified Porter stemmer
- **spector-index:** IVF-PQ vector index (`IvfPqIndex`, `PostingList`) with 32× compression
- **spector-index:** `ProductQuantizer` with K-Means++ initialization and ADC distance
- **spector-index:** `VectorIndex.isReadOnly()` default method for read-only index detection
- **spector-query:** `ReciprocalRankFusion` for zero-config score merging
- **spector-query:** `HybridSearchOrchestrator` with virtual-thread parallel fan-out and optional LLM re-ranking
- **spector-query:** `Reranker` SPI and `LlmReranker` implementation via Ollama
- **spector-query:** `QueryParser` with directive syntax (mode:, k:) and auto-detect
- **spector-embed-api:** `EmbeddingProvider` SPI with `EmbeddingResult`, `EmbeddingConfig`, `EmbeddingException`
- **spector-embed-ollama:** `OllamaEmbeddingProvider` with HTTP client, retry logic, and fallback behavior
- **spector-gpu:** `GpuCapability` — runtime CUDA detection via Panama FFM
- **spector-gpu:** `GpuBatchSimilarity` — SIMD-accelerated batch cosine and dot product computation
- **spector-gpu:** `CudaKernelLauncher` — PTX kernel loader and executor via Panama FFM
- **spector-engine:** `SpectorEngine` unified facade with lifecycle management
- **spector-engine:** `SpectorConfig` immutable configuration with builder-style API
- **spector-engine:** GPU acceleration integration with graceful CPU SIMD fallback
- **spector-engine:** LLM re-ranker integration via config (`withReranker()`)
- **spector-engine:** Document deletion support (`delete()` method)
- **spector-engine:** Auto-embed ingestion, chunked ingestion, and streaming file ingestion
- **spector-engine:** IVF-PQ auto-training with buffered vector accumulation
- **spector-node:** Armeria REST API with virtual threads
- **spector-node:** CORS support via bundled plugin
- **spector-node:** Optional API key authentication (`X-API-Key` header)
- **spector-node:** Auto-embed ingest endpoint (`/api/v1/ingest/auto`)
- **spector-node:** Bulk ingest endpoint (`/api/v1/ingest/bulk`)
- **spector-node:** Document deletion endpoint (`DELETE /api/v1/documents/{id}`)
- **spector-node:** Metrics endpoint (`/api/v1/metrics`)
- **spector-node:** Vector dimension validation on ingest
- **spector-node:** gRPC-based distributed search with coordinator/shard fan-out
- **spector-node:** `ClusterCoordinator` with parallel shard queries and result merging
- **spector-node:** `RemoteShardClient` with TLS support (mutual TLS optional)
- **spector-node:** `ShardNode` gRPC server wrapping a local SpectorEngine
- **spector-node:** `ClusterConfig` with consistent hash and range partitioning
- **spector-bench:** JMH benchmarks for SIMD kernels, HNSW, BM25, ingestion, IVF-PQ, concurrency
- **spector-bench:** `PerformanceTestRunner` for comprehensive latency/throughput reporting
- 316+ tests across all modules, all passing

### Added — spector-mcp (Agent-Native MCP Server)
- **spector-mcp:** Built-in Model Context Protocol (MCP) server for AI agent integration (Claude Desktop, Cursor, autonomous agents)
- **spector-mcp:** 6 MCP tools: `semantic_search`, `hybrid_search`, `rag_query`, `ingest_document`, `delete_document`, `engine_status`
- **spector-mcp:** `McpToolHandler` abstract base class with template method pattern (timing, error handling, arg parsing)
- **spector-mcp:** `ToolSchemaBuilder` — type-safe fluent builder for JSON schemas (replaces error-prone `Map.of()` literals)
- **spector-mcp:** `SpectorToolRegistry` — tool discovery and registration with Open/Closed Principle
- **spector-mcp:** `SpectorResourceProvider` and `SpectorPromptProvider` — MCP resource/prompt definitions
- **spector-mcp:** `ResultFormatter` — shared formatting utilities for search results, RAG context, engine status
- **spector-mcp:** `SpectorMcpMain` CLI entry point with Ollama embedding provider auto-detection
- **spector-mcp:** In-process MCP execution with zero network overhead (50–200µs per tool call)
- **spector-mcp:** 15 unit tests covering tool registry, all tool handlers, schema builder, and argument validation

### Technical Decisions
- Java 25 with `jdk.incubator.vector` for SIMD
- `FloatVector.SPECIES_PREFERRED` for ISA-agnostic code
- `ReentrantLock` everywhere (no `synchronized`) to avoid virtual thread pinning
- Panama `MemorySegment` for zero-GC vector storage
- `Executors.newVirtualThreadPerTaskExecutor()` for hybrid search fan-out
- GPU module as optional dependency — graceful fallback to CPU SIMD
- LLM re-ranker wired through engine config, not global state
