# Spector Synapse ⚡🧠

[![License](https://img.shields.io/badge/License-BSL%201.1-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25+-green.svg)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-brightgreen.svg)](https://spring.io/projects/spring-boot)

The **central nervous system** of the Spector ecosystem. Spring Boot 4 + Armeria entry point for cognitive chat, autonomous agent orchestration, LLM provider management, data connector routing, and channel adapters. Serves the [Cortex UI](../spector-cortex/) as static assets.

## Architecture

```
┌────────────────────────────────────────────────┐
│  Cortex UI (Angular 22)                        │
│  — Chat, Memories, Agents, Connectors          │
└──────────────────────┬─────────────────────────┘
                       │ HTTP
┌──────────────────────▼─────────────────────────┐
│  Spector Synapse (Spring Boot 4 + Armeria)     │
│  ┌─────────────────────────────────────────┐   │
│  │ Agent Framework     │ Connector SPI     │   │
│  │ · AgentSoul         │ · Templates       │   │
│  │ · ToolRegistry      │ · Routing         │   │
│  │ · LangGraph4j       │ · Health/Metrics  │   │
│  ├─────────────────────┼───────────────────┤   │
│  │ Chat Orchestration  │ Provider Registry │   │
│  │ · Memory priming    │ · Ollama bridge   │   │
│  │ · Summarizer        │ · LLM abstraction │   │
│  │ · Reflector         │ · Health checks   │   │
│  └─────────────────────┴───────────────────┘   │
│  ┌─────────────────────────────────────────┐   │
│  │ Plugin SPI · MCP Endpoint · REST APIs   │   │
│  └─────────────────────────────────────────┘   │
└──────────────────────┬─────────────────────────┘
                       │ Java API
┌──────────────────────▼─────────────────────────┐
│  Spector Core                                  │
│  engine · memory · rag · embed · ingestion     │
└────────────────────────────────────────────────┘
```

## Building

Synapse is gated behind a Maven profile and is **not part of the default reactor build**.

```bash
# Build core + synapse
mvn clean compile -Psynapse

# Build and test synapse only (after core is built)
mvn verify -pl spector-synapse -Psynapse

# Run the Synapse server
mvn spring-boot:run -pl spector-synapse -Psynapse
```

## Docker

```bash
# Build and run with Docker Compose
docker compose -f docker-compose.synapse.yml up --build
```

## Key Components

| Component | Description |
|:---|:---|
| **Agent Framework** | `AgentSoul`, `ToolRegistry`, cognitive graph builder |
| **Chat Orchestration** | Memory-primed conversations, summarization, reflection |
| **Connector SPI** | Template-based data connector registration |
| **Provider Registry** | LLM provider management with health checks |
| **Plugin SPI** | Runtime plugin loading and lifecycle |
| **MCP Endpoint** | Model Context Protocol tool discovery & invocation |
| **Memory Bridge** | Bidirectional integration with Spector Memory |

## Contributing

We welcome contributions! Synapse is a great place to contribute:

- 🛠️ **Agent tools** — add new tools in `agent/tools/`
- 🔌 **Connector templates** — add templates in `connector/templates/`
- 🔧 **Provider integrations** — add new LLM providers
- 🧪 **Tests** — improve test coverage

See the [Contributing Guide](../CONTRIBUTING.md) for setup instructions and the `-Psynapse` profile.

## License

This module is licensed under the **Business Source License 1.1 (BSL 1.1)**.

- **Change Date**: July 6, 2030
- **Change License**: Apache License, Version 2.0

See [LICENSE](LICENSE) for full terms.
