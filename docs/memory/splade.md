---
title: "Learned Sparse Search (SPLADE)"
description: "SPLADE and Li-LSR learned sparse retrieval indexing and configurations."
---

# 🧬 Learned Sparse Search (SPLADE)

Spector implements **Learned Sparse Retrieval (LSR)**, supporting the **SPLADE (Sparse Lexical and Expansion Model)** architecture and the fast **Li-LSR** (Inference-Free Learned Sparse Retrieval) model. 

Learned sparse retrieval combines the advantages of lexical search (exact-matching, fast inverted index lookups) with the advantages of dense vector search (semantic awareness, synonym matching).

---

## 🧠 How SPLADE Works

SPLADE encodes a document or query into a sparse vector that matches the size of the vocabulary (typically ~30,522 dimensions). However, instead of only marking terms that *literally* exist in the text (like BM25), the model performs **term expansion**:

```
Input: "Where is my flight status?"
        │
        ▼ (Neural Transformer Term Expansion)
Expanded Terms:
  - flight:    5.2
  - status:    4.8
  - airplane:  3.9  (expanded synonym)
  - departure: 3.5  (expanded synonym)
  - delay:     2.8  (expanded synonym)
```

By indexing these expanded terms with their relative weights into an inverted index, Spector captures synonyms and semantic relationships lexically, completely resolving the vocabulary mismatch problem.

---

## 🏗️ Architectural Concepts

Spector structures sparse retrieval through the following concepts:

### 1. Sparse Embeddings
When a text is ingested, it is passed to a sparse embedding model which outputs token IDs (or string terms) and their corresponding positive activation values (representing term importance weights).

### 2. Learned Sparse Indexing
Spector indexes sparse weights in a high-performance inverted index:
- **Inverted Posting Lists**: The index maps each vocabulary token to a postings list of document IDs and activations. These lists are stored in contiguous memory structures for fast traversal.
- **Max-Score Pruning**: At query time, Spector uses threshold-gating and max-score pruning algorithms to evaluate only the most promising postings lists, achieving sub-millisecond search latencies across millions of documents.

### 3. Li-LSR (Inference-Free Sparse)
For ultra-low latency requirements, Spector implements **Li-LSR**. This model replaces neural model query inference with static, high-quality term-expansion lookup tables. This eliminates the neural model execution penalty at query time, bringing learned sparse query latencies down to microseconds.

---

## ⚙️ Configuration & Execution

Sparse retrieval is controlled by global and query-level properties.

### Global defaults in `spector.yml`

```yaml
spector:
  memory:
    splade-enabled: true     # Master toggle for learned sparse search
```

### Query-level settings

Configure sparse search via query parameters:

- `SPLADE`: Runs learned sparse retrieval only, bypassing both dense HNSW and BM25 search.
- `SPLADE_HYBRID`: Runs a hybrid query combining dense vector search and SPLADE, merging results via Reciprocal Rank Fusion.
- `LI_LSR`: Forces query execution path to use the fast, inference-free Li-LSR lookup tables.
- `FULL_STACK`: Runs BM25, SPLADE, and dense vector searches concurrently, then passes candidates to the late-interaction reranking layer.
