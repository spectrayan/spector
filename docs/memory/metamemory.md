---
title: "Metamemory — Self-Reflection"
description: "Memory health analytics — the agent's ability to reason about its own memory state and optimize behavior."
---

# 🪞 Metamemory — Self-Reflection

> **Biological Analog**: **Metamemory** is the awareness of one's own memory processes — "I know I'm forgetting things more often" or "I'm confident I remember this correctly." It's what enables humans to say "I need to write this down" or "Let me double-check that."

---

## The Concept

The introspection system provides analytics and health metrics for the memory system, enabling agents to **reason about their own memory state** and adapt their behavior accordingly.

---

## Available Insights

```mermaid
flowchart TD
    INTRO["Memory Introspection"] --> TIERS["Tier Distribution<br/><i>counts per tier</i>"]
    INTRO --> TAGS["Tag Distribution<br/><i>top topics by frequency</i>"]
    INTRO --> IMPORTANCE["Importance Stats<br/><i>min, max, mean, stddev</i>"]
    INTRO --> RECALLED["Most Recalled<br/><i>habituation candidates</i>"]
    INTRO --> OLDEST["Oldest Active<br/><i>consolidation candidates</i>"]

    style INTRO fill:#9b59b6,color:white
    style TIERS fill:#4a90d9,color:white
    style TAGS fill:#4a90d9,color:white
    style IMPORTANCE fill:#4a90d9,color:white
    style RECALLED fill:#f39c12,color:white
    style OLDEST fill:#f39c12,color:white
```

| Insight | What It Reveals | Actionable Signal |
|---|---|---|
| **Tier distribution** | Working: 50, Episodic: 12K, Semantic: 200 | Imbalance → trigger consolidation |
| **Tag distribution** | "database": 3K, "error": 2K, "deploy": 500 | Topic concentration → diversify or focus |
| **Importance stats** | mean: 0.45, stddev: 0.12 | Low mean → increase surprise sensitivity |
| **Most recalled** | Memory X recalled 47 times | Over-reliance → diversify or suppress |
| **Oldest active** | Memory Y is 6 months old, never promoted | Stale content → consolidate or forget |

---

## Adaptive Agent Behavior

An agent can use metamemory signals to self-optimize:

```mermaid
flowchart TD
    CHECK["Agent inspects memory health"] --> C1{"High episodic,<br/>low semantic?"}
    C1 -->|"Yes"| A1["Trigger consolidation<br/><i>'I should sleep on this'</i>"]

    CHECK --> C2{"One memory recalled<br/>47 times?"}
    C2 -->|"Yes"| A2["Diversify recall<br/><i>'I'm over-relying on this'</i>"]

    CHECK --> C3{"Low importance<br/>average?"}
    C3 -->|"Yes"| A3["Increase surprise sensitivity<br/><i>'Everything feels routine'</i>"]

    style A1 fill:#9b59b6,color:white
    style A2 fill:#f39c12,color:white
    style A3 fill:#e74c3c,color:white
```

### Example Scenarios

| Observation | Agent Response |
|---|---|
| 90% episodic, 2% semantic | "I should consolidate — trigger a reflect cycle" |
| One memory dominates all queries | "I'm over-relying on this — increase habituation rate" |
| Average importance is 0.2 | "Most memories are routine — lower the surprise threshold" |
| 500 memories tagged "error" | "I'm seeing too many errors — escalate to the user" |

---

## Next Steps

- :material-sync: [**Sync — Persistence & Replication**](sync.md) — WAL and CRDT merge
- :material-brain: [**Architecture**](architecture.md) — system overview
