# ADR-0066: Engine & CLI Stabilization — Issue #727 Hardening

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-30 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

## 1. Context & Problem Statement

Spector Memory's cognitive capabilities—Project Panama off-heap storage, fused SIMD vector search, 4-tier biological memory, sleep consolidation, and native Model Context Protocol (MCP) server—provide an industry-leading cognitive memory platform. However, initial onboarding currently presents severe friction points:

1. **The Zero-Config Crash**: In `SpectorAutoConfiguration`, if no external embedding provider (Ollama, OpenAI, Anthropic, Azure, Google, Mistral) is configured, the Spring context throws `SpectorInternalException(ErrorCode.ARGUMENT_NULL)` and terminates. A new user cannot test `remember` or `recall` without first installing and running an external service like Ollama or provisioning cloud API keys.
2. **The CLI Split-Brain**: The CLI fat JAR (`spector.jar` from `synapse/spector-cli`) provides Picocli commands for `mcp`, `remember`, `recall`, `index`, and `status`, but lacks a `serve` command. To run the REST API and web dashboard, users must locate and run `spector-synapse-*.jar`, creating confusion over which binary to run.
3. **Absence of Environment Diagnostics & Scaffolding**: Spector requires JDK 25 with preview and incubator Vector API flags (`--add-modules jdk.incubator.vector --enable-preview --enable-native-access=ALL-UNNAMED`). When developers run into classpath or JVM mismatch issues, they receive low-level JVM error dumps rather than actionable diagnostics.

We require an architectural stabilization of the core engine and CLI to enable an instant, zero-config first run in under 3 seconds.

---

## 2. Problem Statement

Initial developer feedback highlighted critical friction points when running the CLI (`spectorctl`) or bootstrapping a local memory environment:

1. **Missing External Service Dependencies**: Requiring an external OpenAI/Ollama embedding endpoint meant simple CLI exploration failed immediately on clean installs.
2. **Spring Context Startup Latency**: Running routine commands (`spectorctl --version`, `spectorctl status`) suffered from 2–3s Spring Boot web context startup delays.
3. **Fragile Uninitialized Directories**: Commands failed ungracefully if local storage directories (`data/`, `namespaces/`) had not been pre-created.

## 3. Decision Drivers

- **Zero-Config Developer Onboarding**: Ship a native, in-process ONNX embedding model (MiniLM-L6-v2) as a self-contained fallback requiring zero API keys or external services.
- **Sub-100ms CLI Startup**: Enforce pure Picocli execution (`WebApplicationType.NONE`) for standard administration commands.
- **Self-Healing Initial Scaffolding**: Implement an automated `InitCommand` creating required directory trees and configuration templates seamlessly.

## 4. Considered Options

## 5. Alternatives Considered

| Alternative | Evaluation | Verdict |
|:---|:---|:---|
| **Lexical Hash Projection (LSH)** | Generates deterministic pseudo-vectors without machine learning weights. Fast and lightweight (~10 KB). | **Rejected**: Fails semantic similarity queries (e.g., "automobile" does not match "car"). Developers testing Spector would experience poor recall quality. |
| **Mandatory Ollama Pre-requisite** | Require users to download Ollama and pull `nomic-embed-text`. | **Rejected**: Adds 5-10 minutes of friction, requires 500MB+ download, and fails in air-gapped or CI environments. |
| **Separate `spector-server.jar` vs `spector-cli.jar`** | Keep CLI and REST server as separate Maven distributions. | **Rejected**: Confuses developers. Single-binary CLI with `serve` and `mcp` subcommands is the standard pattern (e.g., Vault, Consul, Qdrant). |

---

## 5. Decision Outcome

## 2. Architectural Decisions

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                         Spector CLI & Runtime Architecture                       │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                 spector CLI Entry                                │
│                   (picocli + Spring Boot ApplicationContext)                     │
│                                                                                  │
│   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐      │
│   │ spector init │   │spector doctor│   │ spector mcp  │   │spector serve │      │
│   └──────┬───────┘   └──────┬───────┘   └──────┬───────┘   └──────┬───────┘      │
│          │                  │                  │                  │              │
│          ▼                  ▼                  ▼                  ▼              │
│     Scaffolding        Diagnostics         STDIO JSON-RPC     HTTP / SSE         │
│     ~/.spector/        JVM, SIMD,          Agent Protocol     Port :7070         │
│     spector.yml        Ollama, Path          (31 tools)     REST + Events        │
├──────────────────────────────────────────────────────────────────────────────────┤
│                         SpectorAutoConfiguration Engine                          │
│                                                                                  │
│   ┌──────────────────────────────────────────────────────────────────────────┐   │
│   │                     Embedding Provider Resolution                        │   │
│   │                                                                          │   │
│   │   [External Provider Configured?]                                        │   │
│   │          ├── YES ──► Ollama / OpenAI / Anthropic / Google / Mistral      │   │
│   │          └── NO  ──► In-Process Native ONNX Embedding Provider           │   │
│   │                      (AllMiniLmL6V2QuantizedEmbeddingModel - 384 dims)   │   │
│   └──────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   ┌──────────────────────────────────────────────────────────────────────────┐   │
│   │                    Spector Cognitive Memory Backbone                     │   │
│   │   Working Memory ──► Episodic Memory ──► Semantic Store ──► Procedural   │   │
│   │   Zero-GC Panama FFM Off-Heap Storage | SIMD Dot-Product Cosine Scoring   │   │
│   └──────────────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Decision 1: In-Process Native ONNX Embedder as Zero-Config Fallback
- **Component**: `OnnxProviderFactory` and `OnnxEmbeddingProvider` in `memory/spector-providers` already support in-process LangChain4j ONNX models.
- **Dependency**: Package `dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2-q` into `spector-synapse` and `spector-cli`.
- **Resolution Logic**: In `SpectorAutoConfiguration.java`, if no external `EmbeddingProvider` bean is registered, auto-configure:
  ```java
  @Bean
  @ConditionalOnMissingBean(EmbeddingProvider.class)
  EmbeddingProvider defaultFallbackOnnxEmbeddingProvider() {
      log.info("[Spector Engine] No external embedding provider configured. Activating built-in In-Process Native ONNX Embedder (all-MiniLM-L6-v2-q, 384 dims).");
      return new OnnxEmbeddingProvider(
          new AllMiniLmL6V2QuantizedEmbeddingModel(),
          "all-MiniLM-L6-v2-q",
          384
      );
  }
  ```
- **Dimensionality**: Automatically defaults memory vector dimensions to 384 when the ONNX fallback is active, requiring no manual YAML edits.

### Decision 2: Preserving Pure CLI (`WebApplicationType.NONE`) & Dedicated Synapse Server
- **Pure CLI Discipline**: `spector-cli` remains strictly a lightweight command-line tool (`WebApplicationType.NONE` with Picocli). It contains **zero** servlet containers (no Tomcat/Jetty) and **zero** web controllers.
- **Dual-Mode Execution in CLI**:
  1. **Embedded Mode (`cli-embedded`)**: Used for `spector mcp` (STDIO JSON-RPC for Claude Desktop/Cursor) and local batch ingestion. Boots in-process `SpectorMemory` with the bundled ONNX fallback embedder over standard I/O.
  2. **Remote Client Mode (`cli-remote`)**: Default for `spector recall`, `remember`, `status`, and `index`. Uses `SpectorHttpClient` to issue fast sub-second HTTP REST calls to a running Synapse server on `:7070`.
- **Dedicated Server**: `spector-synapse` (`SynapseApplication`) remains the sole Spring Boot 4 web server daemon providing REST APIs, SSE streaming (`/api/v1/events`), HTTP MCP (`/mcp`), and static Cortex UI. It is executed via Docker or `java -jar spector-synapse.jar`.

### Decision 3: Self-Healing Scaffolding (`InitCommand`)
- **Subcommand**: `spector init [--data-dir <path>]`
- **Behavior**:
  - Checks if `~/.spector/spector.yml` exists. If not, writes a clean starter configuration.
  - Provisions `~/.spector/data/memory` and `~/.spector/data/index` directories.
  - Sets safe file permissions (0700 on Unix).

### Decision 4: Environment Diagnostic Suite (`DoctorCommand`)
- **Subcommand**: `spector doctor`
- **Checks**:
  1. **Java Version**: Verifies JDK 25+ (`java.version` >= 25).
  2. **Vector API**: Checks if `jdk.incubator.vector.FloatVector` is accessible and functional.
  3. **SIMD Acceleration**: Probes hardware flags for AVX-512, AVX2, or ARM NEON.
  4. **Data Directory**: Validates read/write access to storage paths.
  5. **Ollama Reachability**: Probes `http://localhost:11434/api/tags` (optional, reports status without failing).
  6. **Client Config Generator**: Emits formatted JSON configuration blocks for Claude Desktop, Cursor, and Windsurf with absolute paths resolved for the current host.

---

## 3. Component Design & Contracts

### A. Subcommand Hierarchy in `SpectorCtl.java`
```java
@Command(
    name = "spector",
    aliases = {"spectorctl"},
    subcommands = {
        InitCommand.class,
        DoctorCommand.class,
        McpCommand.class,
        ServeCommand.class,
        RememberCommand.class,
        RecallCommand.class,
        IndexCommand.class,
        StatusCommand.class,
        MemoryCommand.class
    }
)
public class SpectorCtl implements Runnable { ... }
```

### B. Fallback Fall-Through Matrix
| Configuration State | Resulting Embedding Provider | Vector Dims | External Services Needed |
|:---|:---|:---:|:---:|
| `spector.provider.embedding.type: Ollama` | `OllamaEmbeddingProvider` | Configured (e.g. 768) | Ollama running |
| `spector.provider.embedding.type: OpenAi` | `OpenAiEmbeddingProvider` | Configured (e.g. 1536) | OpenAI API Key |
| `OPENAI_API_KEY` in environment | `OpenAiEmbeddingProvider` | 1536 | OpenAI API Key |
| **No configuration / No environment keys** | **`OnnxEmbeddingProvider` (`all-MiniLM-L6-v2-q`)** | **384** | **NONE (100% Offline)** |

---

## 6. Pros and Cons of the Options

## 4. Consequences & Trade-offs

### Positive
- **3-Second Zero-Config Onboarding**: Developers clone or download `spector.jar` and can immediately run `spector mcp` or `spector serve` with semantic recall working offline.
- **Single Unified Binary**: No confusion between `spector.jar` (CLI) and `spector-synapse.jar` (Server); one binary handles all execution modes.
- **Actionable Diagnostics**: `spector doctor` eliminates guesswork regarding Vector API flags or missing runtime directories.

### Negative
- **Distribution Size**: Including `langchain4j-embeddings-all-minilm-l6-v2-q` adds ~25 MB of quantized model weights to the fat JAR.
- **Memory Footprint**: In-process ONNX model requires ~100 MB of native memory for weights during active inference.

### Mitigations
- The 8-bit quantized MiniLM model preserves >98% semantic fidelity while keeping memory overhead negligible compared to Java Panama off-heap allocations.

---

## 7. Implementation Plan

## 6. Implementation Plan & Persona Allocation

1. **Maintainer (Core & Synapse)**:
   - Add `langchain4j-embeddings-all-minilm-l6-v2-q` to `pom.xml` dependencyManagement and `spector-synapse`/`spector-cli`.
   - Update `SpectorAutoConfiguration.java` to auto-wire the fallback ONNX embedder.
   - Implement `ServeCommand.java`, `DoctorCommand.java`, and `InitCommand.java` in `synapse/spector-cli`.

2. **Maintainer (Quality Assurance)**:
   - Integration tests verifying zero-config boot, offline `remember`/`recall` cycle, and CLI commands.

3. **Maintainer (Infrastructure & Platform)**:
   - Verify fat JAR shade configuration and update `scripts/start-mcp.bat` and shell wrappers.

## 8. Code Reference & Verification

All stabilization features and fallback embedders are verified in the codebase:
- **CLI Command Tree**: `synapse/spector-cli/src/main/java/com/spectrayan/spector/cli/`
- **Native ONNX Fallback**: `nucleus/spector-providers/src/main/java/com/spectrayan/spector/providers/onnx/`
- **Initialization Command**: `synapse/spector-cli/src/main/java/com/spectrayan/spector/cli/cmd/InitCommand.java`
