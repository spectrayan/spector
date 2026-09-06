# 🖥️ CLI Reference

> **Manage Spector from the command line.** `spectorctl` connects to a running server via REST and provides commands for indexing, ingestion, search, and status monitoring — with both human-friendly tables and machine-parseable JSON output.

---

## 📦 Installation

Build from source:

```bash
cd spector
mvn clean package -pl spector-cli -am -DskipTests
```

The CLI JAR is at `spector-cli/target/spector-cli.jar`. Run it with:

```bash
java -jar spector-cli/target/spector-cli.jar [command] [options]
```

> [!TIP]
> Create an alias for convenience:
> ```bash
> alias spectorctl='java -jar /path/to/spector-cli.jar'
> ```

---

## 🌐 Global Options

| Option | Default | Description |
|--------|---------|-------------|
| `--host` | localhost | Spector server hostname |
| `--port` | 7070 | Spector server port |
| `--json` | false | Output in JSON format (machine-parseable) |
| `--api-key` | — | API key for authentication |
| `--help` | — | Show help for any command |

---

## 📋 Commands

### 📊 `index` — Index Management

Create, list, and delete indexes.

```bash
# Create an index with specific dimensions
spectorctl index create --name my-index --dimensions 384

# List all indexes
spectorctl index list

# Delete an index
spectorctl index delete --name my-index
```

| Option | Required | Description |
|--------|----------|-------------|
| `--name` | ✅ | Index name |
| `--dimensions` | ✅ (create) | Vector dimensionality |

---

### 📥 `remember` — Document & Memory Ingestion

The `remember` command (with backward-compatible alias `ingest`) supports two modes, auto-detected from the flags:

#### Local Batch Mode (Direct Ingestion)

Discovers and ingests files directly into `SpectorMemory` via Spring Boot auto-configuration — no server needed. Reads configuration from `spector.yml`.

```bash
# Remember from config (root-directory, pattern, etc. from spector.yml)
spectorctl remember --config spector.yml

# Remember with explicit root directory (or with 'ingest' alias)
spectorctl remember --root /path/to/docs --pattern "**/*.md"
spectorctl ingest --root /path/to/docs --pattern "**/*.md"

# Override chunk size
spectorctl remember --config spector.yml --root . --chunk-size 1200
```

| Option | Required | Description |
|--------|----------|-------------|
| `--config` | ❌ | Path to `spector.yml` config file |
| `--root` | ❌ | Root directory for file discovery |
| `--pattern` | ❌ | File glob pattern (default from config) |
| `--chunk-size` | ❌ | Chunk size in characters (default from config) |

> [!TIP]
> If `--config` is provided and `spector.yml` contains `spector.ingestion.root-directory`, local batch mode activates automatically — no `--root` flag needed.

#### Remote Mode (via HTTP)

Sends a single document or memory to a running Spector server.

```bash
# Remember text content
spectorctl remember --id doc-1 --content "Hello world"

# Remember from a file (or with 'ingest' alias)
spectorctl remember --file README.md --title "Project README"
spectorctl ingest --file README.md --title "Project README"
```

| Option | Required | Description |
|--------|----------|-------------|
| `--id` | ❌ | Document ID (auto-generated if not provided) |
| `--content` | ❌ | Document text content |
| `--file` | ❌ | Path to file to ingest |
| `--title` | ❌ | Document title |

---

### 🔍 `recall` — Recall Documents & Memories

The `recall` command (with backward-compatible alias `search`) queries Spector for documents or memories:

```bash
# Keyword / semantic recall
spectorctl recall "vector search engine" --top-k 10

# Hybrid recall
spectorctl recall "search" --mode HYBRID --top-k 10

# Backward-compatible alias
spectorctl search "search" --top-k 10

# JSON output for scripting
spectorctl recall "search" --json
```

| Option | Required | Description |
|--------|----------|-------------|
| `<query>` | ✅ | Recall query text (positional parameter) |
| `-k, --top-k` | ❌ | Number of results (default: 10) |
| `-m, --mode` | ❌ | Search mode: `KEYWORD`, `VECTOR`, `HYBRID` (default: `KEYWORD`) |

---

### 💚 `status` — Server Status

```bash
# Human-readable status
spectorctl status

# JSON output
spectorctl status --json
```

---

### 🧠 `memory` — Cognitive Memory Operations

Available when the server is running in `MEMORY` or `HYBRID` mode.

```bash
# Remember — store a cognitive memory
spectorctl memory remember --id pref-dark --text "User prefers dark mode" \
  --type EPISODIC --source USER_STATED --tags "ui,preferences"

# Recall — cognitive search across all tiers
spectorctl memory recall --query "dark theme settings" --topK 5

# Forget — tombstone a memory
spectorctl memory forget --id pref-dark

# Reinforce — positive/negative feedback
spectorctl memory reinforce --id fact-42 --valence 1

# Suppress — hide from recall (reversible)
spectorctl memory suppress --id noisy-fact --reason "Not relevant"

# Introspect — how well does the system know a topic?
spectorctl memory introspect "kubernetes"

# Reminder — schedule a future reminder
spectorctl memory reminder --text "check build logs" --delay 3600

# Scratchpad — quick-write to working memory
spectorctl memory scratchpad "working hypothesis: the issue is GC"

# Why-Not — explain why a memory was not recalled
spectorctl memory why-not --id fact-42 --query "pool config"

# Reflect — trigger sleep consolidation
spectorctl memory reflect

# Status — memory tier counts
spectorctl memory status --json
```

---

## 🎨 Output Formats

### 📋 Table Format (Default)

Human-readable tables for interactive use:

```
$ spectorctl status
╔══════════════════════════════════════╗
║ Spector Status                ║
╠══════════════════════════════════════╣
║ Status:    RUNNING                   ║
║ Port:      7070                      ║
║ SIMD:      AVX-512 (512-bit)         ║
║ GPU:       Available (CUDA 12.x)     ║
║ Documents: 1250                      ║
╚══════════════════════════════════════╝
```

```
$ spectorctl search --text "nearest neighbor" --topK 5
┌─────────────┬────────┬────────────────────────────────────────────┐
│ ID          │ Score  │ Content                                    │
├─────────────┼────────┼────────────────────────────────────────────┤
│ doc-1       │ 0.9412 │ Spector uses HNSW for approximate.. │
│ doc-2       │ 0.7231 │ IVF-PQ provides memory-efficient billion.. │
└─────────────┴────────┴────────────────────────────────────────────┘
```

### 🔧 JSON Format (`--json`)

Machine-parseable output for scripting and automation:

```json
{"status": "RUNNING", "port": 7070, "simd": "AVX-512 (512-bit)", "gpuAvailable": true, "documentCount": 1250}
```

---

## 🔧 Scripting Examples

### Pipe to jq

```bash
# Extract document IDs from recall results
spectorctl recall "query" --json | jq '.results[].id'

# Check server health in CI
if spectorctl status --json | jq -e '.status == "RUNNING"' > /dev/null; then
  echo "Server is healthy"
fi
```

### Batch Ingestion from File

```bash
# Ingest from a JSONL file
while IFS= read -r line; do
  id=$(echo "$line" | jq -r '.id')
  content=$(echo "$line" | jq -r '.content')
  spectorctl remember --id "$id" --content "$content"
done < documents.jsonl
```

### Health Check Script

```bash
#!/bin/bash
MAX_RETRIES=30
for i in $(seq 1 $MAX_RETRIES); do
  if spectorctl --host $SPECTOR_HOST --port $SPECTOR_PORT status --json 2>/dev/null | \
     jq -e '.status == "RUNNING"' > /dev/null 2>&1; then
    echo "✅ Spector is ready"
    exit 0
  fi
  echo "⏳ Waiting for server... ($i/$MAX_RETRIES)"
  sleep 1
done
echo "❌ Server did not start in time"
exit 1
```

---

## ⚠️ Error Handling

| Scenario | Behavior |
|----------|----------|
| Server unreachable | Displays connection error with host:port |
| Invalid arguments | Shows error message and command usage |
| Missing required options | Shows which options are missing |
| No results found | Displays empty result table |

```
$ spectorctl --host badhost --port 9999 status
Error: Cannot connect to badhost:9999 — Connection refused
```

---

## 🔗 See Also

- [REST API Reference](../api-reference/rest-endpoints.md) — The API that spectorctl uses

- [Getting Started](../getting-started/quickstart.md) — Server setup before using CLI

- [Configuration Guide](../configuration/parameters.md) — Server configuration