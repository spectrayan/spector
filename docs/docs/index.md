---
title: "Spector — Zero-Overhead AI Memory & Cognitive Graph"
description: "Spector is a cognitive memory backbone for AI agents — biologically-inspired memory tiers and associative graphs with fused semantic and hybrid retrieval, a built-in MCP server, and sub-millisecond recall."
---

# ⚡ Spector — The AI Memory Backbone

> **Agent-ready cognitive memory that forms associations — sub-millisecond recall, zero infrastructure.**

Spector gives AI agents real memory: it **remembers, forgets, consolidates, and forms associations** across working, episodic, semantic, and procedural tiers, linked by Hebbian, temporal, and entity graphs. Retrieval fuses dense semantic search with hybrid signals and cognitive scoring for sub-millisecond recall.

Connect your agents through the **built-in MCP server** (Claude Desktop, Cursor, custom agents), call it over **REST/gRPC**, use the **Python SDK**, or embed it as a single JAR — no external database, no infrastructure to run. Every user, agent, or tenant is physically isolated in its own on-disk namespace. Java Project Panama keeps it all off-heap with zero GC pressure.

---

## 🔥 Key Numbers

| Metric | Value |
|:-------|:------|
| 🧠 Cognitive Recall | **0.13ms** p50 at 1M memories |
| ⚡ Similarity Scoring | **88µs** p50 (10K docs, 128-dim) |
| 🚀 Peak QPS | **61,011** concurrent recalls |
| 🤖 MCP Tools | **16 tools** (stdio + HTTP Model Context Protocol) |
| 🗜️ Compression | **4×–32×** (SVASQ-8 to IVF-PQ) |
| ✅ Test Suite | **685+ tests**, all passing |
| 📦 Dependencies | **Zero** (JDK only) |

---

## 🗺️ Choose Your Path

=== "🚀 I want to use Spector"

    | Page | What you'll learn |
    |:-----|:------------------|
    | [Quick Start](getting-started/quickstart.md) | Build, run, and search in 5 minutes |
    | [MCP Server Guide](sdk-usage/mcp-server.md) | Connect Claude Desktop, Cursor, or custom agents |
    | [Installation](getting-started/installation.md) | Prerequisites and setup options |
    | [Configuration](configuration/parameters.md) | All parameters with tuning advice |
    | [REST API Reference](api-reference/rest-endpoints.md) | All endpoints with curl examples |
    | [Cognitive Memory](memory/index.md) | Getting started with AI agent memory |
    | [Cortex Dashboard](cortex/index.md) | Real-time neural visualization dashboard |

=== "🧠 I want to understand how it works"

    | Page | What you'll learn |
    |:-----|:------------------|
    | [Architecture Overview](architecture/overview.md) | Module diagram, data flow, threading model |
    | [Core Concepts](architecture/core-concepts.md) | HNSW, IVF-PQ, BM25, RRF, SIMD deep-dives |
    | [Memory Architecture](memory/architecture.md) | How cognitive memory works under the hood |
    | [6-Phase Scoring Pipeline](memory/scoring-pipeline.md) | Fused SIMD scoring across memory tiers |
    | [Cortex Dashboard](cortex/index.md) | Watch your AI's brain think — 12+ live panels |
    | [SVASQ Quantization](deep-dives/svasq-deep-dive.md) | Our proprietary SIMD-first quantization engine |
    | [Benchmarks](deep-dives/real-embedding-benchmarks.md) | Empirical sweeps on 4096-dim embeddings |

=== "🤝 I want to contribute"

    | Page | What you'll learn |
    |:-----|:------------------|
    | [Contributing Guide](operations/contributing.md) | Development setup and PR process |
    | [JDK API Status](getting-started/jdk-api-status.md) | Vector API, Panama FFM compatibility |
    | [Roadmap](roadmap.md) | What's planned next |
    | [FAQ](faq.md) | Common questions answered |

---

## 💡 How It Works

Spector fuses **semantic vector search, hybrid retrieval, and cognitive scoring** into a single pipeline:

```mermaid
graph LR
    A["🤖 AI Agent"] --> B["📡 MCP Server"]
    B --> C["⚡ SpectorEngine"]
    C --> D["🧠 Hybrid Search"]
    D --> E["🎯 RRF Fusion"]
    E --> F["🤖 LLM Re-ranking"]
    F --> G["✨ Results"]

    H["📄 Document"] --> I["🧩 Chunking"]
    I --> J["🧬 Embedding"]
    J --> C
```

### What Makes Spector Different

- **Flexible deployment** — connect over MCP or REST/gRPC, drive it from the Python SDK, or embed it as a library inside your JVM. No Docker, no external database, no network hops when embedded.
- **Agent-native** — 16 MCP tools for memory, recall, and cognitive operations. Connect Claude Desktop or Cursor in one config line.
- **Associative memory** — Hebbian co-activation, temporal chains, and entity graphs with spreading activation, so recall surfaces what's *related*, not just what matches.
- **Cognitive memory** — the only system combining power-law decay, Two-Factor strengthening (Bjork & Bjork), emotional valence, and Hebbian association in a single scoring formula.
- **Zero GC pressure** — all vector data and headers live off-heap via Project Panama. The JVM garbage collector never sees memory records.
- **SIMD everywhere** — vector distance, quantization, and scoring use Java Vector API (AVX2/AVX-512/NEON) for hardware-accelerated computation.

!!! tip "New here?"
    Start with [Quick Start](getting-started/quickstart.md) to build and run your first search in under 5 minutes. Want to connect an AI agent? See the [MCP Server Guide](sdk-usage/mcp-server.md).

---

## 🌟 Project Stats

| | |
|:---|:---|
| **Language** | Java 25 |
| **License** | Apache 2.0 · [BSL 1.1](https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE) (memory module) |
| **Modules** | 25 Maven modules |
| **Dependencies** | Zero (JDK only) |
| **SIMD** | AVX2 / AVX-512 / NEON |
| **GPU** | CUDA via Panama FFM |
| **MCP** | Built-in, 16 agent-ready tools |
| **Distributed** | gRPC fan-out + consistent hashing |

---

**Built with ⚡ by [Spectrayan](https://www.spectrayan.com/)** · [GitHub](https://github.com/spectrayan/spector) · [Apache 2.0](https://github.com/spectrayan/spector/blob/main/LICENSE) · [BSL 1.1 (memory)](https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE)