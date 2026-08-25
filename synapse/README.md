# Synapse Layer (`/synapse`)

This directory contains the runtime coordinator, endpoint adapters, client SDKs, and application distributions for the Spector headless engine. The synapse layer acts as the bridge connecting the core memory/search engine to the outside network.

## Modules

* **[`spector-cli`](/synapse/spector-cli)**: Command-line interface (`spectorctl`) for administration, diagnostic control, and standalone MCP server runner (packaged as `spector.jar`).
* **[`spector-connector`](/synapse/spector-connector)**: Apache Camel-based integration connector runtime with dynamic YAML route templates, PII scrubbing, row-level SQL splitting, and direct Spector Memory ingestion sinks.
* **[`spector-mcp`](/synapse/spector-mcp)**: Model Context Protocol (MCP) server implementation allowing LLM agents to recall/remember memories directly over STDIO/SSE.
* **[`spector-spring`](/synapse/spector-spring)**: Spring AI auto-configurations and embedded `SpectorVectorStore` integration.
* **[`spector-batch`](/synapse/spector-batch)**: Spring Batch migration engine for offline bulk memory loading and re-indexing.
* **[`spector-synapse`](/synapse/spector-synapse)**: Spring Boot 4 core entry point that exposes high-performance REST, SSE, and MCP endpoints.

## Dependency Rules

* **Flow**: Synapse modules occupy the application runtime tier. They depend directly on the lower algorithmic layers in `/memory` and `/nucleus`. 
* **Integration**: All runtime wiring occurs here, separating domain mathematics and indexing code from transport protocols and frameworks.
