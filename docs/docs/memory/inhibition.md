---
title: "Inhibition — Suppression"
description: "Memory suppression enables explicit, reversible blocking — the digital equivalent of motivated forgetting."
---

# 🚫 Inhibition — Suppression

> **Biological Analog**: **Retrieval-Induced Forgetting** (Anderson et al., 1994) — the brain actively suppresses competing memories during recall. When you try to remember where you parked today, your brain inhibits memories of yesterday's parking spot. This is an active process, not passive decay.

---

## The Concept

Suppression is different from forgetting:

| Operation | API | Effect | Reversible? |
|---|---|---|---|
| **Forget** | `memory.forget(id)` | Tombstones the record — permanently excluded from all scans | No |
| **Suppress** | `memory.suppress(id, reason)` | Adds to suppression set — excluded from recall results | **Yes** |

Tombstoning modifies the off-heap flags byte (bit 0 = 1). Suppression maintains a separate in-memory set — the underlying memory is untouched and can be un-suppressed later.

---

## How It Works

```mermaid
flowchart TD
    subgraph "Suppress (Reversible)"
        S1["memory.suppress(id, reason)"] --> S2["Add to suppression set<br/><i>in-memory, auditable</i>"]
        S2 --> S3["Recall pipeline filters<br/>suppressed IDs after scoring"]
        S3 --> S4["memory.unsuppress(id)<br/><i>restores to recall</i>"]
    end

    subgraph "Forget (Permanent)"
        F1["memory.forget(id)"] --> F2["Set tombstone flag<br/><i>off-heap bit flip</i>"]
        F2 --> F3["Scorer Phase 1 skips<br/>in ~1 CPU cycle"]
        F3 --> F4["Cannot be undone"]
    end

    style S4 fill:#2ecc71,color:white
    style F4 fill:#e74c3c,color:white
```

**Performance difference**: Tombstoned memories are skipped in Phase 1 of the scorer (~1 cycle). Suppressed memories go through the full 6-phase scoring pipeline and are only filtered afterward. For bulk removal, `forget()` is more efficient.

---

## Where It Fits in the Pipeline

Suppression is checked **after** the 6-phase scorer completes but **before** habituation:

```mermaid
flowchart TD
    SCORER["6-Phase Scorer<br/><i>produces scored candidates</i>"] --> SUPPRESS["Step 4: Suppression Filter<br/><b>Remove suppressed IDs</b>"]
    SUPPRESS --> HAB["Step 5a: Habituation<br/><i>attenuate repeated results</i>"]
    HAB --> GRAPH["Steps 5c–5e: Graph Augmentation"]
    GRAPH --> SORT["Final Sort → Top-K"]

    style SUPPRESS fill:#e74c3c,color:white
    style SORT fill:#00b894,color:white
```

---

## Use Cases

### 1. User Redaction

```
User: "Please forget what I said about project X"
→ memory.suppress("project-x-conversation-1", "User requested redaction")
→ memory.suppress("project-x-conversation-2", "User requested redaction")
```

### 2. Context Switching

```
Agent switching to backend work:
→ memory.suppress("frontend-task-context", "Switching to backend work")

Later, switching back:
→ memory.unsuppress("frontend-task-context")
```

### 3. Stale Data Quarantine

```
Data source under validation:
→ memory.suppress(staleIds, "Source under validation — suppressed until confirmed")
```

### 4. A/B Testing Memory Strategies

```
Suppress certain memories to test agent performance without them:
→ experimentGroup.forEach(id → memory.suppress(id, "A/B test: control group"))
```

---

## Next Steps

- :material-speedometer: [**Performance**](performance.md) — benchmark results and optimization techniques
- :material-sleep: [**Habituation — Anti-Filter Bubble**](habituation.md) — automatic score attenuation
- :material-brain: [**Architecture**](architecture.md) — where suppression fits in the pipeline
