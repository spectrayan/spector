# Spector Python Client SDK (`spector-client`)

> **Lightweight, zero-dependency Python client for the Spector Cognitive Memory & Vector Search platform.**

`spector-client` is generated from OpenAPI 3.1 with an ergonomic handwritten facade providing 1-to-1 cognitive verb parity (`remember`, `recall`, `forget`, `inspect`, `reinforce`) and real-time Server-Sent Events (SSE) streaming.

## Highlights

- **Zero Mandatory Dependencies**: Built entirely on the Python standard library (`urllib.request`, `asyncio`, `json`, `dataclasses`).
- **Zero Java Prerequisite for REST**: Connects directly to Spector Synapse over HTTP REST on port `:7070` with no JVM or incubator flags required.
- **Sync & Async (`asyncio`)**: Dual clients (`SpectorClient` and `AsyncSpectorClient`) with identical cognitive signatures.
- **Real-Time Streaming**: Stream live cognitive consolidation events, Hebbian graph co-activations, and recall telemetry over Server-Sent Events (SSE).
- **Multi-Transport Support**: Connect via HTTP REST (default), remote MCP over HTTP/SSE, or local standalone `spector.jar` subprocess.

---

## Installation

```bash
# Install via pip
pip install spector-client

# Or install locally for development
cd sdks/python
pip install -e ".[dev]"
```

---

## Quick Start (Synchronous)

```python
from spector_client import SpectorClient, MemoryTier

# Connect to running Spector Synapse instance (default: http://localhost:7070)
client = SpectorClient.builder() \
    .with_rest(base_url="http://localhost:7070", api_key="optional-api-key") \
    .build()

# 1. Remember — Store with cognitive metadata
client.memory.remember(
    text="User prefers concise answers and dark mode UI",
    tier=MemoryTier.SEMANTIC,
    tags=["preferences", "ui"],
    interest=0.9,
    valence=1,
)

# 2. Recall — Retrieve using multi-tier associative cognitive scoring
results = client.memory.recall("user preferences", top_k=5)
for record in results:
    print(f"[{record.id}] score={record.score:.4f} | {record.text}")

# 3. Stream Events — Real-time Server-Sent Events (SSE)
for event in client.events.stream(topics=["memory", "cortex"]):
    print(f"📡 Real-time event: {event.event} -> {event.data}")
```

---

## Quick Start (Asynchronous `asyncio`)

```python
import asyncio
from spector_client import AsyncSpectorClient, MemoryTier

async def main():
    async with AsyncSpectorClient.builder().with_rest("http://localhost:7070").build() as client:
        # Asynchronously remember
        await client.memory.remember(
            "Working memory context for active task",
            tier=MemoryTier.WORKING,
            tags=["task-42"]
        )

        # Asynchronously recall
        memories = await client.memory.recall("active task context")
        for mem in memories:
            print(mem.text)

        # Asynchronously stream events
        async for event in client.events.stream(topics=["memory"]):
            print(f"Stream: {event.event}")

asyncio.run(main())
```

---

## Multi-Transport Modes

### 1. High-Throughput REST (Default)
```python
client = SpectorClient.builder() \
    .with_rest(base_url="http://localhost:7070", api_key="secret-key") \
    .build()
```

### 2. Standalone Subprocess (`spector.jar`)
```python
from spector_client.transports import StdioTransport

transport = StdioTransport(
    jar_path="/path/to/spector.jar",
    config_path="/path/to/spector.yml",
    java_bin="java"
)
transport.start()
client = SpectorClient(transport=transport)
```

---

## Cognitive Verbs API Reference

| Verb | Signature | Description |
|:---|:---|:---|
| **`remember()`** | `(text, tier, tags, interest, urgency, challenge, valence, arousal)` | Asynchronously store memory with cognitive tier hints. |
| **`store()`** | `(text, tags)` | Synchronous store returning assigned memory ID. |
| **`recall()`** | `(query, top_k, profile, min_salience, tags)` | Retrieve memories via fused cognitive scoring. |
| **`search()`** | `(query, top_k)` | Pure dense vector semantic similarity search. |
| **`get()`** / **`find()`** | `(id)` | Retrieve full memory record by ID. |
| **`forget()`** | `(id, reason)` | Tombstone memory. |
| **`reinforce()`** | `(id, valence)` | Hebbian Long-Term Potentiation (LTP). |
| **`suppress()`** / **`unsuppress()`** | `(id, reason)` | Active recall inhibition / habituation. |
| **`resolve()`** / **`unresolve()`** | `(id)` | Zeigarnik closure. |
| **`status()`** | `()` | Real-time memory tier counts and index health. |
| **`browse()`** | `(tags)` | Fast inverted tag index lookup. |
| **`table()`** | `(page, page_size, tier)` | Paginated database records. |
| **`vector()`** | `(id)` | INT8 quantized embedding vector retrieval. |
| **`consolidate()`** | `()` | Trigger circadian sleep consolidation sweep. |
| **`vacuum()`** | `(tier)` | Trigger vacuum compaction. |

---

## Development & Testing

```bash
cd sdks/python
python -m unittest discover -s tests -t .
# Or if package is installed in editable mode:
# pip install -e ".[dev]" && pytest
```

---

## License

Apache License, Version 2.0 — see [LICENSE](../../LICENSE) for details.
