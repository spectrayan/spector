# AGENTS.md — Agentic AI Foundation Developer & Agent Guide

> **Agent-to-Agent (A2A) & Developer Agent Manifest for Spector**  
> Compliant with the [AAIF AGENTS.md Specification](https://github.com/agent-infra/agents.md).  
> Designed for autonomous coding agents (Goose, Claude Code, Cursor, Copilot, Codex, OpenClaw).

---

## 🤖 Repository Overview

Spector is the high-performance, zero-overhead cognitive memory backbone for autonomous AI agents. It provides biological memory mechanisms—working, episodic, semantic, and procedural memory tiers with power-law temporal decay, Hebbian associative graphs, homeostatic affective regulation, and fused 6-phase cognitive scoring.

- **Primary Stack**: Java 25 (OpenJDK), Project Panama Foreign Function & Memory (FFM), Java Vector API (SIMD), Virtual Threads.
- **Frontend / Inspection UI**: Angular 22 + Three.js (`cortex/spector-cortex`).
- **SDKs**: Python (`sdks/python`), TypeScript/Node.js (`sdks/typescript`), Java client (`sdks/java`).
- **Licensing**: 100% [Apache License 2.0](LICENSE).
- **Governance**: Linux Foundation / AAIF meritocracy with DCO 1.1 sign-off ([GOVERNANCE.md](GOVERNANCE.md)).

---

## 🛠️ Environment & Build Commands

### Prerequisites
- **Java**: OpenJDK 25 (e.g. Eclipse Temurin 25 or SDKMAN: `sdk use java 25-tem`).
- **Build System**: Apache Maven 3.9+.
- **Node.js**: Node 22+ (only needed when modifying `cortex/spector-cortex` or SDKs).

### Common Commands

```bash
# Zero-credential reactor compilation (all 27 Java modules)
mvn clean compile

# Fast compilation skipping tests
mvn clean test-compile -DskipTests

# Run unit tests on spector-memory core
mvn test -pl memory/spector-memory

# Check license header compliance across all modules
mvn license:check

# Automatically format missing or misaligned license headers
mvn license:format

# Validate Mermaid diagram syntax across documentation
node scratch/validate_mermaid.js

# Build documentation site in strict mode
mkdocs build --strict
```

---

## 🔌 Agent Integration: MCP & Tool Interfaces

Spector exposes its cognitive memory directly to agents via the **Model Context Protocol (MCP)** standard (`synapse/spector-mcp`).

### Available MCP Tools

| Tool Name | Purpose | Key Parameters |
|:---|:---|:---|
| `memory_remember` | Ingest an observation or fact into memory with cognitive tags | `content`, `tags`, `importance`, `valence`, `domain` |
| `memory_recall` | Query memory using fused 6-phase cognitive scoring scan | `query`, `limit`, `profile`, `min_score`, `domain` |
| `memory_reinforce` | Strengthen Hebbian associations or increment engram recall count | `memory_id`, `delta` |
| `memory_introspect` | Retrieve cognitive state, salience distribution, and memory statistics | `namespace`, `include_distribution` |
| `memory_why_not` | Explain why a specific candidate engram was dropped during retrieval | `memory_id`, `query` |
| `memory_status` | Health check, active mmap slab capacity, and off-heap allocations | None |

### Goose Framework Integration

To configure Spector Memory in **Goose** (`~/.config/goose/config.yaml`):

```yaml
extensions:
  spector-memory:
    enabled: true
    type: stdio
    cmd: java
    args:
      - "--enable-preview"
      - "--add-modules=jdk.incubator.vector"
      - "-jar"
      - "/path/to/spector/synapse/spector-mcp/target/spector-mcp-0.1.0-SNAPSHOT.jar"
```

---

## 📐 Architecture Conventions for AI Agents

When authoring code or refactoring components in Spector, coding agents MUST adhere to these architectural invariants:

1. **Zero External Dependencies in Engine**:
   - `nucleus/spector-core`, `nucleus/spector-cpu`, `memory/spector-memory`, and `nucleus/spector-index` must NEVER depend on third-party frameworks (no Spring, Netty, Jackson, Guava, or Commons-Lang).
   - Use standard JDK APIs only (`java.lang.foreign`, `jdk.incubator.vector`, `java.lang.ScopedValue`).

2. **Error Taxonomy & Exception Handling**:
   - All throw sites must use `ErrorCode` and `SpectorException` adhering to the `SPE-XXX-YYY` scheme defined in [ADR-0070](docs/adr/0070-unified-error-taxonomy-and-exception-handling.md).
   - Use parameterized SLF4J templates (`{}`) avoiding expensive string concatenations on hot paths.

3. **Concurrency & Threading (Model B)**:
   - Subsystem libraries must never spawn raw unmanaged threads. Use `SpectorTaskQueue` with context-propagated `ScopedValue` bindings ([ADR-0076](docs/adr/0076-zero-dependency-pluggable-cache-abstraction.md), [ADR-0077](docs/adr/0077-model-b-asynchronous-task-queue-concurrency.md)).

4. **Off-Heap Memory Safety**:
   - All off-heap allocations must use Panama `Arena.ofShared()` or `Arena.ofConfined()` with bounded lifetimes.
   - Slices must be memory-aligned (`SegmentAllocator`, 64-byte cache line alignment).

5. **Commit Message Format**:
   - Conventional Commits: `type(scope): description`.
   - Always sign off with DCO 1.1 (`git commit -s`).
   - When acting as the Forge persona, include `Co-authored-by: Bharat Joshi <bharatjoshi@spectrayan.com>`.

6. **Architectural Decisions**:
   - Review [docs/adr/catalog.md](docs/adr/catalog.md) before altering storage layouts, scoring logic, or cognitive pathway relays.
   - Follow the 8-section layout in `docs/adr/0000-template.md` when introducing new architectural proposals.
