# Synapse Layer (`/synapse`)

This directory contains the runtime coordinator, endpoint adapters, client SDKs, and application distributions for the Spector headless engine. The synapse layer acts as the bridge connecting the core memory/search engine to the outside network.

## Modules

* **[`spector-cli`](/synapse/spector-cli)**: Command-line interface (`spectorctl`) for administration and diagnostic control.
* **[`spector-client`](/synapse/spector-client)**: Java client SDK providing standard API connections to a remote Spector node.
* **[`spector-dist`](/synapse/spector-dist)**: Distribution packaging module that bundles the application into runnable Docker containers and deployment zip files.
* **[`spector-mcp`](/synapse/spector-mcp)**: Model Context Protocol (MCP) server implementation allowing LLM agents (e.g. Claude) to recall/remember memories directly.
* **[`spector-runtime`](/synapse/spector-runtime)**: The orchestrator that wires index components, memory files, and query pipelines into a running Spector node.
* **[`spector-spring`](/synapse/spector-spring)**: Spring AI auto-configurations and client integrations.
* **[`spector-synapse`](/synapse/spector-synapse)**: Spring Boot 4 + Armeria core entry point that exposes high-performance gRPC, REST, and MCP endpoints on a single port.

## Dependency Rules

* **Flow**: Synapse modules occupy the application runtime tier. They depend directly on the lower algorithmic layers in `/memory` and `/nucleus`. 
* **Integration**: All runtime wiring occurs here, separating domain mathematics and indexing code from transport protocols and frameworks.
