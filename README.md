# Spector-Search ⚡

> Ultra-fast, SIMD-accelerated semantic search engine built on Java Vector API + modern JVM technologies.

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)
[![Build](https://img.shields.io/github/actions/workflow/status/spectrayan/spector-search/ci.yml?branch=main)](https://github.com/spectrayan/spector-search/actions)

## ✨ Features

- **🔥 SIMD-Accelerated** — Hardware-accelerated vector math via Java Vector API (AVX2/AVX-512/NEON)
- **🧠 Hybrid Search** — Combines semantic vector search (HNSW) with keyword search (BM25) via Reciprocal Rank Fusion
- **💾 Zero-Copy Storage** — Off-heap vector storage using Panama Foreign Function & Memory API
- **🧵 Virtual Thread Native** — Designed for Project Loom's virtual threads, no `synchronized` blocks
- **🎯 High Recall** — HNSW approximate nearest-neighbor search with configurable recall@K ≥ 80%
- **⚡ Sub-Millisecond Queries** — Branchless SIMD kernels with masked tail handling

## 🏗 Architecture

```
spector-search/
├── spector-core/      # SIMD kernels (DotProduct, Cosine, Euclidean, VectorOps)
├── spector-storage/   # Panama MemorySegment stores (InMemory + Mmap)
├── spector-index/     # HNSW vector index + BM25 keyword index
├── spector-query/     # Hybrid orchestrator + RRF fusion
├── spector-engine/    # Unified engine facade + lifecycle
├── spector-server/    # REST API (Javalin + virtual threads)
└── spector-bench/     # JMH benchmarks
```

### Module Dependency Graph

```
server → engine → query → index → core
                        → index → storage → core
```

## 🚀 Quick Start

### Prerequisites

- **JDK 25+** (OpenJDK with Vector API incubator)
- **Maven 3.9+**

### Build & Test

```bash
# Clone the repository
git clone https://github.com/spectrayan/spector-search.git
cd spector-search

# Build and run all tests (212 tests)
mvn clean test

# Start the REST server
mvn exec:java -pl spector-server \
  -Dexec.mainClass="com.spectrayan.spector.server.SpectorServer"
```

### REST API

```bash
# Health check
curl http://localhost:7070/health

# Engine status (includes SIMD capability)
curl http://localhost:7070/api/v1/status

# Ingest a document
curl -X POST http://localhost:7070/api/v1/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "id": "doc-1",
    "title": "Java Vector API",
    "content": "SIMD-accelerated search engine on modern JVM",
    "vector": [0.1, 0.2, 0.3, ...]
  }'

# Search (auto-detects mode: keyword/vector/hybrid)
curl -X POST http://localhost:7070/api/v1/search \
  -H "Content-Type: application/json" \
  -d '{
    "text": "vector search engine",
    "vector": [0.1, 0.2, 0.3, ...],
    "topK": 10
  }'
```

## 🧩 Programmatic API

```java
var config = SpectorConfig.DEFAULT
    .withDimensions(384)
    .withCapacity(100_000);

try (var engine = new SpectorEngine(config)) {
    // Ingest
    engine.ingest("doc-1", "Hello world", embedding);

    // Search
    SearchResponse response = engine.hybridSearch("hello", queryVector, 10);

    for (ScoredResult result : response.results()) {
        System.out.printf("%s → %.4f%n", result.id(), result.score());
    }
}
```

## ⚙️ Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| `dimensions` | 384 | Vector dimensionality |
| `capacity` | 100,000 | Max documents |
| `similarityFunction` | COSINE | COSINE, DOT_PRODUCT, or EUCLIDEAN |
| `M` | 16 | HNSW max connections per node |
| `efConstruction` | 200 | HNSW construction beam width |
| `efSearch` | 50 | HNSW search beam width |
| `k1` | 1.2 | BM25 term frequency saturation |
| `b` | 0.75 | BM25 document length normalization |
| `RRF k` | 60 | Reciprocal Rank Fusion constant |

## 🏎 Performance

SIMD auto-detection adapts to your hardware:

| ISA | Width | Lanes (float) | Platform |
|-----|-------|---------------|----------|
| AVX2 | 256-bit | 8 | Most modern x86 |
| AVX-512 | 512-bit | 16 | Intel Xeon, recent AMD |
| NEON | 128-bit | 4 | Apple Silicon, ARM |

## 📊 Test Suite

| Module | Tests | Coverage |
|--------|-------|----------|
| spector-core | 117 | SIMD kernels, similarity functions |
| spector-storage | 38 | Off-heap stores, mmap persistence |
| spector-index | 36 | HNSW recall, BM25 scoring, analyzer |
| spector-query | 13 | RRF fusion, hybrid orchestration |
| spector-engine | 8 | End-to-end ingestion + search |
| **Total** | **212** | **All passing ✅** |

## 🤝 Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## 📄 License

This project is licensed under the Apache License 2.0 — see [LICENSE](LICENSE) for details.

## 🔒 Security

Please see [SECURITY.md](SECURITY.md) for our security policy and how to report vulnerabilities.

---

**Built with ⚡ by [Spectrayan](https://www.spectrayan.com/)**
