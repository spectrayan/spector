# 🖥️ CLI Reference: `spector`

> **Manage Spector from the command line.** `spector` provides direct access to the embedded cognitive memory engine, background services, diagnostics, MCP agent endpoints, document ingestion, and status monitoring.

---

## 📦 Installation & Execution

### Standalone Executable (Recommended)
Install using the one-line POSIX or Windows installers:
```bash
# macOS / Linux
curl -fsSL https://raw.githubusercontent.com/spectrayan/spector/main/scripts/install.sh | sh

# Windows (PowerShell)
irm https://raw.githubusercontent.com/spectrayan/spector/main/scripts/install.ps1 | iex

# Homebrew (macOS / Linux)
brew tap spectrayan/spector https://github.com/spectrayan/spector
brew install spector
```

### Build from Source
Build the unified fat JAR with all Synapse modules and dependencies:
```bash
mvn clean package -pl synapse/spector-cli -am -DskipTests
```
The executable JAR is emitted at `synapse/spector-cli/target/spector.jar`. Run with:
```bash
java --enable-preview --add-modules=jdk.incubator.vector --enable-native-access=ALL-UNNAMED -jar synapse/spector-cli/target/spector.jar [command] [options]
```

---

## 🌐 Global Options

| Option | Default | Description |
|--------|---------|-------------|
| `-h, --host` | `localhost` | Spector server hostname |
| `-p, --port` | `7070` | Spector server port |
| `--json` | `false` | Output in JSON format (machine-parseable) |
| `--api-key` | — | API key for authentication |
| `--help` | — | Show help for any command |

---

## 📋 Commands

### 🩺 `doctor` — Environment & Diagnostic Checks

Inspects JDK version (requires OpenJDK 25+), Panama Vector API, SIMD capabilities, directory permissions, Synapse daemon health on `/actuator/health`, and generates AI agent integration snippets.

```bash
# Standard diagnostic check
spector doctor

# JSON report (exits 0 on OK, 1 on FAIL)
spector doctor --json

# Emit copy-pasteable MCP configuration for AI tools
spector doctor --print-config claude
spector doctor --print-config cursor
spector doctor --print-config http
```

---

### ⚡ `mcp` — Model Context Protocol Server

Runs the MCP server over standard input/output (stdio) for local AI coding assistants (Claude Desktop, Cursor, Windsurf) using the in-process Panama SIMD vector engine and local ONNX embeddings:

```bash
# Run stdio MCP server directly
spector mcp

# Specify custom data directory and dimensions
spector mcp --data-dir ~/.spector/data --dims 384
```

---

### 🚀 `serve` — Launch Synapse Daemon

Starts the full Spector Synapse REST and HTTP MCP server on port 7070 (or custom port) with `/actuator/health` endpoint:

```bash
# Launch server with default data directory
spector serve --port 7070

# Launch with custom data directory
spector serve --port 7070 --data-dir ~/.spector/data
```

---

### 🛠️ `init` — Configuration Scaffolding

Initializes `~/.spector` or current workspace with default `spector.yml` configuration:

```bash
spector init
```

---

### 📥 `remember` — Document & Memory Remember Pathway

Store memories directly into the cognitive store or batch remember documents:

```bash
# Remember a text fact
spector remember --id doc-1 --content "Fast SIMD vector memory"

# Batch remember from markdown directory
spector remember --root /path/to/docs --pattern "**/*.md"
```

---

### 🔍 `recall` — Fused Cognitive Recall & Search

Query memories using fused cognitive scoring (vector similarity + Hebbian graph + temporal decay):

```bash
# Recall top 5 memories
spector recall "SIMD vector search" --top-k 5

# JSON output for scripting
spector recall "SIMD vector search" --json
```

---

### 💚 `status` — Server Health & Memory Statistics

```bash
# Inspect running server status
spector status

# JSON status
spector status --json
```

---

### 🧠 `memory` — Advanced Cognitive Memory Operations

```bash
# Store memory with cognitive tags
spector memory remember --id pref-dark --text "User prefers dark mode" --tags "ui,preferences"

# Recall with cognitive scoring
spector memory recall --query "dark theme" --topK 5

# Tombstone a memory
spector memory forget --id pref-dark

# Reinforce memory with LTP
spector memory reinforce --id fact-42 --valence 1

# Suppress memory from active recall
spector memory suppress --id noisy-fact --reason "Temporary override"
```

---

## 🔧 Scripting Examples

### Pipe to jq
```bash
# Extract document IDs from recall results
spector recall "query" --json | jq '.results[].id'

# Check health in CI
if spector doctor --json | jq -e '.summary.passed == true' > /dev/null; then
  echo "Environment is healthy"
fi
```

---

## 🔗 See Also

- [REST API Reference](../api-reference/rest-endpoints.md) — The REST API exposed by `spector serve`
- [Getting Started](../getting-started/quickstart.md) — 30-second quickstart
- [Configuration Guide](../configuration/parameters.md) — `spector.yml` parameters