# spector-cli 🖥️

> **Multi-function Command-line interface (`spector`) and standalone MCP server runner for Spector.**

`spector-cli` implements **`spector`**, a unified CLI packaged as the standalone runnable `spector.jar` that supports:
- **MCP server** — run the Model Context Protocol server over STDIO (`spector mcp`)
- **Local batch mode** — discover and ingest files directly into `SpectorMemory` (`spector ingest --root`)
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
spector mcp --config spector.yml

# Start MCP server with custom data directory and dimensions
spector mcp --dims 4096 --data-dir ~/.spector/data --ollama-model qwen3-embedding:latest
```

---

## 📥 Remember (Ingestion)

The `remember` command (with backward-compatible alias `ingest`) auto-detects mode from the flags provided:

### Local Batch Mode (Direct Memory Ingestion)

Discovers and ingests files directly into `SpectorMemory` via Spring Boot auto-configuration — no server needed. Honors `spector.yml` config.

```bash
# Remember from config (root-directory from spector.yml)
spector remember --config spector.yml

# Remember with explicit root directory (or using 'ingest' alias)
spector remember --root /path/to/docs --pattern "**/*.md"
spector ingest --root /path/to/docs --pattern "**/*.md"

# Override chunk size
spector remember --config spector.yml --root . --chunk-size 1200
```

### Remote Mode (via HTTP)

Sends a single document or memory to a running Spector server.

```bash
# Remember text content
spector remember --content "Hello world" --id doc-1

# Remember from a file
spector remember --file README.md --title "Project README"
```

---

## 🧠 Cognitive Memory CLI

```bash
# Store a memory
spector memory remember --text "Spector uses 4-tier cognitive memory" --tier SEMANTIC

# Recall memories
spector memory recall "cognitive memory" --top-k 5

# View memory status
spector memory status
```

---

## 🔍 Recall (Search)

The `recall` command (with backward-compatible alias `search`) queries Spector for documents or memories:

```bash
# Recall with default settings
spector recall "vector databases" --top-k 5

# Recall using 'search' alias
spector search "vector databases" --top-k 5

# Output as JSON (machine-parseable)
spector recall "HNSW algorithm" --json
```

---

## 📊 Status

```bash
# Show engine status
spector status

# JSON output
spector status --json
```

---

## 🌐 Global Options

| Option | Default | Description |
|--------|---------|-------------|
| `--host` | localhost | Spector server hostname (remote mode) |
| `--port` | 7070 | Spector server port (remote mode) |
| `--json` | false | Output in JSON format |
