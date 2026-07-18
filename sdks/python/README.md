# Spector Python SDK

> **Zero-dependency Python client for the Spector AI Memory & Search platform.**

The SDK wraps Spector's MCP server via JSON-RPC 2.0 over stdio, providing a Pythonic API for cognitive memory and vector search operations.

## Installation

```bash
# From the repo (git-installable)
pip install git+https://github.com/spectrayan/spector.git#subdirectory=sdks/python

# Or install locally for development
cd sdks/python
pip install -e ".[dev]"
```

## Requirements

- **Python ≥ 3.10**
- **JDK 25+** (for the Spector MCP server process)
- Built `spector.jar` — run `mvn package -pl spector-dist -am -DskipTests` from the repo root

## Quick Start

```python
from spector import SpectorClient

with SpectorClient(
    jar_path="/path/to/spector-dist/target/spector.jar",
    config_path="/path/to/spector.yml",
) as client:
    # ── Memory Operations ──
    
    # Store a memory (ID auto-generated)
    mem_id = client.memory.remember(
        "User prefers dark mode with high contrast",
        tags=["preferences", "ui"],
    )
    print(f"Stored: {mem_id}")

    # Recall with cognitive scoring
    results = client.memory.recall("user preferences")
    print(results)

    # Inspect — full cognitive X-ray
    record = client.memory.inspect(mem_id)
    print(record)

    # Browse by tags (no vector search)
    matches = client.memory.browse("preferences")
    print(matches)

    # Export all memories as JSON
    export = client.memory.export_json()
    print(export)

    # ── Search Operations ──
    
    hits = client.engine.search("SIMD acceleration", top_k=5)
    print(hits)

    context = client.engine.rag("How does quantization work?")
    print(context)
```

## API Reference

### SpectorClient

| Method | Description |
|--------|-------------|
| `SpectorClient(jar_path, config_path, java_bin, extra_jvm_args)` | Constructor |
| `start()` / `stop()` | Lifecycle management |
| `with SpectorClient(...) as client:` | Context manager |
| `client.memory` | Access `MemoryClient` |
| `client.engine` | Access `EngineClient` |
| `client.call_tool(name, args)` | Raw MCP tool call |
| `client.list_tools()` | List available tools |

### MemoryClient (`client.memory`)

| Method | Description |
|--------|-------------|
| `remember(text, type, source, tags, id)` | Store a cognitive memory |
| `recall(query, top_k, tags)` | Recall with cognitive scoring |
| `inspect(id)` | Full cognitive X-ray |
| `browse(*tags)` | Tag-based browsing |
| `export_json()` | Bulk JSON export |
| `forget(id)` | Tombstone a memory |
| `reinforce(id, valence)` | LTP reinforcement |
| `suppress(id, reason)` | Suppress from recall |
| `resolve(id)` | Zeigarnik resolution |
| `introspect(topic)` | Metamemory analysis |
| `why_not(id, query)` | Recall diagnostic |
| `compute_importance(text)` | Importance estimation |
| `status()` | Tier counts |
| `scratchpad(text)` | Working memory |
| `reminder(text, delay_seconds)` | Prospective scheduling |

### EngineClient (`client.engine`)

| Method | Description |
|--------|-------------|
| `search(query, top_k)` | Vector similarity search |
| `hybrid_search(query, top_k, keyword_weight)` | Hybrid search with RRF |
| `rag(query, top_k)` | RAG context retrieval |
| `ingest(id, text, metadata)` | Add document to index |
| `delete(id)` | Remove document |
| `status()` | Engine stats |

## Configuration

The SDK spawns the Spector JAR with the correct JVM flags automatically. You can customize:

```python
client = SpectorClient(
    jar_path="/path/to/spector.jar",
    config_path="/path/to/spector.yml",
    java_bin="/usr/lib/jvm/java-25/bin/java",   # Custom JDK path
    extra_jvm_args=["-Xmx1g", "-Xms256m"],       # Additional JVM args
)
```

## Development

```bash
cd sdks/python
pip install -e ".[dev]"
python -m pytest tests/ -v
```

## License

Apache 2.0 — see [LICENSE](../../LICENSE) for details.
