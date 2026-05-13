# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **spector-core:** SIMD-accelerated kernels for DotProduct, CosineSimilarity, and EuclideanDistance using Java Vector API
- **spector-core:** `VectorOps` utility (magnitude, normalize, scale, add, subtract) — all SIMD-accelerated
- **spector-core:** `SimilarityFunction` enum with pluggable strategy dispatch
- **spector-core:** `SimdCapability` runtime ISA detection and reporting
- **spector-storage:** Off-heap `InMemoryVectorStore` backed by Panama `MemorySegment` + `Arena`
- **spector-storage:** File-backed `MappedVectorStore` via memory-mapped I/O
- **spector-storage:** `VectorStoreLayout` for contiguous vector memory arithmetic
- **spector-storage:** `DocumentStore` for metadata (title, content, tags)
- **spector-index:** HNSW approximate nearest-neighbor index with multi-layer graph
- **spector-index:** `NeighborQueue` bounded binary heap for candidate tracking
- **spector-index:** BM25 inverted index with Okapi BM25 scoring (k1=1.2, b=0.75)
- **spector-index:** `StandardAnalyzer` text pipeline (tokenize → lowercase → stop words)
- **spector-query:** `ReciprocalRankFusion` for zero-config score merging
- **spector-query:** `HybridSearchOrchestrator` with virtual-thread parallel fan-out
- **spector-engine:** `SpectorEngine` unified facade with lifecycle management
- **spector-engine:** `SpectorConfig` immutable configuration with builder-style API
- **spector-server:** Javalin REST API with virtual threads (`/health`, `/api/v1/status`, `/api/v1/ingest`, `/api/v1/search`)
- 212 tests across all modules, all passing

### Technical Decisions
- Java 25 with `jdk.incubator.vector` for SIMD
- `FloatVector.SPECIES_PREFERRED` for ISA-agnostic code
- `ReentrantLock` everywhere (no `synchronized`) to avoid virtual thread pinning
- Panama `MemorySegment` for zero-GC vector storage
- `Executors.newVirtualThreadPerTaskExecutor()` for hybrid search fan-out
