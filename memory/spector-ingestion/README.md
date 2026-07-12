# spector-ingestion 📥

> **Unified ingestion pipeline — builder-configured chunk → embed → store orchestration.**

`spector-ingestion` defines the core `IngestionPipeline` and `IngestionTarget` interface. It has **no dependency on engine, runtime, or memory** — downstream modules implement the `IngestionTarget` interface for their storage backends.

---

## 🏗️ Architecture

```
spector-ingestion (core pipeline + interface)
├── IngestionPipeline       — builder-configured: chunk → embed → store
├── IngestionTarget         — interface for storage backends
├── IngestionResult         — result record for ingestion operations
├── FileDiscoveryService    — file discovery + title extraction
└── StreamingChunker bridge — bounded-memory file processing

Dependencies:
├── spector-config     (configuration)
├── spector-commons    (TextChunker, StreamingChunker)
└── spector-embed-api  (EmbeddingProvider, ParallelEmbeddingPipeline)
```

> [!IMPORTANT]
> This module does **NOT** depend on `spector-engine` or `spector-memory`. Those modules depend on `spector-ingestion` to implement `IngestionTarget`.

---

## 🚀 Key APIs

### Builder Pattern

```java
// Read config from spector.yml
var config = SpectorConfigFactory.ingestionDefaults(props);

var pipeline = IngestionPipeline.builder()
    .target(myTarget)                          // required
    .embeddingProvider(embedder)               // optional (not needed for pre-embedded)
    .chunking(new TextChunker(config.chunkSize(), config.chunkOverlap()))
    .chunkThreshold(config.chunkSize())        // auto-chunk if content > this
    .build();

// Single API — pipeline decides strategy internally
IngestionResult result = pipeline.ingest("doc-1", content);
```

### IngestionTarget Interface

```java
public interface IngestionTarget {
    void ingest(String id, String text, float[] vector);
    default void storeParentMetadata(String parentId, int chunkCount) {}
    default void onBatchComplete() {}
}
```

**Implementations:**

| Target | Module | Storage path |
|--------|--------|-------------|
| `EngineIngestionTarget` | `spector-engine` | VectorStore → VectorIndex → KeywordIndex |
| `CognitiveIngestionTarget` | `spector-memory` | Quantize → Surprise → Tier route → WAL |

### File Discovery

```java
var discovery = FileDiscoveryService.fromProperties(props, rootDir);
List<Path> files = discovery.discover();

// Title extraction
String title = FileDiscoveryService.extractTitle(content, "fallback.md");
```

### Ingestion Modes

```java
// Auto-chunked text (pipeline decides based on content length)
IngestionResult result = pipeline.ingest("doc-1", longText);

// Pre-embedded (skip embedding)
IngestionResult result = pipeline.ingest("doc-1", text, precomputedVector);

// Streaming file (bounded memory for large files)
IngestionResult result = pipeline.ingest(Path.of("corpus.txt"), "corpus");
```

---

## 📊 Result Tracking

```java
public record IngestionResult(
    String documentId,
    int chunksStored,
    List<String> failures,
    long durationMs
) {
    boolean isFullSuccess();  // true if no failures
}
```

---

## 🔗 How It Fits

All entry points (CLI, MCP, Server) route through `SpectorRuntime`:

```
CLI/MCP/Server → SpectorRuntime.ingestion() → IngestionHandler → IngestionPipeline
                                                                        │
                                                                  ┌─────┴─────┐
                                                                  ▼           ▼
                                                       EngineIngestionTarget  CognitiveIngestionTarget
                                                       (SEARCH mode)          (MEMORY mode)
```

`SpectorRuntime.ingestion()` builds the pipeline with the right target based on the active mode and reads chunking config from `spector.yml`.
