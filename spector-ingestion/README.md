# spector-ingestion 📥

> **High-throughput document ingestion pipelines and parallel streaming processors for Spector Search.**

`spector-ingestion` implements highly parallel, lock-free document ingestion loops. It processes massive, streaming batches of unstructured data by binding parallel tokenizer and chunking pipelines directly to modern Virtual Thread executor contexts.

---

## 🏗️ Core Architecture & Roles

1. **`IngestionPipeline`:** Manages end-to-end ingestion lifecycles (parse -> chunk -> embed -> index).
2. **`StreamIngester`:** Efficiently streams tokens from databases, file systems, or network sockets into a sliding window parser.
3. **`BatchProcessor`:** Dynamically aggregates documents into batches and queues them for GPU/CPU parallel vector calculations.

---

## 🚀 Key APIs

### Setting Up a Parallel Streaming Pipeline
```java
// Setup a streaming processor with 4 parallel threads
IngestionPipeline pipeline = IngestionPipeline.builder()
    .engine(engine)
    .embeddingProvider(provider)
    .concurrency(4)
    .batchSize(100)
    .build();

// Stream and process documents in the background
pipeline.processStream(documentStream);
```
