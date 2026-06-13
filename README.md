<p align="center">
  <img src="docs/docs/assets/spector-logo-full.png" alt="Spector" width="600" />
</p>

<p align="center">
  <strong>The Zero-Overhead, Agent-Ready AI Memory Backbone.</strong>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=for-the-badge" alt="License" /></a>
  <a href="https://openjdk.org/"><img src="https://img.shields.io/badge/Java-25-orange.svg?style=for-the-badge" alt="Java" /></a>
  <a href="https://github.com/spectrayan/spector/actions"><img src="https://img.shields.io/github/actions/workflow/status/spectrayan/spector/ci.yml?branch=main&style=for-the-badge" alt="Build" /></a>
  <a href="spector-mcp/"><img src="https://img.shields.io/badge/MCP-Agent_Ready-blueviolet.svg?style=for-the-badge" alt="MCP" /></a>
  <a href="https://spectrayan.github.io/spector/"><img src="https://img.shields.io/badge/Docs-MkDocs-blue?logo=materialformkdocs&style=for-the-badge" alt="Docs" /></a>
  <a href="https://deepwiki.com/spectrayan/spector"><img src="https://img.shields.io/badge/DeepWiki-spectrayan%2Fspector-blue?style=for-the-badge" alt="DeepWiki" /></a>
</p>

---

Legacy search engines bolted vectors onto text databases. **Spector** is designed from the ground up for modern AI — leveraging Java Project Panama to achieve C++ bare-metal SIMD speeds natively, with a built-in MCP server that turns any AI agent into a search-powered reasoning machine.

---

## System Architecture

```mermaid
graph TB
    subgraph Clients["Client Interfaces"]
        claude["🤖 Claude Desktop"]
        cursor["✏️ Cursor / AI IDEs"]
        agents["🦾 Autonomous Agents"]
        sdk["☕ Java SDK"]
        spring["🌱 Spring AI"]
        cli["🖥️ spectorctl CLI"]
        rest["🌐 REST / gRPC"]
    end

    subgraph Transport["Transport Layer"]
        mcp["MCP Server<br/><i>stdio · 13 tools</i>"]
        armeria["Armeria Server<br/><i>REST + gRPC + SSE</i>"]
    end

    subgraph Core["Spector Engine"]
        runtime["SpectorRuntime<br/><i>Composition Root</i>"]

        subgraph Search["Search Pipeline"]
            hybrid["Hybrid Search<br/><i>Vector + Keyword</i>"]
            hnsw["HNSW Index<br/><i>M=16, ef=200</i>"]
            bm25["BM25 Index<br/><i>Inverted Index</i>"]
            rrf["RRF Fusion<br/><i>+ LLM Re-ranking</i>"]
        end

        subgraph Memory["Cognitive Memory"]
            cortex["4-Tier Cortex<br/><i>Working → Episodic → Semantic → Procedural</i>"]
            hebbian["Hebbian Graph<br/><i>Associative links</i>"]
            decay["Memory Decay<br/><i>Power-law forgetting</i>"]
            consolidation["Sleep Consolidation<br/><i>Hippocampal replay</i>"]
        end

        subgraph Ingestion["Ingestion Pipeline"]
            chunking["Document Chunking<br/><i>Sentence · Paragraph · Semantic</i>"]
            embedding["Embedding<br/><i>Ollama · Provider SPI</i>"]
            indexing["Index Writer<br/><i>Batch + streaming</i>"]
        end
    end

    subgraph Platform["Platform Layer"]
        simd["SIMD Kernels<br/><i>AVX2 / AVX-512 / NEON</i>"]
        panama["Panama Storage<br/><i>Off-heap MemorySegment</i>"]
        quant["SVASQ Quantization<br/><i>INT8 · INT4 · IVF-PQ</i>"]
        gpu["GPU Acceleration<br/><i>CUDA via Panama FFM</i>"]
    end

    subgraph Observe["Observability"]
        events["TelemetryBus<br/><i>12 event types</i>"]
        metrics["Micrometer<br/><i>Prometheus export</i>"]
        sse["SSE Stream<br/><i>Real-time events</i>"]
    end

    claude & cursor & agents --> mcp
    sdk & spring & cli & rest --> armeria
    mcp & armeria --> runtime

    runtime --> Search
    runtime --> Memory
    runtime --> Ingestion

    Search --> simd & panama & quant
    Memory --> simd & panama
    Ingestion --> embedding

    runtime --> events
    events --> metrics & sse

    gpu -.->|optional| simd

    style Clients fill:#1a1a2e,stroke:#e94560,color:#fff
    style Transport fill:#16213e,stroke:#0f3460,color:#fff
    style Core fill:#0f3460,stroke:#533483,color:#fff
    style Platform fill:#533483,stroke:#e94560,color:#fff
    style Observe fill:#1a1a2e,stroke:#533483,color:#fff
    style Search fill:#16213e,stroke:#0f3460,color:#fff
    style Memory fill:#16213e,stroke:#0f3460,color:#fff
    style Ingestion fill:#16213e,stroke:#0f3460,color:#fff
```

### Data Flow

```mermaid
graph LR
    subgraph Ingest["Ingest"]
        docs["📄 Documents"]
        files["📁 Files"]
        api["🌐 API Data"]
    end

    subgraph Process["Process"]
        chunk["✂️ Chunk"]
        embed["🧬 Embed"]
        quantize["🗜️ Quantize"]
    end

    subgraph Store["Store"]
        vectors["📊 Vector Index<br/><i>HNSW · IVF-PQ</i>"]
        text["📝 Text Index<br/><i>BM25</i>"]
        memory["🧠 Cognitive Store<br/><i>4-tier cortex</i>"]
    end

    subgraph Query["Query"]
        search["🔍 Hybrid Search"]
        recall["💭 Memory Recall"]
        rag["🤖 RAG Pipeline"]
    end

    docs & files & api --> chunk --> embed --> quantize
    quantize --> vectors & text & memory
    vectors & text --> search --> rag
    memory --> recall --> rag

    style Ingest fill:#1a1a2e,stroke:#e94560,color:#fff
    style Process fill:#16213e,stroke:#0f3460,color:#fff
    style Store fill:#0f3460,stroke:#533483,color:#fff
    style Query fill:#533483,stroke:#e94560,color:#fff
```

### Deployment Topology

```mermaid
graph TB
    subgraph Standalone["Standalone Mode"]
        jar["java -jar spector.jar<br/><i>Embedded engine + server</i>"]
    end

    subgraph Embedded["Embedded Mode"]
        lib["SpectorEngine API<br/><i>In-process, zero-network</i>"]
    end

    subgraph Distributed["Distributed Mode"]
        coord["Coordinator Node<br/><i>Query routing · fan-out</i>"]
        shard1["Shard 1<br/><i>Partition A</i>"]
        shard2["Shard 2<br/><i>Partition B</i>"]
        shard3["Shard N<br/><i>Partition C</i>"]
        coord --> shard1 & shard2 & shard3
    end

    style Standalone fill:#0f3460,stroke:#533483,color:#fff
    style Embedded fill:#16213e,stroke:#0f3460,color:#fff
    style Distributed fill:#1a1a2e,stroke:#e94560,color:#fff
```

---

## 🧠 Cognitive Memory — AI Agents That Actually Remember

Spector Memory is a **biologically-inspired cognitive memory engine** that gives AI agents the ability to **remember**, **forget**, **consolidate**, and **associate** — with microsecond latency and zero garbage collection pressure.

| Capability | What it does |
|:---|:---|
| 🧠 **4-Tier Cortex** | Working → Episodic → Semantic → Procedural memory |
| ⚡ **0.13ms recall** at 1M memories | 15× faster than the 2ms target (vs. 50–200ms for Mem0/Letta/Zep) |
| 🔗 **Fused SIMD Scoring** | Similarity × importance × decay in a single pass — no truncation trap |
| 🛏️ **Sleep Consolidation** | Hippocampus-inspired pruning and partition rebuild |
| 😱 **Emotional Valence** | Amygdala-driven positive/negative/neutral tagging |
| 🚫 **Zero GC** | 100% off-heap Panama storage (≤0.01% overhead measured) |

> 📖 **[Full Cognitive Memory Documentation →](https://spectrayan.github.io/spector/memory/)**

---

## ✨ Key Capabilities

| Capability | Technology | Performance |
|:---|:---|:---|
| 🤖 **Agent-Native (MCP)** | Model Context Protocol · 13 tools · stdio | Claude · Cursor · autonomous agents |
| ⚡ **SIMD Search** | Java Vector API (AVX2/AVX-512/NEON) | 88µs p50 · 61K QPS |
| 🧊 **Off-Heap Storage** | Panama MemorySegment · zero-copy I/O | 0.01% GC overhead |
| 🗜️ **Quantization** | SVASQ-8/4 · IVF-PQ · FWHT rotation | 4–32× compression · 99.5% recall |
| 🔍 **Hybrid Search** | HNSW + BM25 + RRF + LLM re-ranking | Sub-ms latency |
| 🖥️ **GPU Acceleration** | CUDA via Panama FFM | Optional · zero-copy transfer |
| 📦 **Flexible Deployment** | Embedded JAR · Standalone · Distributed | Zero to cluster in one config |

---

## 📸 Demo

<p align="center">
  <a href="docs/screenshots/spector-cortex-neural-graph.mp4">
    <img src="docs/screenshots/spector-cortex-graph.png" alt="Spector Cortex — Neural Graph Explorer" width="800" />
  </a>
  <br />
  <sub>🎥 <a href="docs/screenshots/spector-cortex-neural-graph.mp4">Watch the Neural Graph in action →</a></sub>
</p>

<details open>
<summary><b>📊 Dashboard — 12+ live cognitive panels</b></summary>
<p align="center">
  <img src="docs/screenshots/spector-cortex-dashboard.png" alt="Spector Cortex Dashboard" width="800" />
</p>
Real-time scoring pipeline, SIMD lanes, decay curves, vector space, Hebbian graph, cognitive profiles, live metrics — all rendered with Three.js, Canvas 2D, and Angular Signals.
</details>

<details>
<summary><b>🌌 Graph Explorer — 3D neural galaxy</b></summary>
<p align="center">
  <img src="docs/screenshots/spector-cortex-graph.png" alt="Spector Cortex Graph Explorer" width="800" />
</p>
Interactive 3D graph with glowing star nodes, Hebbian/temporal/entity edges, fly-to navigation, and real-time topology stats.
</details>

<details>
<summary><b>🧠 Memory Table — browse & manage memories</b></summary>
<p align="center">
  <img src="docs/screenshots/spector-cortex-memory-table.png" alt="Spector Cortex Memory Table" width="800" />
</p>
Full CRUD with tier filtering, importance bars, valence indicators, synaptic tags, recall counts, and bulk actions.
</details>

<details>
<summary><b>🔬 Memory Detail — deep cognitive inspection</b></summary>
<p align="center">
  <img src="docs/screenshots/spector-memory-detail.png" alt="Spector Memory Detail" width="800" />
</p>
Identity, cognitive state (importance/valence/arousal), synaptic tags, and full relationship graph (Hebbian associations, temporal chains, entity links).
</details>

---

## 🚀 Quick Start

**Prerequisites:** JDK 25+, Maven 3.9+

```bash
git clone https://github.com/spectrayan/spector.git
cd spector
mvn clean test                                        # Build & run all 685+ tests
mvn package -pl spector-dist -am -DskipTests          # Build the distribution JAR
```

**Start the MCP server** (for AI agents):

```bash
java --add-modules jdk.incubator.vector \
  --enable-native-access=ALL-UNNAMED --enable-preview \
  -jar spector-dist/target/spector.jar \
  --config spector.yml
```

**Claude Desktop config** — add to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "spector": {
      "command": "java",
      "args": [
        "--add-modules", "jdk.incubator.vector",
        "--enable-native-access=ALL-UNNAMED",
        "--enable-preview",
        "-jar", "/path/to/spector-dist/target/spector.jar",
        "--config", "/path/to/spector.yml"
      ]
    }
  }
}
```

> 📖 **[Full Quick Start Guide →](https://spectrayan.github.io/spector/getting-started/quickstart/)** · **[Configuration Reference →](https://spectrayan.github.io/spector/configuration/parameters/)**

---

## 📊 Benchmarks

All numbers measured on Intel Core Ultra 9 285K, Java 25, AVX2 256-bit.

| Benchmark | Result | Notes |
|:---|:---|:---|
| Vector search p50 | **88–143µs** | 10K–100K docs, HNSW M=16 |
| Cognitive recall at 1M | **0.13ms p50** | 15× better than 2ms target |
| Peak QPS (16 threads) | **61,011** | Concurrent vectorSearch |
| GC overhead | **0.01%** | 1 pause / 100K searches |
| vs. Python MCP servers | **23–113× faster** | In-process SIMD, zero network |

> 📖 **[Full Benchmark Report →](https://spectrayan.github.io/spector/deep-dives/real-embedding-benchmarks/)** · **[Performance Tuning →](https://spectrayan.github.io/spector/operations/performance-tuning/)**

---

## 📖 Documentation

| I want to... | Start here |
|:---|:---|
| **Use Spector** | [Quick Start](https://spectrayan.github.io/spector/getting-started/quickstart/) · [Installation](https://spectrayan.github.io/spector/getting-started/installation/) · [Configuration](https://spectrayan.github.io/spector/configuration/parameters/) |
| **Connect an AI agent** | [MCP Server Guide](https://spectrayan.github.io/spector/sdk-usage/mcp-server/) · [Claude Desktop Config](#claude-desktop-config) |
| **Add cognitive memory** | [Memory Overview](https://spectrayan.github.io/spector/memory/) · [Getting Started](https://spectrayan.github.io/spector/memory/getting-started/) · [Use Cases](https://spectrayan.github.io/spector/memory/use-cases/) |
| **Use the Java SDK** | [Java SDK Guide](https://spectrayan.github.io/spector/sdk-usage/java-client/) · [Spring AI Integration](https://spectrayan.github.io/spector/sdk-usage/spring-ai/) |
| **Deploy to production** | [Docker Deployment](deploy/docker/) · [Performance Tuning](https://spectrayan.github.io/spector/operations/performance-tuning/) |
| **Extend with Enterprise** | [Spector Enterprise](https://github.com/spectrayan/spector-enterprise) — LLM providers, Cortex dashboard, data connectors |

> 📖 **[Full Documentation →](https://spectrayan.github.io/spector/)**

---

## 🤝 Contributing

We welcome contributions of all kinds — code, docs, tests, benchmarks, and ideas!

- 🐛 **Found a bug?** → [Open an Issue](https://github.com/spectrayan/spector/issues/new?template=bug_report.md)
- 💡 **Have an idea?** → [Start a Discussion](https://github.com/spectrayan/spector/discussions)
- 🔧 **Want to contribute code?** → See [CONTRIBUTING.md](CONTRIBUTING.md)
- 🤖 **AI-assisted PRs welcome!**

---

## ⭐ Star History

[![Star History Chart](https://api.star-history.com/svg?repos=spectrayan/spector&type=Date)](https://star-history.com/#spectrayan/spector&Date)

---

## 📄 License

This repository uses a **split licensing model**:

- **`spector-memory`** — [Business Source License 1.1](spector-memory/LICENSE) (transitions to Apache 2.0 on May 27, 2030)
- **All other modules** — [Apache License 2.0](LICENSE)

For branding and trademark guidelines, see the [NOTICE](NOTICE) file.

## 🔒 Security

See [SECURITY.md](SECURITY.md) for our security policy and vulnerability reporting.

## 🙏 Acknowledgments

See [ACKNOWLEDGMENTS.md](ACKNOWLEDGMENTS.md) for credits to the cognitive science researchers, open-source frameworks, and AI coding tools that made Spector possible.

---

<p align="center"><strong>Built with ⚡ by <a href="https://www.spectrayan.com/">Spectrayan</a></strong></p>
