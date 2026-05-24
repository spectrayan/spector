# spector-query 🔍

> **Hybrid orchestrator, Reciprocal Rank Fusion (RRF), and LLM re-ranking engine.**

`spector-query` coordinates hybrid searches by executing parallel keyword (BM25) and semantic vector (HNSW) searches on Virtual Threads, fusing their scores using Reciprocal Rank Fusion (RRF), and executing final listwise LLM relevance re-ranking for precision-critical pipelines.

---

## 🏗️ Core Architecture & Roles

1. **Hybrid Query Planner (`HybridQueryPlanner`):** Orchestrates parallel execution of keyword search and vector search legs on JVM Virtual Threads.
2. **Reciprocal Rank Fusion (`RrfCombiner`):** Fuses keyword rankings and vector rankings using rank-based scores:
   $$RRF\_Score(d) = \sum_{m \in M} \frac{1}{k + r_m(d)}$$
   This prevents raw scoring scale differences from skewing the results.
3. **Listwise LLM Re-ranker (`OllamaReranker`):** Sends the top retrieved candidates to a local Ollama LLM (e.g. `llama3.2`) in a listwise prompt to calculate final relevance scores.

---

## 🚀 Key APIs

### Executing an Orchestrated Hybrid Query
```java
// Coordinates both legs in parallel using Virtual Threads
SearchResponse response = HybridQueryPlanner.execute(
    engine,
    "java vector api",    // keyword text
    queryVector,          // semantic vector
    10                    // topK
);
```

### RRF Fusion
```java
ScoredResult[] keywordResults = ...;
ScoredResult[] vectorResults = ...;
int k = 60; // default RRF constant

ScoredResult[] fused = RrfCombiner.fuse(keywordResults, vectorResults, k);
```
