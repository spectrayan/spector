---
title: "Quick Start — 30 Seconds to First Memory"
description: "Get started with Spector in 30 seconds: NPX runner for AI agents, Python/TypeScript SDKs, Docker Compose, or standalone CLI."
---

# 🚀 Quick Start — 30 Seconds to First Memory

> **Store and recall your first AI agent memory in seconds.** Choose your preferred path below.

---

## Path 1: Zero-Install AI Agent MCP (No Setup Required)

If you are connecting Spector to **Claude Desktop**, **Cursor**, **Windsurf**, or **Claude Code**, run the zero-install launcher:

```bash
npx -y @spectrayan/spector mcp
```

This connects directly to your local Spector node if one is running, or automatically boots an in-process memory kernel with embedded ONNX neural embeddings.

---

## Path 2: Python Client SDK

Install the lightweight client SDK:

```bash
pip install spector-client
```

Store and recall memories with authentic cognitive verbs:

```python
from spector_client import SpectorClient, MemoryTier

# Connect to running daemon or local test instance
client = SpectorClient.builder().with_rest("http://localhost:7070").build()

# 1. Remember
record = client.memory.remember(
    text="User is designing a low-latency RAG system with pgvector and Spector",
    tier=MemoryTier.SEMANTIC,
    tags=["rag", "architecture", "database"],
    interest=0.9,
    valence=1,
)
print(f"Memory recorded: {record.id}")

# 2. Recall with associative cognitive scoring
memories = client.memory.recall("database architecture preferences", top_k=3)
for memory in memories:
    print(f"[{memory.id}] score={memory.score:.4f} | {memory.text}")
```

---

## Path 3: Universal TypeScript / Node.js SDK

Install via npm:

```bash
npm install @spectrayan/spector-client
```

Run in Node.js 18+, Bun, or Deno:

```typescript
import { SpectorClient, MemoryTier } from '@spectrayan/spector-client';

const client = SpectorClient.createDefault('http://localhost:7070');

async function main() {
  // Store
  const record = await client.memory.remember({
    text: 'User prefers dark mode, high contrast, and TypeScript examples',
    tier: MemoryTier.SEMANTIC,
    tags: ['preferences', 'ui'],
  });

  // Recall
  const results = await client.memory.recall({
    query: 'user ui preferences',
    topK: 5,
  });

  results.forEach(m => console.log(`[${m.id}] ${m.text}`));
}

main();
```

---

## Path 4: Instant Local Server (Docker Compose)

Start the Spector memory daemon with a single command:

```bash
# Clone the repository
git clone https://github.com/spectrayan/spector.git
cd spector

# Start core daemon (REST + SSE on port :7070)
docker compose up -d

# Check health
curl http://localhost:7070/actuator/health
```

To launch the 3D Neural Galaxy UI (Cortex):
```bash
docker compose --profile ui up -d
# Open http://localhost in your browser
```

---

## Path 5: One-Line CLI Installers

Install the standalone `spector` CLI binary on your machine:

=== "Linux / macOS (POSIX)"
    ```bash
    curl -fsSL https://raw.githubusercontent.com/spectrayan/spector/main/scripts/install.sh | sh
    ```

=== "Windows (PowerShell)"
    ```powershell
    irm https://raw.githubusercontent.com/spectrayan/spector/main/scripts/install.ps1 | iex
    ```

Verify your environment:
```bash
spector doctor
```

---

## Next Steps

- 🔷 [**TypeScript SDK Guide**](../sdk-usage/typescript-sdk.md) — Explore full async APIs and event streaming
- 🐍 [**Python SDK Guide**](../sdk-usage/python-sdk.md) — Learn how to build agentic memory loops
- 🤖 [**MCP Server Configuration**](../sdk-usage/mcp-server.md) — Configure Cursor, Claude, and Windsurf
- 🐳 [**Docker & Compose Guide**](../deployment/docker.md) — Profiles, volumes, and GPU options