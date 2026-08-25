# spector-cli 🖥️

> **Multi-function Command-line interface (`spectorctl`) and standalone MCP server runner for Spector.**

`spector-cli` implements **`spectorctl`**, a unified CLI packaged as the standalone runnable `spector.jar` that supports:
- **MCP server** — run the Model Context Protocol server over STDIO (`spectorctl mcp`)
- **Local batch mode** — discover and ingest files directly into `SpectorMemory` (`spectorctl ingest --root`)
- **Remote mode** — manage a running Spector server via REST API (search, status, memory inspect/recall)

---

## 🚀 Quick Start

```bash
# Build standalone JAR from source
mvn clean package -pl synapse/spector-cli -am -DskipTests

# Run MCP server (default STDIO transport for AI agents)
java --enable-preview --add-modules jdk.incubator.vector \
    -jar synapse/spector-cli/target/spector.jar mcp --config spector-local.yml

# Run CLI commands
java --enable-preview --add-modules jdk.incubator.vector \
    -jar synapse/spector-cli/target/spector.jar [command] [options]
```

---

## 🤖 MCP Server

```bash
# Start MCP server with configuration file
spectorctl mcp --config spector.yml

# Start MCP server with custom data directory and dimensions
spectorctl mcp --dims 4096 --data-dir ~/.spector/data --ollama-model qwen3-embedding:latest
```

---

## 📥 Ingestion

The `ingest` command auto-detects mode from the flags provided:

### Local Batch Mode (Direct Memory Ingestion)

Discovers and ingests files directly into `SpectorMemory` — no server needed. Honors `spector.yml` config.

```bash
# Ingest from config (root-directory from spector.yml)
spectorctl ingest --config spector.yml

# Ingest with explicit root directory
spectorctl ingest --root /path/to/docs --pattern "**/*.md"

# Override chunk size
spectorctl ingest --config spector.yml --root . --chunk-size 1200
```

### Remote Mode (via HTTP)

Sends a single document to a running Spector server.

```bash
# Ingest text content
spectorctl ingest --content "Hello world" --id doc-1

# Ingest from a file
spectorctl ingest --file README.md --title "Project README"
```

---

## 🧠 Cognitive Memory CLI

```bash
# Store a memory
spectorctl memory remember --text "Spector uses 4-tier cognitive memory" --tier SEMANTIC

# Recall memories
spectorctl memory recall "cognitive memory" --top-k 5

# View memory status
spectorctl memory status
```

---

## 🔍 Search

```bash
# Search with default settings
spectorctl search "vector databases" --top-k 5

# Output as JSON (machine-parseable)
spectorctl search "HNSW algorithm" --json
```

---

## 📊 Status

```bash
# Show engine status
spectorctl status

# JSON output
spectorctl status --json
```

---

## 🌐 Global Options

| Option | Default | Description |
|--------|---------|-------------|
| `--host` | localhost | Spector server hostname (remote mode) |
| `--port` | 7070 | Spector server port (remote mode) |
| `--json` | false | Output in JSON format |
