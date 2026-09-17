# ADR-0025: Declarative MCP Tool Definitions via JSON Schemas

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-24 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## Status
Accepted

## Date
2026-08-24

## Deciders
- Project Lead
- Technical Lead
- Architecture Working Group (Systems Architecture)
- Forge (Senior Full-Stack Developer)

## Context

Spector's Model Context Protocol (MCP) server exposes 22+ cognitive memory tools to external AI agents (e.g., Claude Desktop, Cursor, Copilot, and autonomous agent loops).

Previously, all tool metadata — including tool names, descriptive LLM prompt text, JSON Schema parameter definitions, and OAuth scopes — was declared imperatively in Java classes using fluent builder methods (`ToolSchemaBuilder`):

```java
// Legacy imperative approach in MemoryRememberTool.java
@Override
public String description() {
    return "Store a memory with optional cognitive metadata. Use 'tier' to choose...";
}

@Override
public Map<String, Object> inputSchema() {
    return ToolSchemaBuilder.object()
            .requiredString("text", "The fact, experience, or knowledge to remember.")
            .optionalString("tier", "Memory tier: WORKING, EPISODIC, SEMANTIC, PROCEDURAL", "SEMANTIC")
            // ... 15+ more property declarations ...
            .build();
}
```

### Key Issues with the Imperative Approach:
1. **Code Bloat & Noise**: Approximately 60–70% of each tool class (~150–200 lines per file) consisted of repetitive schema construction and string formatting rather than execution logic.
2. **Schema Drift & Verification**: It was difficult to validate imperative builder calls against standard JSON Schema specifications (Draft 7 / 2020-12) without instantiating the entire JVM server at runtime.
3. **Documentation Siloing**: Tool descriptions and schemas could not easily be extracted, linted, or exported for documentation (MkDocs), client SDK generators, or external IDE schemas without running reflection.
4. **Maintenance Overhead**: Updating descriptions, tuning agent prompt instructions, or adjusting parameter metadata required modifying and recompiling Java code.

---

## Decision

We chose **Option 1: Granular Declarative JSON Resources (`mcp/tools/{tool_name}.json`)**.

Each MCP tool defines its metadata and JSON Schema in a dedicated JSON file under `src/main/resources/mcp/tools/`:

```json
{
  "name": "memory_remember",
  "description": "Store a memory with optional cognitive metadata. Use 'tier' to choose where it goes: WORKING (ephemeral scratchpad), EPISODIC (personal experiences with time context), SEMANTIC (facts and knowledge, default), PROCEDURAL (skills, patterns, how-to). Set 'interest', 'challenge', 'urgency' (0.0-1.0) for importance tuning. Set 'valence' for emotional memories (-128=very negative, +127=very positive). Set 'arousal' for intensity (0=calm, 255=extreme). Tags help with contextual recall (e.g., 'preferences', 'architecture'). Use 'workspace_id' + 'agent_id' to store in a shared workspace.",
  "category": "MEMORY",
  "scopes": [
    "spector:memory:write"
  ],
  "inputSchema": {
    "type": "object",
    "required": [
      "text"
    ],
    "properties": {
      "text": {
        "type": "string",
        "description": "The fact, experience, or knowledge to remember."
      },
      "tier": {
        "type": "string",
        "description": "Memory tier: WORKING (ephemeral), EPISODIC (experiences), SEMANTIC (facts, default), PROCEDURAL (skills/patterns).",
        "default": "SEMANTIC"
      },
      "tags": {
        "type": "string",
        "description": "Comma-separated contextual tags for Bloom filter encoding."
      }
    }
  }
}
```

### Architectural Components:

1. **`McpToolSpec` (Record)**: Immutable representation of the tool contract (`name`, `description`, `category`, `scopes`, `inputSchema`, `outputSchema`).
2. **`McpToolSpecLoader`**: Classpath scanner and parser utilizing Jackson to load and cache tool specifications on startup.
3. **`McpToolHandler`**: Base class automatically resolving its metadata from `McpToolSpecLoader.load(name)`, supplying `name()`, `description()`, `inputSchema()`, and `requiredScopes()`.
4. **Tool Implementations**: Subclasses only implement `execute(Map<String, Object> args)` — reducing each class from ~250 LOC to ~35 LOC.
5. **Unified MCP Resources**: Grouped templates and schemas under `src/main/resources/mcp/` (`mcp/templates/` and `mcp/tools/`).
6. **`McpToolSchemaValidationTest`**: A test suite that guarantees:
   - Every registered Java tool has a corresponding `.json` definition in classpath.
   - All definitions contain valid JSON Schema syntax, non-empty descriptions, and proper types.

---

## Consequences

### Positive
- **Code Footprint**: Removed ~1,500+ lines of boilerplate builder code across 22 tools in `spector-mcp`.
- **Maintainability**: Tool documentation, parameter hints, and agent prompt engineering can be edited directly in clean JSON files without touching Java code.
- **Contract Verification**: JSON Schema validation runs in automated unit tests, catching typos, invalid types, or missing parameters before release.
- **Export & Sync**: Tool definitions can be exported or converted into documentation, OpenAPI specs, or client libraries effortlessly.

### Negative / Trade-offs
- Adding a new tool requires both a `src/main/resources/mcp/tools/{name}.json` and a Java class (enforced by unit tests).
- Specs are loaded into memory on startup (minimal overhead: ~50KB total JSON parsed once in <2ms).

---

## References

- [Model Context Protocol (MCP) Specification (2024-11-05)](https://modelcontextprotocol.io/docs/concepts/tools)
- [JSON Schema Specification (Draft 7 / 2020-12)](https://json-schema.org/)
- Spector GitHub Issue: [#659](https://github.com/spectrayan/spector/issues/659)
- Spector Pull Request: [#660](https://github.com/spectrayan/spector/pull/660)
