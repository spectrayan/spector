---
title: "Interference — Deduplication"
description: "Near-duplicate detection and merge at ingestion time — preventing proactive interference from redundant memories."
---

# 🔀 Interference — Deduplication

> **Biological Analog**: **Proactive interference** occurs when old memories interfere with new learning. If you move to a new city, your old address "interferes" when you try to recall the new one. The brain resolves this by strengthening the newer trace and weakening the old one.

---

## The Problem

Without deduplication, an agent remembering the same fact repeatedly creates redundant entries:

```
memory[0]: "User prefers dark mode"     importance=0.8
memory[1]: "User prefers dark mode"     importance=0.7
memory[2]: "The user likes dark mode"   importance=0.9  ← near-duplicate
```

These compete during recall, waste storage, and dilute the Hebbian co-activation signal.

---

## How It Works

The deduplication system detects near-duplicates by computing L2 distance between the new memory's vector and existing memories. When a match is found within a configurable threshold, it **merges** rather than creating a new record:

```mermaid
flowchart TD
    NEW["New memory arrives<br/><i>with embedding vector</i>"] --> SCAN["Scan existing memories<br/><i>compute L2 distance</i>"]
    SCAN --> CHECK{"Distance < threshold?"}
    CHECK -->|"No match"| WRITE["Write new record<br/><i>normal ingestion</i>"]
    CHECK -->|"Near-duplicate found"| MERGE["Merge into existing record"]

    MERGE --> M1["importance = max(existing, new)"]
    MERGE --> M2["tags = existing OR new<br/><i>union of Bloom filters</i>"]
    MERGE --> M3["timestamp = most recent"]
    MERGE --> M4["valence = from newer"]

    style CHECK fill:#f39c12,color:white
    style MERGE fill:#9b59b6,color:white
    style WRITE fill:#00b894,color:white
```

### Merge Rules

| Field | Strategy | Rationale |
|---|---|---|
| `importance` | `max(existing, new)` | Keep the highest importance signal |
| `synapticTags` | `existing OR new` | Union of Bloom filters — broader context |
| `timestamp` | Most recent | Memory is "refreshed" |
| `recallCount` | Preserved | Reconsolidation history maintained |
| `valence` | From newer | Most recent emotional assessment |

---

## Where It Fits

Deduplication runs during the **ingestion pipeline** — after embedding but before writing. If a merge occurs, no new record is created — the existing record is updated in-place:

```mermaid
flowchart LR
    EMBED["Step 1: Embed text"] --> TAGS["Step 2: Encode tags"]
    TAGS --> SURPRISE["Step 3: Surprise detection"]
    SURPRISE --> DEDUP["Step 4: Deduplication<br/><b>merge or write</b>"]
    DEDUP --> STORE["Step 5: Write to tier store"]

    style DEDUP fill:#9b59b6,color:white
```

---

## Next Steps

- :material-clock: [**Prospective — Future Intents**](prospective.md) — time-triggered reminders
- :material-brain: [**Architecture**](architecture.md) — where deduplication fits in the pipeline
