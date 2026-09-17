# ADR-0042: Graph Recall Architecture and Cognitive Traversals

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-20 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

Spector's Model Context Protocol (MCP) server in `synapse/spector-mcp` exposes cognitive memory operations to external agents and runtimes. Among these tools, `MemoryGraphRecallTool` provides multi-hop graph traversal across temporal facts and entity hyperedges to discover relational pathways between entities.

## 2. Problem Statement

An architectural audit of the MCP layer revealed a severe module boundary violation in `MemoryGraphRecallTool`:
- The class contained **570 lines of traversal engine code** directly importing six internal memory subsystem classes: `EntityDirectory`, `HyperEntityGraphMemory`, `HyperEdge`, `HyperEdgeVertex`, `TemporalFact`, and `TemporalKnowledgeGraph`.
- It bypassed the public `SpectorMemory` API, drilling directly into raw subsystems via `memory.admin().entityDirectory()`, `memory.admin().hyperEntityGraph()`, and `memory.admin().temporalKnowledgeGraph()`.
- It implemented its own ad-hoc Breadth-First Search (BFS) traversal engine, duplicating logic that already existed in `CognitiveGraphFacade`.
- Because traversal logic lived in the MCP presentation layer, other consumers (REST APIs, internal agents, CLI) could not reuse graph recall, and upcoming features like temporal supersession and conflict-aware recall would have to be duplicated across multiple modules.

## 3. Decision Drivers

- **Module Encapsulation**: The MCP layer must remain a thin presentation adapter; all graph traversal and cognitive reasoning must reside in `memory/spector-memory`.
- **DRY Cognitive Traversals**: Unify multi-hop BFS graph traversal inside `CognitiveGraphFacade` to benefit from caching (`SpectorCache`).
- **Clean Public API**: Expose graph recall via typed options (`GraphRecallOptions`) and results (`GraphTraversalResult`) on `SpectorMemory`.
- **Architectural Boundary Enforcement**: Strictly prevent external layers from importing internal graph storage implementations.

## 4. Considered Options

### Option 1: Status Quo (Keep Traversal in `MemoryGraphRecallTool`)
- **Description**: Leave BFS traversal engine inside the MCP tool class.
- **Advantages**: No immediate refactoring required.
- **Disadvantages**: Module boundary violation; logic inaccessible to non-MCP consumers; requires duplicate implementation for temporal supersession.

### Option 2: Expose Raw Subsystems Publicly
- **Description**: Promote `HyperEntityGraphMemory`, `EntityDirectory`, and `TemporalKnowledgeGraph` to the public API of `SpectorMemory`.
- **Advantages**: Allows external callers to inspect graph structures directly.
- **Disadvantages**: Leaks off-heap storage and threading details; breaks encapsulation; severely limits internal storage refactoring.

### Option 3: Dedicated `graphRecall()` Service in `CognitiveGraphFacade` (Selected)
- **Description**: Relocate multi-hop BFS traversal into `CognitiveGraphFacade.graphRecall(...)`, expose a thin delegation method on `SpectorMemory`, reduce `MemoryGraphRecallTool` to an 80-line adapter, and enforce boundaries via ArchUnit tests.
- **Advantages**: Clean encapsulation; reusable across all presentation layers; leverages internal caching; single location for temporal supersession logic.
- **Disadvantages**: Requires updating MCP tool call sites and tests.

## 5. Decision Outcome

**Chosen Option**: Option 3 (Dedicated `graphRecall()` Service in `CognitiveGraphFacade`).

### Architectural Implementation:

#### Step 1: Add `graphRecall()` to `CognitiveGraphFacade` and `SpectorMemory`
`CognitiveGraphFacade` encapsulates multi-hop BFS traversal, entity resolution, and temporal fact filtering:

```java
public final class CognitiveGraphFacade {
    /**
     * Multi-hop BFS traversal for GraphRAG — entity-centric path discovery
     * across TemporalKnowledgeGraph facts and HyperEntityGraph co-occurrences.
     */
    public GraphTraversalResult graphRecall(GraphRecallOptions options) {
        // BFS traversal, caching, entity resolution, and temporal filtering
    }
}
```

Exposed via thin delegation on `SpectorMemory`:

```java
default GraphTraversalResult graphRecall(GraphRecallOptions options) {
    return admin().graph().graphRecall(options);
}
```

#### Step 2: Slim Down `MemoryGraphRecallTool` to an Adapter (~80 Lines)
`MemoryGraphRecallTool` delegates entirely to `SpectorMemory.graphRecall()` and focuses purely on argument parsing and MCP text response formatting:

```java
@Override
protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                Map<String, Object> args) {
    GraphTraversalResult result = memory.graphRecall(
        GraphRecallOptions.builder()
            .startEntity(startEntityName)
            .targetEntity(targetEntityName)
            .maxHops(maxHops)
            .entityTypeFilters(entityTypeFilters)
            .relationTypeFilters(relationTypeFilters)
            .topPaths(topPaths)
            .includeMemories(includeMemories)
            .build());
    return textResult(formatResult(result));
}
```

#### Step 3: Restrict Internal Class Visibility and Enforce ArchUnit Rules
- Internal graph structures (`HyperEdge`, `HyperEdgeVertex`) restricted to package-private visibility where feasible.
- Automated ArchUnit architectural test ensures no classes in `com.spectrayan.spector.mcp` import internal packages (`..memory.graph..`, `..memory.temporal..`, `..memory.hebbian..`, `..memory.index..`).

### Positive Consequences
- Restores clean hexagonal architecture: MCP layer is strictly an adapter.
- Graph traversal logic is reusable by REST endpoints, CLI tools, and autonomous agent loops.
- Unblocks temporal supersession features with zero changes required in the MCP layer.
- Traversal gains transparent acceleration and caching via `SpectorCache`.

### Negative Consequences & Trade-offs
- One-time refactoring of existing MCP graph recall integration tests.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Traversal in MCP** | Zero refactor | 570 LOC boundary leak, duplicate BFS logic, no caching |
| **Option 2: Expose Raw Subsystems** | Full external access | Leaks off-heap internals, locks in storage structures |
| **Option 3: CognitiveGraphFacade (Selected)** | Clean encapsulation, reusable, cached, ArchUnit-enforced | Minor refactoring across MCP tool classes |

## 7. Implementation Plan

1. **Phase 1**: Author `GraphRecallOptions` and `GraphTraversalResult` domain records in `com.spectrayan.spector.memory.model`.
2. **Phase 2**: Port multi-hop BFS traversal engine from `MemoryGraphRecallTool` into `CognitiveGraphFacade.graphRecall()`.
3. **Phase 3**: Add `graphRecall(GraphRecallOptions)` default method to `SpectorMemory` interface.
4. **Phase 4**: Refactor `MemoryGraphRecallTool` to delegate to `SpectorMemory.graphRecall()`.
5. **Phase 5**: Add ArchUnit test enforcing module boundaries between `spector-mcp` and internal memory packages.

## 8. Code Reference & Verification

- **Primary Module(s)**: `memory/spector-memory`, `synapse/spector-mcp`
- **Key Packages**: `com.spectrayan.spector.memory.graph`, `com.spectrayan.spector.mcp.tools`
- **Classes**: `CognitiveGraphFacade.java`, `SpectorMemory.java`, `MemoryGraphRecallTool.java`, `HyperEntityGraphMemory.java`, `TemporalKnowledgeGraph.java`
- **Verification Tests**: `CognitiveGraphFacadeTest.java`, `MemoryGraphRecallToolTest.java`
