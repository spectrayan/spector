# 🗺️ Roadmap

Spector is under active development. This page tracks delivered milestones, in-progress work, and the forward engineering roadmap.

> For the high-level strategic overview, see the root [ROADMAP.md](https://github.com/spectrayan/spector/blob/main/ROADMAP.md).

---

## ✅ Milestones Delivered (May–Sep 2026)

Major engineering accomplishments from the last four months, grouped by domain.

---

### Kernel & Off-Heap Memory Engine

| Feature | Description |
|:---|:---|
| **Sealed Kernel Module** (`spector-kernel`) | Carved Panama FFM mmap slab isolation into a dedicated module with 22 off-heap `RegionLayout` definitions — zero-copy layouts for engrams, graphs, WAL, provenance, and strengths. [#793](https://github.com/spectrayan/spector/pull/796) |
| **Pure Math Kernels** (ADR-0033) | Extracted domain-pure computational kernels — Hopfield, BOCPD (Bayesian Change-Point), Predictive Coding, Time2Vec, SDE Solver, Dopaminergic Surprise — into `spector-core` with zero external dependencies. [#809](https://github.com/spectrayan/spector/pull/809), [#810](https://github.com/spectrayan/spector/pull/810) |
| **Dual-Plane Concurrency** (ADR-0026) | Executor injection, thread classification (IO vs Compute plane), Quartz-based routing, and Arena safety for Virtual Thread-safe off-heap access. [#783](https://github.com/spectrayan/spector/pull/784) |
| **Structured Concurrency** (JEP 505) | `ConcurrentTasks` utility centralizing `StructuredTaskScope` usage with `forkJoinAll` (all-or-nothing) and `forkJoinPartial` (deadline-bounded gather). Virtual thread `ExecutorService` fallback via `-Dspector.concurrency.structured=false`. |
| **128-bit Synaptic Tag Gating** | Closed precision gap in Bloom filter tag masks for high-cardinality tag spaces. [#951](https://github.com/spectrayan/spector/pull/951) |
| **Namespace Path Unification** (ADR-0033) | Unified storage directory layout across all memory tiers and regions for consistent multi-namespace isolation. [#821](https://github.com/spectrayan/spector/pull/821) |

---

### Cognitive Architecture

| Feature | Description |
|:---|:---|
| **6 Cognitive Pathways** (ADR-0035, Epic #924) | Full rearchitecture into six named pathways — *Remember*, *Recall*, *Reflect*, *Wander*, *Dream*, *Express* — each implemented as a dedicated relay chain with pathway-specific instrumentation. [#935](https://github.com/spectrayan/spector/pull/935) |
| **4-Layer Cognitive Graph** | Three associative graph structures augmenting vector recall: **Hebbian** (STDP causal edges, off-heap adjacency), **Entity Directory + HyperEntityGraph** (n-ary hyperedges with typed roles), **Temporal Chain** (session-local bidirectional linked list). 357 tests. |
| **HyperEntityGraph Graduation** | Hypergraph promoted to sole entity graph structure, completely replacing the legacy binary `EntityGraph`. Phase 4 binary excision completed. |
| **Hypergraph Vertex Quarantine** (ADR-0082) | Index reconciliation engine with quarantine protocol for orphaned hypergraph vertices during concurrent ingestion. [#948](https://github.com/spectrayan/spector/pull/948) |
| **Index Plane Lifecycle** (ADR-0082) | `IndexPlaneCoordinator` managing SPLADE bundle persistence and Quartz `IndexReconcileJob`. [#945](https://github.com/spectrayan/spector/pull/945) |
| **Cross-Layer Promotion** | Hebbian→Entity automatic promotion during `reflect()` — strong statistical co-activations graduate to explicit entity relations via reverse index. |
| **Entity Graph Decay, Merging & Adjacency LTD** | Levenshtein-based entity merge, multiplicative edge decay, adjacency weight LTD with compaction. Fan-effect attenuation (1/√refCount) for ACT-R spreading activation dilution. |
| **Temporal Chain Pruning** | Configurable retention via `temporalRetentionDays()`, integrated into `ReflectionOrchestrator` consolidation cycle. |
| **Graph-Aware Scoring** | `GraphScoringPolicy` record — 8 tunable parameters for causal boost, Hebbian spread, temporal hop attenuation, and entity hop depth. |
| **ProfileAdaptor Contextual Bandit** | ε-greedy (10% exploration) auto-profile selection via `profile=auto`. Tracks reinforcement rates per (tag-context, CognitiveProfile) pair. Falls back to `BALANCED` until ≥10 signals. 37 tests. |
| **Two-Factor Memory** (Bjork & Bjork, 1992) | Separate retrieval strength R(t) from storage strength S(t). Spacing effect: low-R retrieval produces maximal storage gain ΔS = S_gain × (1 − R(t)). Precomputed 64-entry LUT for S(t)^0.3 (~50× faster than `Math.pow`). |
| **Executive Dysfunction Profile** | Hebbian-first recall bypass — when queries are vague, STDP causal edges and recent context tags drive retrieval instead of vector similarity. Fully wired into `RecallPathway` as `ASSOCIATIVE` scoring mode. |
| **MindSpan Benchmark Validation** | 100% QA accuracy on 20-year longitudinal MindSpan dataset. Dream Pathway validated against MindSpan longitudinal corpus. [#752](https://github.com/spectrayan/spector/pull/752), [#918](https://github.com/spectrayan/spector/pull/918) |

---

### 4-Layer Retrieval Stack

Full SIMD-accelerated retrieval architecture with Reciprocal Rank Fusion (RRF) across all layers:

```
Layer 4: ColBERT v2 Reranker   (token-level late interaction, SIMD MaxSim)
Layer 3: SPLADE / Li-LSR       (learned sparse retrieval, inverted index)
Layer 2: BM25                  (keyword search, AVX-512 SIMD scoring)
Layer 1: Dense Vector           (HNSW semantic similarity, SVASQ-8/SVASQ-4)
─────── RRF Fusion ─────────── (merges all layer signals)
```

| Component | Module | Description |
|:---|:---|:---|
| `BM25Index` | spector-index | Struct-of-Arrays posting lists with `SIMDScoreAccumulator` for AVX-512 term scoring |
| `SpladeIndex` + `MemorySpladeIndex` | spector-index, spector-memory | Neural sparse term expansion via `SparseEncodingProvider` SPI, partition-parallel search |
| `ColBERTReranker` | spector-memory | MaxSim scoring with SIMD-accelerated dot products via `TokenEmbeddingProvider` SPI |
| `TextSearchMode` | spector-memory | 8-mode enum controlling layer activation: `BM25_ONLY`, `SPLADE`, `COLBERT_RERANK`, `FULL_STACK`, etc. |

---

### Compression & Quantization

| Feature | Compression | Recall Impact |
|:---|:---|:---|
| **SVASQ-4** (INT4 Codes) | 6× vs float32 (768-dim) | ~97–99% recall@10 with 3× oversampling rescore |
| **Padding-Aware Storage** | 25% savings (skip zero-padded dimensions) | None for L2 distance |
| **Norm Header Compression** (float32 → float16) | 2 bytes saved per vector | < 0.01% |

---

### Distributed Systems — Cell HA (ADR-0034)

Complete multi-phase distributed clustering epic:

| Phase | Feature | Description |
|:---|:---|:---|
| Phase 1 | **Cell Ownership Ring** | Ketama consistent hashing with monotonic fence tokens |
| Phase 2 | **Redis Routing Cache** | Gateway resilience with Caffeine fallback for Redis-free deployments |
| Phase 3 | **Snapshot Replication** | gRPC mTLS sealed-once invariant replication |
| Phase 4 | **Lease Coordinator Election** | Fence tokens and monitored failover |
| Phase 5 | **Kubernetes Topology** | Role separation and resource isolation |
| Phase 6 | **Disaster Recovery & Compliance Erasure** | Legal hold, namespace erasure, measured RPO |
| Hardening | **Production Wiring** | 60+ findings addressed (G0–G61) across replication, routing, failover, control plane, and DR |
| ADR-0081 | **Reactive Cell Ingress Gateway** | Dedicated `spector-gateway` module with streaming reverse proxy and CSRF protection |

---

### Security Hardening

| Feature | Description |
|:---|:---|
| **PII Redaction** | Phileas integration behind Spector facade for automatic PII detection and redaction. [#914](https://github.com/spectrayan/spector/pull/914) |
| **Prompt Injection Detection** | Prevention and audit logging for prompt injection attacks against MCP tools. [#913](https://github.com/spectrayan/spector/pull/913) |
| **Tool Access Policy** | Per-namespace Synapse tool authorization controlling which MCP tools are accessible. [#917](https://github.com/spectrayan/spector/pull/917) |
| **CVE Remediation** | 174 vulnerability findings remediated: 5 Critical, 13 High, 88 Medium severity across Trivy, CodeQL, and npm advisories. |
| **Docker Container Scanning** | Trivy + CodeQL integrated into CI pipeline for continuous vulnerability detection. |

---

### Agent Protocols & Developer Experience

| Feature | Description |
|:---|:---|
| **MCP Server** | 16 cognitive tools via stdio and HTTP transport — `memory_remember`, `memory_recall`, `memory_reinforce`, `memory_consolidate`, `memory_introspect`, `memory_why_not`, and more |
| **MEL — Memory Engine Language** | Diagnostic REPL with Lexer → Parser → sealed AST → Evaluator pipeline. Supports `REMEMBER`, `RECALL`, `CONSOLIDATE`, `FORGET`, `EXPLAIN RECALL`, `INTROSPECT`. Module: `synapse/spector-mel` |
| **OpenClaw Integration** | First-class MCP memory provider plugin for OpenClaw autonomous agents (`plugins/openclaw`) |
| **Python SDK** (`spector-client`) | Dual sync/async HTTP+SSE client on PyPI with zero Java runtime requirements |
| **TypeScript SDK** (`@spectrayan/spector-client`) | Universal ESM/CJS packaging for Node.js, Bun, Deno, and browsers. Full cognitive verb parity + SSE streaming |
| **Java Client SDK** | Pure HTTP/SSE Maven client (`sdks/java/spector-client`) with OpenAPI 3.1 contract generation |
| **NPX Zero-Install Runner** | `npx -y @spectrayan/spector mcp` — auto-detects local Synapse or boots release JAR |
| **One-Line Installers** | POSIX shell (`install.sh`), PowerShell (`install.ps1`), Homebrew tap, Scoop manifest |
| **Docker, Helm & Terraform** | Multi-arch GHCR image, OCI Helm chart, and production Terraform modules for AWS ECS, GCP Cloud Run, Azure Container Apps |
| **Streaming Agentic Chat** (ADR-0084) | Dual-plane persistence with SSE streaming endpoint (< 500ms TTFT), Flyway V9 migration, Playwright visual regression suite |

---

### Frontend — Cortex (Angular 22)

| Feature | Description |
|:---|:---|
| **Streaming Chat UI** (ADR-0084) | Token-by-token SSE rendering, tool call cards, thought disclosure panels |
| **Dynamic Configuration Hub** (ADR-0085) | JSON schema reflection, auto-save debounce, hot reload for runtime settings |
| **Component Decomposition** | Feature-based modular architecture: Dashboard (12+ cognitive cards), Query Playground, Memories Table, Graph Explorer, Settings, Health, Control Center |
| **3D Graph Explorer** | Three.js force-directed neural graph with glowing nodes, edge types, time-travel scrubbing, and fly-to inspection |
| **Namespace-Isolated Telemetry** (ADR-0083) | Per-namespace Micrometer meters eliminating cross-namespace IDOR on live metrics |
| **Observability** | Prometheus + Grafana setup for production monitoring dashboards |

---

### Documentation & Governance

| Feature | Description |
|:---|:---|
| **85 Architecture Decision Records** | Living ADR framework covering all cognitive pathways, memory layouts, provider SPIs, and platform concerns |
| **Documentation Split** | Separate User Guide (quickstart, SDKs, MCP) and Architecture Guide (internals, off-heap layouts, SIMD) |
| **Public Voice Standard** | Mechanism-over-analogy documentation tone across all docs and README |
| **Kernel Documentation** | 30+ region pages with package-level documentation for kernel and provider modules |

---

## 🔄 In Progress — Q4 2026

| Feature | Category | Status | Notes |
|:---|:---|:---:|:---|
| **MEL Phase 2** — `REHEARSE`, `ASSOCIATE`, `DREAM` statements + `spector mel` CLI subcommand | Language | 🔄 In Progress | Phase 1 (6 statements + REPL) is complete. Phase 2 adds advanced cognitive verbs. |
| **Index Plane Reconciliation GA** (ADR-0082) | Engine | 🔄 Hardening | Finalize SPLADE bundle persistence and Quartz reconciliation jobs |
| **GPU Kernel Dispatch** | Compute | 📜 Planned | Ship CUDA compute kernels for batch cosine similarity. Panama FFM bridge and context management are implemented; kernel code is pending. |
| **Cortex Apache 2.0 Header Fix** | Governance | 📜 Planned | Batch license header replacement for ~47 TypeScript files still carrying legacy BSL 1.1 headers |
| **Automated SBOM** | Supply Chain | 📜 Planned | CycloneDX 1.6 aggregate SBOM generation + OpenSSF Best Practices badge |
| **Maven Central Distribution** | Distribution | 📜 Planned | Migrate artifact deployment from GitHub Packages to Sonatype Central |
| **Observability GA** | Operations | 🔄 Hardening | Prometheus + Grafana dashboards moving to production-ready state |

---

## 📅 Near-Term Roadmap — Q1–Q2 2027

### Q1 2027: Agent Runtimes & Protocol Interoperability

| Feature | Category | Notes |
|:---|:---|:---|
| **Native Goose Extension** | Agent Runtimes | Dedicated Goose toolkit extension for instant context hydration, working memory, and sleep consolidation in Goose sessions |
| **Streamable HTTP MCP Transport** | Agent Runtimes | Upgrade MCP server from stdio/SSE to modern streamable HTTP and WebSocket transports |
| **A2A Memory Sharing Fabric** | Agent Runtimes | Federated engram sharing and selective epistemic boundary filtering between cooperating agents |

### Q2 2027: Hardware Acceleration & Edge

| Feature | Category | Notes |
|:---|:---|:---|
| **Panama Symmetric HAL GA** | Hardware | Finalize zero-overhead off-heap abstraction (`spector-cpu`, `spector-gpu`) using JDK 25 Foreign Function & Memory API |
| **Apple Silicon / ARM64 NEON** | Hardware | Native hardware-intrinsic vector kernels for sub-millisecond 6-phase scoring on M-series and Graviton |
| **Cell HA GA** | Distributed | Production graduation of distributed cell clustering with full replication and failover |

---

## 🔮 Forward Roadmap — Q3 2027+

### Q3 2027: Platform & Frontend Upgrades

| Feature | Category | Target | Notes |
|:---|:---|:---:|:---|
| **JDK 27 Intermediate Upgrade** | Platform | Q3 2027 | Toolchain bump enabling Project Valhalla value class candidates. Branch `epic/802-jdk27-upgrade` with 22 `@ValueCandidate` records already certified. |
| **Angular 23 LTS Upgrade** | Frontend | Q3 2027 | Angular 23 LTS releases June 2027. Upgrade Cortex from Angular 22 to Angular 23 with extended 24-month support window. |

### Sep 2027: JDK 29 LTS

| Feature | Category | Target | Notes |
|:---|:---|:---:|:---|
| **JDK 29 LTS Upgrade** | Platform | Sep 2027 | Next OpenJDK Long-Term Support release. Full Valhalla value classes, finalized Vector API, and next-gen Panama FFM improvements. Hot-path records (`EncodingHeader`, `ScoredRecord`, `HebbianEdge`, `EntityEdge`, `TraversalResult`) migrate to `value class`. |

### Q3–Q4 2027: Advanced Cognitive Science

| Feature | Category | Notes |
|:---|:---|:---|
| **AISME Phase 8** — Closed-Loop Epistemic Learning | Cognitive Science | Active Inference Self-Model Engine updating posterior belief models based on agent action feedback |
| **Modern Hopfield Associative Memory** | Cognitive Science | Log-Sum-ReLU dense associative indexing for instant pattern completion under noisy input |
| **Continuous Self-Dynamics** | Cognitive Science | Homeostatic regulation and automated sleep-consolidation daemon with dreaming and counterfactual replay during agent idle windows |
| **RecallMode.REPLAY** — WAL Time-Travel | Agentic AI | Reconstruct point-in-time memory state from WAL events for debugging and audit trails |

---

## 🔬 Research & Future

Items under active investigation or dependent on external ecosystem maturity.

| Feature | Category | Complexity | Key Dependency |
|:---|:---|:---:|:---|
| **LoRA Adapter Routing** | Agentic AI | High | LoRA weight format spec, SIMD matrix multiply |
| **SVASQ-PQ Hybrid** | Compression | Very High | PQ codebook training, ADC lookup tables |
| **Flat-Mode SVASQ** | Compression | Medium | Flat-shard architecture integration |
| **NPU Acceleration** | Compute | High | Intel OpenVINO / AMD XDNA SDK maturity |
| **WASM Edge Runtime** | Runtime | High | GraalWasm or Chicory maturity |
| **SPLARE** — Sparse Autoencoder Learned Retrieval | Retrieval | High | Sparse autoencoder training pipeline |
| **ColPali** — Vision-Language Late Interaction | Retrieval | Very High | Vision encoder integration (PaliGemma/SigLIP) |
| **Neuromodulatory Gain Control** | Cognitive Science | High | Runtime neuromodulatory state calibration |
| **Dynamic Quantization Stepping** | Compression | High | Online re-quantization without locking |
| **Spectral Sparsification** | Graph Memory | High | Approximate eigenvalue computation (Lanczos) |

---

## Summary Table

### 🔄 Active & Planned

| # | Feature | Category | Effort | Target | Status |
|:---:|:---|:---|:---:|:---:|:---:|
| 1 | MEL Phase 2 (advanced cognitive verbs) | Language | Medium | Q4 2026 | 🔄 In Progress |
| 2 | GPU Kernel Dispatch | Compute | Medium | Q4 2026 | 📜 Infra Ready |
| 3 | Index Plane Reconciliation GA | Engine | Medium | Q4 2026 | 🔄 Hardening |
| 4 | Automated SBOM + OpenSSF | Supply Chain | Low | Q4 2026 | 📜 Planned |
| 5 | Maven Central Distribution | Distribution | Medium | Q4 2026 | 📜 Planned |
| 6 | Native Goose Extension | Agent Runtimes | Medium | Q1 2027 | 📜 Planned |
| 7 | Streamable HTTP MCP Transport | Agent Runtimes | Medium | Q1 2027 | 📜 Planned |
| 8 | A2A Memory Sharing Fabric | Agent Runtimes | High | Q1 2027 | 📜 Planned |
| 9 | Panama Symmetric HAL GA | Hardware | Medium | Q2 2027 | 📜 Planned |
| 10 | Apple Silicon / ARM64 NEON | Hardware | High | Q2 2027 | 📜 Planned |
| 11 | Cell HA GA | Distributed | Medium | Q2 2027 | 🔄 Hardening |
| 12 | JDK 27 Intermediate Upgrade | Platform | Medium | Q3 2027 | 📜 Prepared |
| 13 | Angular 23 LTS Upgrade | Frontend | Medium | Q3 2027 | 📜 Planned |
| 14 | JDK 29 LTS Upgrade | Platform | High | Sep 2027 | 📜 Planned |
| 15 | AISME Phase 8 | Cognitive Science | High | Q3 2027 | 📜 Planned |
| 16 | Modern Hopfield Associative Memory | Cognitive Science | High | Q3 2027 | 📜 Planned |
| 17 | Continuous Self-Dynamics | Cognitive Science | High | Q3+ 2027 | 📜 Planned |
| 18 | RecallMode.REPLAY (WAL Time-Travel) | Agentic AI | High | Q3+ 2027 | 📜 Planned |

### 🔬 Research & Future

| # | Feature | Category | Effort | Status |
|:---:|:---|:---|:---:|:---:|
| 19 | LoRA Adapter Routing | Agentic AI | High | 🔬 Research |
| 20 | SVASQ-PQ Hybrid | Compression | Very High | 🔬 Research |
| 21 | Flat-Mode SVASQ | Compression | Medium | 🔬 Research |
| 22 | NPU Acceleration | Compute | High | 🔬 Exploratory |
| 23 | WASM Edge Runtime | Runtime | High | 🔬 Exploratory |
| 24 | SPLARE (Sparse Autoencoder) | Retrieval | High | 🔬 Research |
| 25 | ColPali (Vision-Language) | Retrieval | Very High | 🔬 Research |
| 26 | Neuromodulatory Gain Control | Cognitive Science | High | 🔬 Research |
| 27 | Dynamic Quantization Stepping | Compression | High | 🔬 Research |
| 28 | Spectral Sparsification | Graph Memory | High | 🔬 Research |

### ✅ Delivered (May–Sep 2026)

| # | Feature | Category |
|:---:|:---|:---|
| 29 | Sealed Kernel Module (spector-kernel) | Kernel |
| 30 | Pure Math Kernels (ADR-0033) | Kernel |
| 31 | Dual-Plane Concurrency (ADR-0026) | Kernel |
| 32 | Structured Concurrency (JEP 505) | Runtime |
| 33 | 128-bit Synaptic Tag Gating | Kernel |
| 34 | 6 Cognitive Pathways (ADR-0035) | Cognitive Architecture |
| 35 | 4-Layer Cognitive Graph | Cognitive Architecture |
| 36 | HyperEntityGraph Graduation | Cognitive Architecture |
| 37 | Index Plane Lifecycle (ADR-0082) | Engine |
| 38 | Cross-Layer Promotion | Cognitive Architecture |
| 39 | ProfileAdaptor Contextual Bandit | Agentic AI |
| 40 | Two-Factor Memory (Bjork & Bjork) | Cognitive Architecture |
| 41 | Executive Dysfunction Profile | Agentic AI |
| 42 | MindSpan Benchmark Validation | Benchmarks |
| 43 | 4-Layer Retrieval Stack (BM25 + SPLADE + ColBERT + Dense) | Retrieval |
| 44 | SVASQ-4 (INT4 Codes) | Compression |
| 45 | Padding-Aware Storage | Compression |
| 46 | Norm Header Compression (f16) | Compression |
| 47 | Cell HA — 6-Phase Distributed Clustering (ADR-0034) | Distributed Systems |
| 48 | Reactive Cell Ingress Gateway (ADR-0081) | Distributed Systems |
| 49 | Security Hardening (PII, Prompt Injection, CVEs) | Security |
| 50 | MCP Server (16 tools, stdio + HTTP) | Agent Protocols |
| 51 | MEL Phase 1 (Memory Engine Language REPL) | Language |
| 52 | OpenClaw Integration | Agent Protocols |
| 53 | Python SDK (spector-client) | Client SDKs |
| 54 | TypeScript SDK (@spectrayan/spector-client) | Client SDKs |
| 55 | Java Client SDK | Client SDKs |
| 56 | NPX Zero-Install Runner | Distribution |
| 57 | One-Line Installers (Brew, Scoop, Shell, PS1) | Distribution |
| 58 | Docker, Helm & Terraform | Distribution |
| 59 | Streaming Agentic Chat (ADR-0084) | Frontend |
| 60 | Cortex Dynamic Configuration Hub (ADR-0085) | Frontend |
| 61 | 3D Graph Explorer (Three.js) | Frontend |
| 62 | Namespace-Isolated Telemetry (ADR-0083) | Observability |
| 63 | 85 Architecture Decision Records | Documentation |
| 64 | Documentation Split (User Guide / Architecture Guide) | Documentation |
| 65 | Public Voice Standard | Documentation |
