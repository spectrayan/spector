---
title: Developer Getting Started
description: Build Spector locally, run the node, store a first memory, and find the main code paths for contributions.
---

# Developer Getting Started

This guide is for contributors who want to clone Spector, run it locally, make a focused change, and verify it before opening a pull request. If you only want to try the product, start with the [Quick Start](quickstart.md) or [Installation](installation.md) guide instead.

Project-level contribution rules live in the [README](https://github.com/spectrayan/spector/blob/main/README.md) and [CONTRIBUTING](https://github.com/spectrayan/spector/blob/main/CONTRIBUTING.md) files. Read those before you push a branch; commits require a sign-off.

## Prerequisites

Install these tools before cloning the repository:

| Tool | Required | Check |
| ---- | -------- | ----- |
| JDK 25 | Yes | `java -version` |
| Maven 3.9 or newer | Yes | `mvn --version` |
| Git | Yes | `git --version` |
| Ollama | Optional, useful for memory and auto-embedding examples | `ollama --version` |
| Docker | Optional, useful for container and integration workflows | `docker --version` |

Use a terminal that preserves normal shell quoting. On Windows PowerShell, prefer `curl.exe` in the examples below so PowerShell does not alias `curl` to `Invoke-WebRequest`.

## Clone And Build

```bash
git clone https://github.com/spectrayan/spector.git
cd spector
mvn clean verify
```

A successful first build ends with `BUILD SUCCESS`. For a faster compile-only check while iterating:

```bash
mvn clean compile
```

To build all modules without running tests:

```bash
mvn clean package -DskipTests
```

## Run The Local Synapse Server

The synapse module starts the embedded Armeria REST/gRPC/SSE server on port `7070` by default:

```bash
mvn spring-boot:run -pl synapse/spector-synapse -Psynapse
```

In another terminal, verify the service:

```bash
curl http://localhost:7070/health
curl http://localhost:7070/api/v1/status
```

To use a different port or API key, pass environment variables or system properties:

```bash
SPECTOR_PORT=7071 SPECTOR_API_KEY=my-secret-key mvn spring-boot:run -pl synapse/spector-synapse -Psynapse
```

If you plan to test automatic embeddings or cognitive memory examples, start Ollama before the node:

```bash
ollama serve
ollama pull nomic-embed-text
```

## Store A First Memory

With the node running, store a semantic memory:

```bash
curl -X POST http://localhost:7070/api/v1/memory/remember \
  -H "Content-Type: application/json" \
  -d '{
    "id": "dev-guide-1",
    "text": "Spector stores agent memories in semantic, episodic, working, and procedural tiers.",
    "tier": "SEMANTIC",
    "source": "USER_STATED",
    "tags": "developer-guide,memory"
  }'
```

The endpoint returns an accepted task response with the generated task id and memory id. See the [REST API reference](../api-reference/rest-endpoints.md) for the full request schema and related recall endpoints.

## Architecture Map

Spector is a multi-module Maven project. Start with these areas when deciding where a change belongs:

| Area | Modules |
| ---- | ------- |
| Search, Storage & Core | `nucleus/spector-core`, `nucleus/spector-commons`, `nucleus/spector-config`, `nucleus/spector-storage` |
| Cognitive Memory & Ingestion | `memory/spector-memory`, `memory/spector-index`, `memory/spector-query`, `memory/spector-gpu`, `memory/spector-rag`, `memory/spector-embed-api`, `memory/spector-embed-ollama`, `memory/spector-ingestion` |
| Gateways & Agentic Runtime | `synapse/spector-synapse`, `synapse/spector-mcp`, `synapse/spector-runtime`, `synapse/spector-cli`, `synapse/spector-client`, `synapse/spector-spring`, `synapse/spector-dist` |
| Observability & Verification | `nucleus/spector-events`, `nucleus/spector-metrics`, `nucleus/spector-test-support`, `bench/spector-bench` |

For a deeper walkthrough, read the [architecture overview](../architecture/overview.md), [module guide](../modules/index.md), and module-specific README files.

## Run Focused Tests

Run the full test suite before opening a pull request when practical:

```bash
mvn test
```

For focused work, test the touched module and any required upstream modules:

```bash
mvn test -pl memory/spector-memory -am
mvn verify -pl synapse/spector-synapse -Psynapse -am
```

To run a single test class:

```bash
mvn test -pl spector-core -Dtest=DotProductTest
```

Use `mvn clean verify` before submitting changes that touch public APIs, module boundaries, packaging, or documentation snippets that are checked during the site build.

## IDE Setup

IntelliJ IDEA, Eclipse, and VS Code can import the root `pom.xml` as a Maven project. Set the project SDK to JDK 25 and let Maven manage compiler flags and module dependencies.

Useful defaults:

- Enable automatic Maven project import.
- Delegate build and test actions to Maven if IDE classpath resolution differs from the command line.
- Run the server from Maven with the `spector-synapse` command above, or create a run configuration for `com.spectrayan.spector.synapse.SynapseApplication`.
- Keep generated build output under each module's `target/` directory out of commits.

## Contribution Checklist

Before opening a pull request:

- Confirm the issue is still open and no existing pull request solves it.
- Create a branch with a focused name, for example `docs/getting-started-developer-guide`.
- Keep the change small enough to review in one pass.
- Add or update tests/docs for behavior changes.
- Run the narrowest useful check, then the broader Maven check when the change warrants it.
- Commit with a conventional message and sign off: `git commit -s -m "docs: add developer getting started guide"`.

## Troubleshooting

| Symptom | Fix |
| ------- | --- |
| Maven uses an older JDK | Set `JAVA_HOME` to JDK 25 and reopen the terminal. |
| `curl` behaves differently on PowerShell | Use `curl.exe` instead of `curl`. |
| Port `7070` is already in use | Pass a different port with `-Dexec.args="7071 384"`. |
| Memory examples fail to embed content | Start Ollama and pull `nomic-embed-text`, or inspect node logs for the configured embedding provider. |
| A module test cannot resolve local classes | Add `-am` so Maven also builds required upstream modules. |
