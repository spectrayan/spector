# ⚡ Spector MCP Server

**Agent-native search and cognitive memory integration for the Spector AI Memory Backbone.**

Give any AI agent (Claude Desktop, Cursor, autonomous agents) instant access to Spector's SIMD-accelerated vector search engine and cognitive memory — with zero network overhead. The MCP server runs in-process via `SpectorMemory`, calling memory directly on virtual threads for **88µs p50** query latency.

## Architecture

```
AI Agent --JSON-RPC (stdio)----> SpectorMcpServer (thin orchestrator)
                                ├── SpectorMemory (cognitive memory)
AI Agent --JSON-RPC (HTTP)----> ├── SpectorToolRegistry
  POST /mcp                     │   ├── MemoryRememberTool    ──► memory.remember()
                                │   ├── MemoryRecallTool       ──► memory.recall()
                                │   ├── MemoryStatusTool        ──► memory.introspect()
                                │   ├── MemoryReinforceTool     ──► memory.reinforce()
                                │   ├── MemoryForgetTool        ──► memory.forget()
                                │   ├── MemoryIntrospectTool    ──► memory.introspect()
                                │   └── MemoryScratchpadTool ──► memory.remember()
                                ├── SpectorResourceProvider
                                └── SpectorPromptProvider

Total overhead: 88µs p50 per query (23-113x faster than Python MCP servers)
```

### Module Structure

```
spector-mcp/
├── src/main/java/com/spectrayan/spector/mcp/
│   ├── SpectorMcpServer.java          ← Thin orchestrator (accepts SpectorMemory)
│   ├── SpectorMcpMain.java            ← CLI entry point
│   ├── spec/
│   │   ├── McpToolSpec.java           ← Immutable tool contract record
│   │   └── McpToolSpecLoader.java     ← Classpath Jackson loader & cache
│   ├── schema/
│   │   └── ToolSchemaBuilder.java     ← Programmatic schema builder
│   ├── tools/
│   │   ├── McpToolHandler.java        ← Base class (auto-binds to McpToolSpec)
│   │   ├── SpectorToolRegistry.java   ← Tool discovery & registration
│   │   └── memory/                    ← Pure execution handlers (22 tools)
│   ├── resources/
│   │   └── SpectorResourceProvider.java
│   ├── prompts/
│   │   └── SpectorPromptProvider.java
│   └── util/
│       ├── McpTemplateEngine.java     ← Handlebars engine for mcp/templates
│       └── ResultFormatter.java
└── src/main/resources/mcp/
    ├── templates/                     ← Formatting templates (*.hbs)
    └── tools/                         ← Declarative tool JSON specs (*.json)
```

## MCP Tools

### Engine Tools (available in SEARCH/HYBRID mode)

| Tool | Description |
|:---|:---|
| `engine_search` | Semantic similarity search with auto-embedding |
| `engine_hybrid_search` | Combined keyword (BM25) + vector search with RRF |
| `engine_rag` | Retrieval-Augmented Generation with source citations |
| `engine_ingest` | Document ingestion with auto-embedding + chunking |
| `engine_delete` | Document deletion by ID |
| `engine_status` | Engine metadata, SIMD capabilities, GPU status |

### Memory Tools (available in MEMORY/HYBRID mode)

| Tool | Description |
|:---|:---|
| `memory_remember` | Store a semantic memory with tags and source |
| `memory_recall` | Cognitive recall with fused scoring across tiers |
| `memory_graph_recall` | Multi-hop knowledge graph relationship traversal (GraphRAG) |
| `memory_context_pack` | Hierarchical memory context pack formatted for LLM system prompts (Tiers 1-4) |
| `memory_fact_history` | Chronological bitemporal evolution, supersession chain, and validity intervals |
| `memory_multi_evidence_recall` | Surfaces competing hypothesis clusters, epistemic confidence spread, and action policies |
| `memory_status` | Memory tier counts and persistence info |
| `memory_reinforce` | Report positive/negative outcome for a memory |
| `memory_forget` | Tombstone a memory by ID |
| `memory_introspect` | Metamemory self-analysis on a topic |
| `memory_scratchpad` | Quick-write to working memory |

## Quick Start

### 1. Build

```bash
mvn package -pl synapse/spector-cli -am -DskipTests
```

### 2. Configuration

Create a `spector.yml` with your settings:

```yaml
spector:
  mode: memory
  memory:
    enabled: true                # Enable cognitive memory tools
    persistence-mode: DISK
    persistence-path: .spector/memory
    dimensions: 768
  provider:
    embedding:
      model: nomic-embed-text
      base-url: http://localhost:11434
```

### 3. Claude Desktop Configuration

Add to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "spector": {
      "command": "java",
      "args": [
        "--add-modules", "jdk.incubator.vector",
        "--enable-native-access=ALL-UNNAMED",
        "--enable-preview",
        "-jar", "/path/to/synapse/spector-cli/target/spector.jar",
        "mcp",
        "--config", "/path/to/spector.yml"
      ]
    }
  }
}
```

### 4. CLI Options

```
--config <FILE>        Explicit config file (YAML or .properties)
--profile <NAME>       Configuration profile (loads spector-{profile}.yml)
--dims <N>             Vector dimensionality (default: 384)
--capacity <N>         Max document capacity (default: 100000)
--data-dir <DIR>       Persistence directory (auto-enables DISK mode)
--ollama-url <URL>     Ollama embedding server URL
--ollama-model <NAME>  Ollama embedding model name
--help, -h             Show help
```

> **Recommended:** Use a `spector.yml` config file. CLI flags override config file values.

## Why Spector MCP is Different

| Feature | Python Vector DB MCP | **Spector MCP** |
|:---|:---|:---|
| Search latency | 2 - 10ms (network + Python GIL) | **88us p50** (in-process SIMD) |
| Network overhead | HTTP/gRPC round-trip | **Zero** (direct method call) |
| GC pauses | Python/JVM heap pressure | ** <= 0.01%** (100% off-heap Panama) |
| Concurrent queries | Limited by Python GIL | **61,000 QPS** (Virtual Threads) |
| Dependencies | Python framework stack | **Single JAR** (zero Python) |
| Cognitive memory | External service (Mem0, Zep) | **Built-in** (opt-in via config) |

## Design Patterns

### Adding a New Tool

To add a new MCP tool:

1. **Define the declarative contract** in `src/main/resources/mcp/tools/{tool_name}.json`:
```json
{
  "name": "my_tool",
  "description": "Does something useful.",
  "category": "MEMORY",
  "scopes": ["spector:memory:read"],
  "inputSchema": {
    "type": "object",
    "required": ["input"],
    "properties": {
      "input": { "type": "string", "description": "The input." },
      "count": { "type": "integer", "description": "How many.", "default": 5 }
    }
  }
}
```

2. **Implement the execution handler** extending `MemoryToolHandler` or `McpToolHandler`:
```java
public final class MyTool extends MemoryToolHandler {
    public static final String NAME = "my_tool";

    public MyTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    @Override
    protected CallToolResult executeMemory(SpectorMemory memory, Map<String, Object> args) {
        String input = requireString(args, "input");
        int count = optionalInt(args, "count", 5);
        return textResult("Result: " + input);
    }
}
```

3. **Register in `SpectorToolRegistry.handlers()`**:
```java
List.of(
    new EngineSearchTool(),
    // ... existing tools ...
    new MyTool(memory)
);
```

### Key Design Decisions

- **Declarative Tool Specs** (`ADR-001`)  --  JSON schemas defined on classpath in `mcp/tools/*.json`, automatically validated and loaded.
- **Template Method** (`McpToolHandler`)  --  timing, error handling, security scopes, and argument parsing in the base class.
- **Handlebars Engine** (`McpTemplateEngine`)  --  centralized formatting templates in `mcp/templates/*.hbs`.
- **Open/Closed Principle** (`SpectorToolRegistry`)  --  add a tool = 1 JSON spec + 1 execution handler class.
- **Zero runtime overhead**  --  specs parsed once at startup and cached in memory.

## Protocol Support

### Transports

| Transport | Protocol | Use Case | Module |
|:---|:---|:---|:---|
| **Stdio** | JSON-RPC 2.0 over stdin/stdout | Claude Desktop, Cursor, CLI agents | `spector-mcp` (SpectorMcpMain) |
| **Streamable HTTP** | JSON-RPC 2.0 over HTTP POST `/mcp` | Web clients, remote agents, Spector Enterprise | `spector-node` (ArmeriaMcpTransport) |

### Stdio Transport (CLI / Desktop Agents)

The default transport for local MCP agents (Claude Desktop, Cursor). The MCP server runs in-process — the agent's tool calls go from JSON-RPC → Java method call → SIMD kernel with **zero network overhead**.

### Streamable HTTP Transport (Remote / Web Agents)

When Spector runs as a server (via `SpectorNode` or Spector Enterprise), the same MCP tools are exposed over **Streamable HTTP** at `/mcp`. This follows the [MCP 2025-03-26 spec](https://modelcontextprotocol.io/):

- `POST /mcp` — JSON-RPC request → JSON response
- `GET /mcp` — SSE stream for server-initiated notifications (stateful mode only)
- `DELETE /mcp` — Session termination (stateful mode only)

Supports both **stateless mode** (default, recommended — no session tracking, restart-resilient) and **stateful mode** (with `Mcp-Session-Id` header management).

```bash
# Example: call an MCP tool via Streamable HTTP
curl -X POST http://localhost:7070/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"engine_status","arguments":{}}}'
```

### Other Details

- **MCP SDK:** Official Anthropic Java SDK (`io.modelcontextprotocol.sdk:mcp`)
- **Capabilities:** Tools, Resources, Prompts
- **Java Version:** 25+ (Virtual Threads, Vector API, Panama FFM)

## Test Suite

```
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Covers: tool registry, all tool handlers, schema builder, argument validation.

