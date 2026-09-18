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

## 1. Context

Spector's Model Context Protocol (MCP) server exposes 22+ cognitive memory tools to external AI agents (including Claude Desktop, Cursor, Copilot, and autonomous agent loops). Previously, all tool metadata — including tool names, descriptive LLM prompt text, JSON Schema parameter definitions, and OAuth scopes — was declared imperatively in Java classes using fluent builder methods (`ToolSchemaBuilder`).

## 2. Problem Statement

The imperative Java builder approach created several severe maintenance and architectural problems:

1. **Code Bloat & Noise**: Approximately 60–70% of each tool class (~150–200 lines per file) consisted of repetitive schema construction and string formatting rather than tool execution logic.
2. **Schema Drift & Verification**: Validating imperative builder calls against standard JSON Schema specifications (Draft 7 / 2020-12) was difficult without instantiating the entire JVM server at runtime.
3. **Documentation Siloing**: Tool descriptions and schemas could not easily be extracted, linted, or exported for documentation (MkDocs), client SDK generators, or external IDE schemas without running reflection.
4. **Maintenance Overhead**: Updating descriptions, tuning agent prompt instructions, or adjusting parameter metadata required modifying and recompiling Java code.

## 3. Decision Drivers

- **Declarative Schema Separation**: Decouple tool contracts and prompt instructions from execution logic.
- **Specification Compliance**: Strict adherence to JSON Schema standards (Draft 7 / 2020-12) and MCP 2024-11-05 tool schemas.
- **Automated Verification**: Tool contracts must be verifiable at build time via automated unit test suites without starting network servers.
- **Developer Ergonomics**: Reduce boilerplate Java code in tool handlers while simplifying prompt adjustments.

## 4. Considered Options

### Option 1: Declarative JSON Resource Files (`mcp/tools/{tool_name}.json`) (Selected)
- **Description**: Define tool metadata and JSON Schemas in dedicated `.json` resources loaded via classpath scanning and cached on startup.
- **Advantages**: Removes ~1,500+ lines of Java boilerplate; schemas are independently validatable; prompt tuning does not touch Java code.
- **Disadvantages**: Requires maintaining resource files alongside Java handler classes.

### Option 2: Java Annotation Processing (APT)
- **Description**: Annotate tool classes with Java annotations and generate schemas during compilation.
- **Advantages**: Keeps definitions near Java code.
- **Disadvantages**: Complex custom annotation processor; poor multiline prompt string formatting in Java annotations.

### Option 3: Unified Single-File OpenAPI / MCP Registry
- **Description**: Consolidate all 22+ tool definitions into a single massive JSON/YAML specification file.
- **Advantages**: Single file to distribute.
- **Disadvantages**: Merge conflicts during concurrent tool development; unwieldy file size (>2,500 lines).

## 5. Decision Outcome

**Chosen Option**: Option 1 (Granular Declarative JSON Resources).

### Architecture:

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
6. **`McpToolSchemaValidationTest`**: Test suite ensuring every registered Java tool has a corresponding valid JSON schema.

### Positive Consequences
- **Code Footprint**: Removed ~1,500+ lines of boilerplate builder code across 22 tools in `spector-mcp`.
- **Maintainability**: Tool documentation, parameter hints, and agent prompt engineering can be edited directly in clean JSON files without touching Java code.
- **Contract Verification**: JSON Schema validation runs in automated unit tests, catching typos, invalid types, or missing parameters before release.
- **Export & Sync**: Tool definitions can be exported or converted into documentation, OpenAPI specs, or client libraries effortlessly.

### Negative Consequences & Trade-offs
- Adding a new tool requires both a `src/main/resources/mcp/tools/{name}.json` and a Java class (strictly enforced by unit tests).
- Specs are loaded into memory on startup (minimal overhead: ~50KB total JSON parsed once in <2ms).

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: JSON Resources** | Clean separation, zero Java bloat, instant schema linting | Requires dual file creation (JSON + Java handler) |
| **Option 2: Annotation Processor** | Single-file locality | Fragile APT build dependencies, awkward multiline prompts |
| **Option 3: Monolithic Registry** | Single file | Constant git merge conflicts, difficult component ownership |

## 7. Implementation Plan

1. **Phase 1**: Define `McpToolSpec` record and `McpToolSpecLoader` in `synapse/spector-mcp/spec`.
2. **Phase 2**: Author JSON schema definitions for all 22+ MCP tools in `src/main/resources/mcp/tools`.
3. **Phase 3**: Refactor `McpToolHandler` and all concrete tool subclasses to extend the declarative base.
4. **Phase 4**: Add `McpToolSchemaValidationTest` suite to enforce complete coverage.

## 8. Code Reference & Verification

- **Primary Module(s)**: `synapse/spector-mcp`
- **Key Packages**: `com.spectrayan.spector.mcp.spec`, `com.spectrayan.spector.mcp.tools`
- **Classes**: `McpToolSpec.java`, `McpToolSpecLoader.java`, `McpToolHandler.java`, `MemoryRememberTool.java`
- **Verification Tests**: `McpToolSchemaValidationTest.java`, `McpServerIntegrationTest.java`
