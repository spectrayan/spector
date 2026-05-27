---
title: "Metamemory — Self-Reflection"
description: "MemoryIntrospector provides self-reflective analytics — the agent's ability to reason about its own memory health."
---

# 🪞 Metamemory — Self-Reflection

> **Package**: `com.spectrayan.spector.memory.metamemory`
>
> **Biological Analog**: **Metamemory** is the awareness of one's own memory processes — "I know I'm forgetting things more often" or "I'm confident I remember this correctly." It's what enables humans to say "I need to write this down" or "Let me double-check that."

---

## MemoryIntrospector

The `MemoryIntrospector` provides analytics and health metrics for the memory system:

```java
public final class MemoryIntrospector {

    /**
     * Returns per-tier memory counts.
     */
    public Map<MemoryType, Integer> countsByTier() { ... }

    /**
     * Returns the most common synaptic tags across all memories.
     * Useful for understanding what topics dominate the agent's memory.
     */
    public Map<String, Integer> tagDistribution() { ... }

    /**
     * Returns importance distribution statistics.
     */
    public DoubleSummaryStatistics importanceStats() { ... }

    /**
     * Returns memories with the highest recall counts
     * (most frequently accessed — potential habituation candidates).
     */
    public List<CognitiveResult> mostRecalled(int topK) { ... }

    /**
     * Returns the oldest active memories (potential consolidation candidates).
     */
    public List<CognitiveResult> oldestActive(int topK) { ... }
}
```

---

## Use Cases

### Memory Health Dashboard

```java
var introspector = memory.introspect();

// Tier distribution — is memory balanced?
introspector.countsByTier().forEach((tier, count) ->
    System.out.printf("  %s: %d memories%n", tier, count));

// Tag distribution — what topics dominate?
introspector.tagDistribution().entrySet().stream()
    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
    .limit(10)
    .forEach(e -> System.out.printf("  %s: %d occurrences%n", e.getKey(), e.getValue()));
```

### Adaptive Agent Behavior

An agent can use metamemory to self-optimize:

- **High episodic count, low semantic**: "I should consolidate — trigger a reflect cycle"
- **High recall count on one memory**: "I'm over-relying on this — diversify"
- **Low importance average**: "Most memories are routine — increase surprise sensitivity"

---

## Next Steps

- :material-sync: [**Sync — Persistence & Replication**](sync.md) — WAL and CRDT merge
- :material-brain: [**Architecture**](architecture.md) — system overview
