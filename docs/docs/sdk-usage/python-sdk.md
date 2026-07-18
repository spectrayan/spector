---
title: Python SDK
description: "Install and use the Spector Python SDK to control cognitive memory and vector search from Python."
---

# 🐍 Python SDK

> **Zero-dependency Python client wrapping Spector's MCP server.**

The Python SDK spawns the Spector JVM as a subprocess and communicates via JSON-RPC 2.0 over stdio. All 21 MCP tools are accessible through a clean Pythonic API.

---

## Installation

```bash
# Git-installable (no PyPI yet)
pip install git+https://github.com/spectrayan/spector.git#subdirectory=sdks/python

# Or install locally for development
cd sdks/python
pip install -e ".[dev]"
```

**Requirements:**

- Python ≥ 3.10
- JDK 25+ (for the Spector MCP server process)
- Built `spector.jar` — run `mvn package -pl spector-dist -am -DskipTests` from the repo root

---

## Quick Start

```python
from spector import SpectorClient

with SpectorClient(
    jar_path="/path/to/spector-dist/target/spector.jar",
    config_path="/path/to/spector.yml",
) as client:
    # Store a memory (ID auto-generated via TSID)
    mem_id = client.memory.remember(
        "User prefers dark mode with high contrast",
        tags=["preferences", "ui"],
    )
    print(f"Stored: {mem_id}")  # e.g., "0HJGQK4N00000"

    # Recall with cognitive scoring
    results = client.memory.recall("user preferences")

    # Full cognitive X-ray
    record = client.memory.inspect(mem_id)

    # Export all memories as JSON
    export = client.memory.export_json()
```

---

## Memory Operations

### remember — Store a Memory

```python
mem_id = client.memory.remember(
    "The deployment uses Kubernetes with StatefulSets",
    type=MemoryType.SEMANTIC,          # WORKING, EPISODIC, SEMANTIC, PROCEDURAL
    source=MemorySource.OBSERVED,      # USER_STATED, OBSERVED, INFERRED, etc.
    tags=["deployment", "kubernetes"],
    interest=0.7,                      # ICNU importance hints
    challenge=0.3,
)
```

When `id` is omitted, Spector auto-generates a 13-character TSID (time-sorted, distributed-safe).

### recall — Cognitive Search

```python
results = client.memory.recall(
    "kubernetes deployment strategy",
    top_k=5,
    tags=["deployment"],
)
```

### inspect — Cognitive X-Ray

```python
# Returns full header + vector + metadata correlation
record = client.memory.inspect("0HJGQK4N00000")
```

### browse — Tag-Based Browsing

```python
# AND semantics — returns memories matching ALL tags
matches = client.memory.browse("deployment", "kubernetes")
```

### export — Bulk JSON Export

```python
json_data = client.memory.export_json()
```

### Lifecycle Operations

```python
client.memory.forget("mem-123")                    # Permanent tombstone
client.memory.suppress("mem-123", reason="noisy")  # Reversible suppression
client.memory.reinforce("mem-123", valence=1)       # Positive feedback
client.memory.resolve("mem-123")                    # Zeigarnik resolution
```

### Analysis Tools

```python
client.memory.introspect("kubernetes")          # Metamemory analysis
client.memory.why_not("mem-123", "deployment")  # Recall diagnostic
client.memory.compute_importance("text...")       # Importance estimation
client.memory.status()                           # Tier counts
```

---

## Search Engine Operations

```python
# Vector similarity search
hits = client.engine.search("SIMD acceleration", top_k=5)

# Hybrid search (keyword + vector with RRF fusion)
hits = client.engine.hybrid_search("Panama API", top_k=10)

# RAG context retrieval
context = client.engine.rag("How does quantization work?")

# Index management
client.engine.ingest("doc-1", "Document content here...")
client.engine.delete("doc-1")
client.engine.status()
```

---

## Advanced Configuration

```python
from spector import SpectorClient

client = SpectorClient(
    jar_path="/path/to/spector.jar",
    config_path="/path/to/spector.yml",
    java_bin="/usr/lib/jvm/java-25/bin/java",   # Custom JDK path
    extra_jvm_args=["-Xmx1g", "-Xms256m"],       # Additional JVM args
)
```

### Raw MCP Access

For tools not covered by the high-level API:

```python
result = client.call_tool("memory_remember", {
    "text": "Raw tool call",
    "tier": "SEMANTIC",
})

tools = client.list_tools()  # List all available tools
```

### Logging

```python
import logging
logging.basicConfig(level=logging.DEBUG)
logging.getLogger("spector").setLevel(logging.DEBUG)
```

---

## Architecture

```
┌──────────────────┐         JSON-RPC 2.0          ┌──────────────────┐
│   Python SDK     │  ─────── stdio ──────────►    │  Spector JVM     │
│                  │                                │  (MCP Server)    │
│  SpectorClient   │  ◄────── stdout ──────────    │                  │
│  ├── memory      │                                │  21 MCP tools    │
│  └── engine      │         stderr → logging       │  Off-heap memory │
└──────────────────┘                                └──────────────────┘
```

The SDK is a thin wrapper — all cognitive scoring, SIMD search, and off-heap management happens in the JVM process. The Python side only serializes/deserializes JSON-RPC messages.

---

## See Also

- :material-server: [**MCP Server Setup**](mcp-server.md) — Configure for Claude, Cursor, and custom clients
- :material-language-java: [**Java SDK**](java-client.md) — Direct library usage (no subprocess)
- :material-leaf: [**Spring AI**](spring-ai.md) — Spring Boot integration
- :material-brain: [**Memory API Reference**](../memory/api-reference.md) — Full Java API docs
