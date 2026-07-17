# Memory & Intelligence Layer (`/memory`)

This directory houses the core algorithmic components of the Spector cognitive memory and search engine. These modules implement biological memory models, high-performance off-heap indexing, vector embedding integrations, and RAG query pipelines.

## Modules

* **[`spector-embed-api`](/memory/spector-embed-api)**: Abstract interface for vectorizing text chunks.
* **[`spector-embed-ollama`](/memory/spector-embed-ollama)**: Concrete local LLM embedding implementation using Ollama.
* **[`spector-gpu`](/memory/spector-gpu)**: Optional CUDA-accelerated distance metrics for high-throughput batch vector comparison.
* **[`spector-index`](/memory/spector-index)**: Core nearest-neighbor search indexes (HNSW, flat array brute force) and custom distance metrics.
* **[`spector-ingestion`](/memory/spector-ingestion)**: Chunking strategies, semantic metadata extraction, and ingestion pipelines.
* **[`spector-memory`](/memory/spector-memory)**: The biological cognitive memory engine. Implements the 4-tier memory architecture (episodic, semantic, procedural, Hebbian graph) off-heap via Project Panama.
* **[`spector-query`](/memory/spector-query)**: Retrieval pipelines, hybrid search scoring, and importance/salience score fusions.
* **[`spector-rag`](/memory/spector-rag)**: Retrieval-Augmented Generation workflows and LLM orchestration.

## Dependency Rules

* **Independence Rule**: `spector-memory` and `spector-index` are designed as independent units to enforce modular boundaries. They do **not** depend directly on each other.
* **Flow**: Memory modules depend on the foundation libraries in `/nucleus` and are in turn consumed by `/synapse` and `/bench`.
