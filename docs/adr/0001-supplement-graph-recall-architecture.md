# ADR-0001-S: Graph Recall Architecture and Cognitive Traversals

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

**Persona**: Architecture Working Group (Systems Architecture)
**Date**: August 20, 2026
**Severity**: Architectural — module boundary violation in MCP layer

---

## You're Right, Bharat — This Is a Real Problem

[`MemoryGraphRecallTool`](file:///home/bharat/git/spector/synapse/spector-mcp/src/main/java/com/spectrayan/spector/mcp/tools/memory/MemoryGraphRecallTool.java) has **570 lines of traversal engine code** that directly reaches into 6 internal memory subsystem classes. The MCP layer is doing work that belongs in the memory module.

---

## The Coupling Problem

### What MemoryGraphRecallTool Imports from Internal Memory

```java
// ❌ These are internal subsystem classes — NOT part of SpectorMemory public API
import com.spectrayan.spector.memory.graph.EntityDirectory;          // L37
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;   // L38
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory.HyperEdge;      // L39
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory.HyperEdgeVertex; // L40
import com.spectrayan.spector.memory.index.MemoryIndex;              // L41
import com.spectrayan.spector.memory.temporal.TemporalFact;          // L43
import com.spectrayan.spector.memory.temporal.TemporalKnowledgeGraph; // L44
```

### How It Reaches Into Internals

```java
// Lines 117-124: Drilling through admin() into raw subsystems
EntityDirectory entityDirectory = memory.admin().entityDirectory();
HyperEntityGraphMemory hyperEntityGraph = memory.admin().hyperEntityGraph();
TemporalKnowledgeGraph temporalKnowledgeGraph = memory.admin().temporalKnowledgeGraph();
MemoryIndex memoryIndex = memory.admin().index();
```

Then it implements a **full BFS traversal engine** (lines 205-381) that manually:
1. Reads `TemporalFact` records and checks `isRetraction()` / `retractedFactIds()`
2. Iterates `HyperEdge` / `HyperEdgeVertex` structures
3. Builds slot-to-ID mappings via `MemoryIndex.buildGraphSlotMappings()`
4. Resolves entity names/types via `EntityDirectory`
5. Filters by entity types and relation types

### The Irony: CognitiveGraphFacade Already Exists

[`CognitiveGraphFacade`](file:///home/bharat/git/spector/memory/spector-memory/src/main/java/com/spectrayan/spector/memory/graph/CognitiveGraphFacade.java) was **designed exactly for this purpose** (line 49):

> *"Encapsulates graph traversal, statistics, and neighborhood queries so that consumers (MAO, admin dashboards) never touch raw graph internals directly."*

It already has:
- `neighborhood()` — BFS traversal with configurable depth
- `traceWhy()` / `traceEffects()` — causal chain traversal
- `overview()` — sampled graph overview
- `topologyStats()` — entity/relation type aggregation
- All the Hebbian + Temporal + Entity edge collection logic
- **Caching** via `SpectorCache` (which `MemoryGraphRecallTool` lacks!)

But `MemoryGraphRecallTool` **duplicates** much of this logic with its own BFS implementation, entity resolution, temporal fact filtering, and formatting — all in the MCP layer.

---

## Full MCP Layer Coupling Audit

| MCP Tool | Uses `admin()`? | Internal Classes Accessed | Violation? |
|:---------|:---------------|:--------------------------|:-----------|
| [`MemoryGraphRecallTool`](file:///home/bharat/git/spector/synapse/spector-mcp/src/main/java/com/spectrayan/spector/mcp/tools/memory/MemoryGraphRecallTool.java) | ✅ Heavy | `EntityDirectory`, `HyperEntityGraphMemory`, `HyperEdge`, `HyperEdgeVertex`, `TemporalKnowledgeGraph`, `TemporalFact`, `MemoryIndex` | **❌ Major** — 570 LOC of traversal logic |
| [`MemoryReinforceTool`](file:///home/bharat/git/spector/synapse/spector-mcp/src/main/java/com/spectrayan/spector/mcp/tools/memory/MemoryReinforceTool.java) | ✅ Light | `RecallPipeline` (was-lateral check) | ⚠️ Minor — single method call |
| [`MemoryStatusTool`](file:///home/bharat/git/spector/synapse/spector-mcp/src/main/java/com/spectrayan/spector/mcp/tools/memory/MemoryStatusTool.java) | ✅ Light | `LateralEvaluator`, `MemoryWal`, `SuppressionSet`, `ProspectiveScheduler` | ⚠️ Acceptable — status/admin tools should access admin |
| [`ResultFormatter`](file:///home/bharat/git/spector/synapse/spector-mcp/src/main/java/com/spectrayan/spector/mcp/util/ResultFormatter.java) | ✅ Light | `MemoryWal`, `SuppressionSet`, `ProspectiveScheduler` | ⚠️ Acceptable — operational stats |

> **`MemoryGraphRecallTool` is the only serious offender.** The others use `admin()` for legitimate read-only status queries.

---

## Why This Matters for Temporal Supersession

Here's the connection you spotted, Bharat:

1. `SpectorMemory` already has [`assertFact()`](file:///home/bharat/git/spector/memory/spector-memory/src/main/java/com/spectrayan/spector/memory/SpectorMemory.java#L580-L581), [`retractFact()`](file:///home/bharat/git/spector/memory/spector-memory/src/main/java/com/spectrayan/spector/memory/SpectorMemory.java#L588), and [`factsAbout()`](file:///home/bharat/git/spector/memory/spector-memory/src/main/java/com/spectrayan/spector/memory/SpectorMemory.java#L596) on the public API
2. `TemporalKnowledgeGraph` has bitemporal validity windows and `ContradictionResolver`
3. `CognitiveGraphFacade` has traversal + causal query engines

**But `MemoryGraphRecallTool` bypasses all of this** and directly reads `TemporalFact` records, manually checks `isRetraction()`, and builds its own traversal. So when we add temporal supersession features (history chains, conflict-aware recall), we'd have to add it in **two places** — the memory module AND the MCP tool. That's the maintenance trap.

---

## Proposed Fix

### Step 1: Add `graphRecall()` to `SpectorMemory` (or `CognitiveGraphFacade`)

The BFS traversal in `MemoryGraphRecallTool` should become a first-class method on the memory module:

```java
// Option A: On SpectorMemory directly
public interface SpectorMemory {
    // ... existing API ...

    /**
     * Multi-hop graph traversal across temporal facts and entity hyperedges.
     * Returns structured relational paths with grounding memory context.
     */
    GraphTraversalResult graphRecall(GraphRecallOptions options);
}
```

```java
// Option B: On CognitiveGraphFacade (preferred — it already owns traversal)
public final class CognitiveGraphFacade {
    // ... existing methods ...

    /**
     * Multi-hop BFS traversal for GraphRAG — entity-centric path discovery
     * across TemporalKnowledgeGraph facts and HyperEntityGraph co-occurrences.
     */
    public GraphTraversalResult graphRecall(String startEntity,
                                             String targetEntity,
                                             int maxHops,
                                             Set<String> entityTypeFilters,
                                             Set<String> relationTypeFilters,
                                             int topPaths);
}
```

> [!TIP]
> **Option B is cleaner** — `CognitiveGraphFacade` already owns graph traversal (`neighborhood`, `traceWhy`, `traceEffects`). Adding `graphRecall` keeps the pattern consistent and avoids bloating `SpectorMemory`.

Then expose it via `SpectorMemory`:

```java
// On SpectorMemory — thin delegation
default GraphTraversalResult graphRecall(GraphRecallOptions options) {
    return admin().graph().graphRecall(options);
}
```

### Step 2: Slim Down MemoryGraphRecallTool to ~80 Lines

The MCP tool becomes a thin adapter:

```java
// MemoryGraphRecallTool — AFTER refactor
@Override
protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                    Map<String, Object> args) {
    // 1. Parse args (unchanged)
    // 2. Delegate to memory module
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
    // 3. Format output (keep formatting in MCP layer — that's presentation)
    return textResult(formatResult(result));
}
```

### Step 3: Restrict Internal Classes

Once the MCP layer no longer imports internal graph/temporal classes:

| Class | Current Visibility | Proposed |
|:------|:-------------------|:---------|
| `EntityDirectory` | `public` | Keep `public` (used by `CognitiveGraphFacade`, admin API) |
| `HyperEntityGraphMemory` | `public` | Keep `public` (same reason) |
| `HyperEdge` / `HyperEdgeVertex` | `public` inner classes | Make **package-private** |
| `TemporalFact` | `public` | Keep `public` (used in `SpectorMemory.factsAbout()`) |
| `TemporalKnowledgeGraph` | `public` | Keep `public` (admin accessor) |
| `MemoryIndex` | `public` | Keep `public` (admin accessor) |

> [!IMPORTANT]
> We can't make these `protected` or package-private in the traditional sense since they're in a different package. But we CAN enforce the boundary by:
> 1. **Removing the imports from MCP** — the code simply won't compile if someone re-adds them
> 2. **Adding an ArchUnit test** — `noClasses().that().resideInAPackage("..mcp..").should().dependOnClassesThat().resideInAnyPackage("..memory.graph..", "..memory.temporal..", "..memory.hebbian..", "..memory.index..")`
> 3. **JPMS modules** (future) — when we add `module-info.java`, only export `com.spectrayan.spector.memory` and `com.spectrayan.spector.memory.model` to the MCP module

---

## Impact on Temporal Supersession Feature

If we do this refactor first, then adding temporal supersession becomes clean:

1. Add `RecallMode.HISTORY` / `RecallMode.TEMPORAL` to `CognitiveGraphFacade.graphRecall()`
2. Extend `GraphTraversalResult` with supersession chains
3. The MCP tool automatically gets the feature — **zero changes to MCP layer**

If we DON'T refactor, we'll have to implement temporal supersession in `MemoryGraphRecallTool` too — duplicating logic across module boundaries.

---

## Recommended Execution Sequence

1. **Create `GraphRecallOptions` + `GraphTraversalResult` model classes** (in `memory.model`)
2. **Move BFS logic to `CognitiveGraphFacade.graphRecall()`** — port from `MemoryGraphRecallTool`
3. **Add thin delegation on `SpectorMemory`** — `graphRecall(options)`
4. **Slim `MemoryGraphRecallTool` to adapter** — parse args → delegate → format
5. **Add ArchUnit boundary test** — prevent future violations
6. **Then build temporal supersession** on clean foundations

> Estimated effort: **3-5 days** for the refactor, which pays for itself immediately when we build temporal supersession.

---

*— @titan, Solutions Architect*
