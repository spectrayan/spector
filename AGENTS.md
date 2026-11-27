# AGENTS.md — Autonomous Agent & Developer Guide

> **Agent-to-Agent (A2A) & Developer Agent Manifest for Spector**  
> Compliant with the [AGENTS.md Specification](https://github.com/agent-infra/agents.md).  
> Designed for autonomous coding agents (Goose, Claude Code, Cursor, Copilot, Codex, OpenClaw).

---

## 🤖 Repository Overview

Spector is a zero-overhead cognitive memory engine for autonomous AI agents. It implements working, episodic, semantic, and procedural tiers with power-law temporal decay, co-activation association graphs, valence-weighted ranking, and fused multi-phase recall scoring.

- **Primary Stack**: Java 25 (OpenJDK), Project Panama Foreign Function & Memory (FFM), Java Vector API (SIMD), Virtual Threads.
- **Frontend / Inspection UI**: Angular 22 + Three.js (`cortex/spector-cortex`).
- **SDKs**: Python (`sdks/python`), TypeScript/Node.js (`sdks/typescript`), Java client (`sdks/java`).
- **Licensing**: 100% [Apache License 2.0](LICENSE).
- **Governance**: Meritocratic open governance with DCO 1.1 sign-off ([GOVERNANCE.md](GOVERNANCE.md)).

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

# Package upstream modules so JPMS Automatic-Module-Name manifests exist for downstream compiles
mvn package -DskipTests -q

# Build & test full Synapse reactor (matches CI — Synapse)
mvn -B clean verify -Psynapse --no-transfer-progress -Dsurefire.useSystemClassLoader=false

# Run unit tests on spector-memory core
mvn test -pl memory/spector-memory

# Check license header compliance across all modules
mvn license:check

# Automatically format missing or misaligned license headers
mvn license:format

# Build AND unit-test the Angular UI (CI only runs ng build, so run ng test locally)
cd cortex/spector-cortex && npm run build && npx ng test --watch=false

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
| `memory_dream` | Trigger generative replay rest cycle across memory tiers | `mode`, `namespace` |
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
   - Use standard JDK APIs only (`jdk.incubator.vector`, `java.lang.ScopedValue`).
   - **Kernel Seal Boundary (`KernelSealBoundaryTest`)**: `java.lang.foreign.*` (Panama FFM) is strictly sealed inside `memory/spector-kernel` (and `nucleus/*`). Never import `java.lang.foreign.*` in `memory/spector-memory` or downstream modules; expose safe helper methods on `PartitionBundle` / `RuntimeBundle` instead.

2. **Error Taxonomy & Exception Handling**:
   - All throw sites must use `ErrorCode` and `SpectorException` adhering to the `SPE-XXX-YYY` scheme defined in [ADR-0070](docs/adr/0070-unified-error-taxonomy-and-exception-handling.md).
   - Use parameterized SLF4J templates (`{}`) avoiding expensive string concatenations on hot paths.
   - Never expose raw exception messages (`e.getMessage()`) in HTTP error responses (CodeQL CWE-209); log details server-side and return constant safe client messages.
   - Never leave `Integer.parseInt` / `Long.parseLong` unguarded on untrusted/config input (catch `NumberFormatException` or use `ParseUtils`), and avoid unreachable `instanceof` branches (e.g., `EpisodicMemory` already implements `EngramRegion`).

3. **Concurrency & Threading (Model B & Virtual Threads)**:
   - **NEVER use `synchronized` blocks or methods** anywhere in Java code (pins virtual-thread carrier threads). Always use `ReentrantLock`, `StampedLock`, `Condition`, `Atomic*`, or concurrent collections (`ConcurrentHashMap`, `CopyOnWriteArrayList`, `ConcurrentLinkedQueue`).
   - Subsystem libraries must never spawn raw unmanaged threads. Use `SpectorTaskQueue` with context-propagated `ScopedValue` bindings ([ADR-0076](docs/adr/0076-zero-dependency-pluggable-cache-abstraction.md), [ADR-0077](docs/adr/0077-model-b-asynchronous-task-queue-concurrency.md)).

4. **Off-Heap Memory Safety & Storage Persistence**:
   - All off-heap allocations must use Panama `Arena.ofShared()` or `Arena.ofConfined()` with bounded lifetimes.
   - Slices must be memory-aligned (`SegmentAllocator`, 64-byte cache line alignment).
   - Reuse `namespace.json` (`StoragePaths.FILE_NAMESPACE`) and existing bundle header `stride` for store/namespace metadata; do not introduce redundant JSON sidecars or alter binary mmap layouts without an ADR.
   - For AES-GCM encryption, always generate a fresh random IV via `SECURE_RANDOM.nextBytes(nonce)` immediately before constructing `GCMParameterSpec` (CodeQL `java/static-initialization-vector`).

5. **Config, Security & Contract Guardrails (Enforced by CI Tests)**:
   - **No Duplicate Builder Setters (`DuplicateConfigBanTest`)**: Do not add duplicate scalar setters onto `SpectorMemoryBuilder`; read new config flags from `builder.properties()`.
   - **Spring `@ConfigurationProperties` Records**: Keep a single canonical constructor (with `@ConstructorBinding` where needed); secondary overloaded constructors break Spring Boot property binding.
   - **Content-Free Admin & Observability Plane ([ADR-0083](docs/adr/0083-namespace-isolated-memory-analytics-telemetry.md), [ADR-0087](docs/adr/0087-admin-role-model-content-free-contract-break-glass.md))**: Admin, analytics, observability, and 3D projection DTOs/endpoints (`/api/v1/admin/**`, `/api/v1/observability/**`) must NEVER return memory `text`, `vector`, `tags`, or raw payloads.
   - **Universal Credentials Vault Only**: Legacy `api_keys` is permanently retired. All inbound API keys and outbound secrets belong in `/api/v1/credentials` (`credentials` table). Never bypass `NamespaceResolutionFilter` on `/api/v1/credentials` without preserving `MemoryScope.TENANT_ID` binding.
   - **Admin Mutation Audit Coverage (`AdminAuditControllerTest`)**: Every new mutating `/api/v1/admin/**` route must emit an `audit_event` row and be covered in `AdminAuditControllerTest`.
   - **Flyway Schema Changes (`TenantIdDefectVerificationTest`)**: Avoid composite indexes on `users(tenant_id, ...)` that block H2 `ALTER TABLE users DROP COLUMN tenant_id` schema-defect tests.

6. **Test Isolation (Headless CI Safety)**:
   - Tests creating `SpectorMemory` must use `MemoryPersistenceMode.IN_MEMORY` (or an isolated `@TempDir`) inside `try-with-resources` so they never leak `StoreDirectoryLock` on `.spector/memory`.
   - `@SpringBootTest` suites must activate `@ActiveProfiles("test")` (or set `spector.security.master-key`) so fail-closed production master-key validation does not break context startup.
   - Synapse controller tests exercising memory writes must mock `MemoryAccessObject` / embedders so headless CI never attempts outbound calls to Ollama.

7. **Commit, PR & Planning Workflow**:
   - Conventional Commits: `type(scope): description`.
   - Always sign off with DCO 1.1 (`git commit -s`).
   - Use the machine's default git identity (`sbharatjoshi`) for commits, pushes, and PR creation — never commit or push as `forgespectrayan`, never mutate global git config, and never add `Co-authored-by: Bharat Joshi` when he is already the author.
   - Pull latest `main` before cutting a new branch. Before planning an issue, inspect linked/prerequisite issues and confirm dependency ordering. When creating GitHub issues from a plan artifact, update the plan artifact with the finalized decisions and issue links.
   - Never run unbounded recursive `grep` over `node_modules/` or `target/` directories; exclude build/dependency dirs or use targeted search tools.

8. **Architectural Decisions**:
   - Review [docs/adr/catalog.md](docs/adr/catalog.md) before altering storage layouts, scoring logic, or cognitive pathway relays.
   - Follow the 8-section layout in `docs/adr/0000-template.md` when introducing new architectural proposals.
   - For UI work in `cortex/spector-cortex`, follow `.agents/rules/spector-ui-standards.md`.
