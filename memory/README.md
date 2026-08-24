# Memory & Intelligence Layer (`/memory`)

This directory houses the core algorithmic components of the Spector cognitive memory and search engine. These modules implement biological memory models, high-performance off-heap indexing, vector embedding integrations, and RAG query pipelines.

## Modules

* **[`spector-provider-api`](/memory/spector-provider-api)**: Abstract interfaces and SPIs for embeddings, LLM chat completions, and sparse representations.
* **[`spector-providers`](/memory/spector-providers)**: Concrete provider implementations (Ollama, OpenAI, Local ONNX) with dynamic discovery.
* **[`spector-ingestion`](/memory/spector-ingestion)**: Chunking strategies, sensory extractors (audio, video, PDFs, images), and metadata enrichment.
* **[`spector-memory`](/memory/spector-memory)**: The biological cognitive memory engine. Implements the 4-tier memory architecture (episodic, semantic, procedural, Hebbian graph, bundle kernel) off-heap via Project Panama.
* **[`spector-inspect`](/memory/spector-inspect)**: Binary inspection tool for partition and runtime bundle files.
* **[`spector-metrics`](/memory/spector-metrics)**: Micrometer-based telemetry, Prometheus metrics, and distributed tracing decorators for cognitive memory.

## Dependency Rules

* **Independence Rule**: `spector-memory` and `spector-index` are designed as independent units to enforce modular boundaries. They do **not** depend directly on each other.
* **Flow**: Memory modules depend on the foundation libraries in `/nucleus` and are in turn consumed by `/synapse` and `/bench`.
