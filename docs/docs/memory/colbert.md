---
title: "Late-Interaction Reranking (ColBERT v2)"
description: "Token-level late-interaction reranking using ColBERT v2."
---

# 🚀 Late-Interaction Reranking (ColBERT v2)

For queries requiring maximum precision, Spector implements a native, hardware-accelerated **ColBERT v2 reranking layer**. By executing token-level late-interaction calculations directly in cache-aligned memory, Spector avoids the latency and complexity of traditional cross-encoder neural rerankers.

---

## 🧮 How Late Interaction Works

Unlike single-vector embedding models (which compress a whole document into a single vector) or cross-encoders (which feed the query and document together into an LLM), ColBERT generates a **separate vector for every single token** in the query and document.

At query time, the similarity is calculated using the **MaxSim** operator:

\[Score(Q, D) = \sum_{i=1}^{|Q|} \max_{j=1}^{|D|} \left( q_i \cdot d_j^T \right)\]

Where:
- $q_i$ is the embedding vector for the $i$-th query token.
- $d_j$ is the embedding vector for the $j$-th document token.
- $q_i \cdot d_j^T$ is the cosine similarity (or dot product) between the query token vector and the document token vector.

```
Query Tokens              Document Tokens
  [Where]    ───────┐        [The]
                    ├──Max── [flight] (highest similarity dot-product)
  [is]       ───────┼──Max── [status]
  [my]       ───────┼──Max── [is]
  [flight]   ───────┼──Max── [delayed]
  [status]   ───────┘
                     │
                     ▼ (Sum over query tokens)
                 Final Score
```

Late interaction preserves fine-grained token-level associations. This allows Spector to identify documents where specific words align correctly, achieving cross-encoder accuracy with a fraction of the computational overhead.

---

## ⚡ SIMD & Token Caching

To execute late-interaction scoring at microsecond speeds, Spector employs two major optimizations:

### 1. Vectorized MaxSim Kernel
Spector parallelizes the MaxSim dot-product search loop using hardware SIMD registers (such as AVX2 or AVX-512). The scoring kernel processes multiple query and document tokens concurrently in registers, evaluating millions of token comparisons in under 200 microseconds per candidate list.

### 2. Off-Heap Token Caching
Generating token embeddings for documents on every search is highly expensive. Spector's ingestion pipeline extracts token vectors and caches them in contiguous memory blocks. At search time, Spector reads these token vectors directly without creating garbage collection pressure or serialization overhead.

---

## ⚙️ Configuration & Execution

ColBERT reranking is configured globally or requested for individual queries.

### Global defaults in `spector.yml`

```yaml
spector:
  memory:
    colbert-enabled: true    # Toggle native ColBERT capability
```

### Query-level settings

Configure ColBERT via query-level options:

- **`enableReranker`**: Set to `true` to activate ColBERT reranking on the candidate set. Defaults to `false` (opt-in for cost/latency trade-offs).
- **`rerankerDepth`**: The number of top candidates pre-fetched by first-stage retrievers to pass to the reranker (typically `10` to `50`).
- **`textSearchMode`**: Setting this to `COLBERT_RERANK` or `FULL_STACK` automatically enables first-stage hybrid search combined with late-interaction ColBERT reranking.
