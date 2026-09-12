---
title: Python SDK
description: "Install and use the Spector Python SDK (spector-client) for cognitive memory operations and real-time SSE streaming."
---

# 🐍 Python SDK (`spector-client`)

> **Zero-dependency, high-throughput Python client for the Spector Cognitive Memory & Vector Search platform.**

`spector-client` connects Python AI applications and agent frameworks (OpenClaw, LangChain, AutoGen, CrewAI) to Spector over HTTP REST and Server-Sent Events (SSE). It requires **zero Java on the client machine** while preserving full cognitive verb parity with the underlying memory engine.

---

## Highlights

- **Zero Mandatory Dependencies**: Built entirely on the Python standard library (`urllib.request`, `asyncio`, `json`, `dataclasses`).
- **Zero Java Prerequisite for REST**: Connects directly to Spector Synapse over HTTP REST on port `:7070` with no JVM or incubator flags required.
- **Sync & Async (`asyncio`)**: Dual clients (`SpectorClient` and `AsyncSpectorClient`) with identical cognitive signatures.
- **Real-Time Streaming**: Stream live cognitive consolidation events, Hebbian graph co-activations, and recall telemetry over Server-Sent Events (SSE).
- **Multi-Transport Support**: Connect via HTTP REST (default), remote MCP over HTTP/SSE, or local standalone `spector.jar` subprocess.

---

## Installation

```bash title="Terminal"
# Install from PyPI
pip install spector-client

# Or install locally for development
cd sdks/python
pip install -e ".[dev]"
```

**Requirements:** Python ≥ 3.10.

---

## Quick Start (Synchronous)

```python title="agent_sync.py" hl_lines="9-16 20"
from spector_client import SpectorClient, MemoryTier

# Connect to running Spector Synapse instance (default: http://localhost:7070)
client = SpectorClient.builder() \
    .with_rest(base_url="http://localhost:7070", api_key="optional-api-key") \
    .build()

# 1. Remember — Store with cognitive metadata
record = client.memory.remember(
    text="User prefers concise answers and dark mode UI",
    tier=MemoryTier.SEMANTIC,
    tags=["preferences", "ui"],
    interest=0.9,
    valence=1,
)
print(f"Stored memory: {record.get('id', 'stored')}")

# 2. Recall — Retrieve using multi-tier associative cognitive scoring
results = client.memory.recall("user preferences", top_k=5)
for item in results:
    print(f"[{item.id}] score={item.score:.4f} | {item.text}")

# 3. Real-time Server-Sent Events (SSE)
for event in client.events.stream(topics=["memory", "consolidation"]):
    print(f"📡 Event: {event.event} -> {event.data}")
```

---

## Quick Start (Asynchronous `asyncio`)

For modern async agent loops, use `AsyncSpectorClient`:

```python title="agent_async.py" hl_lines="6-10 13"
import asyncio
from spector_client import AsyncSpectorClient, MemoryTier

async def main():
    async with AsyncSpectorClient.builder().with_rest("http://localhost:7070").build() as client:
        # Asynchronously remember
        record = await client.memory.remember(
            text="Active task state: calculating cognitive graph embeddings",
            tier=MemoryTier.WORKING,
            tags=["agent-loop", "task-102"],
        )

        # Asynchronously recall
        memories = await client.memory.recall("active task state")
        for mem in memories:
            print(f"Recalled: {mem.text}")

        # Stream real-time events asynchronously
        async for event in client.events.stream(topics=["memory"]):
            print(f"Received event: {event.event}")

asyncio.run(main())
```

---

## Cognitive Memory Operations

The `client.memory` facade exposes cognitive memory operations:

### `remember` — Store a Memory
Store across the 4 cognitive tiers (`WORKING`, `EPISODIC`, `SEMANTIC`, `PROCEDURAL`):

```python
record = client.memory.remember(
    text="Production cluster runs Kubernetes 1.31 with Cilium CNI",
    tier=MemoryTier.SEMANTIC,
    tags=["infrastructure", "kubernetes"],
    interest=0.85,    # ICNU interest (0.0 to 1.0)
    challenge=0.2,    # ICNU novelty/difficulty factor
    urgency=0.1,      # ICNU time-sensitivity
    valence=0,        # Emotional valence (-128 to 127)
    arousal=50,       # Emotional arousal (0 to 255)
)
```

### `recall` — Fused Cognitive Retrieval
Queries memories with fused similarity × importance × temporal decay:

```python
memories = client.memory.recall(
    query="cluster networking CNI",
    top_k=5,
    tags=["infrastructure"],
    min_salience=0.25,
    profile="BALANCED",  # Or HYPERFOCUS, THE_EXECUTOR, DIVERGENT, DEBUGGING
)
```

### Lifecycle Operations

```python
memory_id = record.get("id", "mem-101")

# Apply Long-Term Potentiation (Hebbian reinforcement)
client.memory.reinforce(memory_id, valence=1)

# Close an active task loop (Zeigarnik closure)
client.memory.resolve(memory_id)

# Inhibit recall without deleting
client.memory.suppress(memory_id, reason="Deprecated configuration")

# Permanently tombstone and prune memory graph edges
client.memory.forget(memory_id)

# Tag index browsing
matches = client.memory.browse(tags=["infrastructure", "kubernetes"])

# Node health and tier statistics
stats = client.memory.status()
print(f"Total memories: {stats.total_memories}")
```

---

## Multi-Transport & Authentication

### 1. High-Throughput HTTP REST (Default)
Connects to Spector Synapse over standard HTTP with optional API key and isolated tenant namespace:
```python
client = SpectorClient.builder() \
    .with_rest(base_url="http://localhost:7070") \
    .with_api_key("secret-key") \
    .with_namespace("agent-alpha") \
    .build()
```

### 2. Custom Transport Adapter (MCP HTTP / Stdio)
Pass custom transport implementations directly:
```python
from spector_client.transports.mcp_http import McpHttpTransport

client = SpectorClient.builder() \
    .with_transport(McpHttpTransport(url="http://localhost:7070/mcp")) \
    .build()
```

---

## Error Handling

All SDK exceptions derive from `SpectorClientError`:

```python
from spector_client.exceptions import (
    SpectorClientError,
    TransportError,
    MemoryNotFoundError,
    SpectorAuthError,
    SpectorServerError,
)

try:
    client.memory.recall("query")
except TransportError as err:
    print(f"Failed to connect to Spector daemon: {err.message}")
except MemoryNotFoundError as err:
    print(f"Memory not found: {err.memory_id}")
except SpectorClientError as err:
    print(f"Spector error [{err.status_code}]: {err.message}")
```

---

## See Also

- :material-language-typescript: [**TypeScript SDK**](typescript-sdk.md) — Universal TS/JS client for Node.js, Bun, Deno, and Browser
- :material-server: [**MCP Server Setup**](mcp-server.md) — Connect AI agents to Spector via MCP
- :material-language-java: [**Java SDK**](java-client.md) — Native JVM client and Spring AI integration
- :material-docker: [**Docker Deployment**](../deployment/docker.md) — Run Spector Synapse locally with Docker Compose
