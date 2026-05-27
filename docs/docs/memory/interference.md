---
title: "Interference — Deduplication"
description: "SemanticDeduplicator detects near-duplicate memories and merges them to prevent proactive interference."
---

# 🔀 Interference — Deduplication

> **Package**: `com.spectrayan.spector.memory.interference`
>
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

## SemanticDeduplicator

The `SemanticDeduplicator` detects near-duplicates by computing L2 distance between the new memory's vector and existing memories. When a match is found within a configurable threshold, it **merges** rather than creating a new record:

```java
public final class SemanticDeduplicator {

    /**
     * Checks if a near-duplicate exists and merges if found.
     * 
     * Merge strategy:
     * - importance = max(existing, new)
     * - synapticTags = existing | new  (OR-merge: union of Bloom filters)
     * - timestamp = most recent
     * - recallCount preserved from existing
     */
    public Optional<Long> findAndMerge(MemorySegment segment, int recordCount,
                                        CognitiveRecordLayout layout,
                                        float[] newVector, CognitiveHeader newHeader,
                                        float threshold) {
        // Scan for near-duplicate within L2 threshold
        // If found: merge headers via OR on tags, max on importance
        // Return offset of merged record (or empty if no duplicate)
    }
}
```

**Merge rules**:

| Field | Strategy | Rationale |
|---|---|---|
| `importance` | `max(existing, new)` | Keep the highest importance signal |
| `synapticTags` | `existing \| new` | Union of Bloom filters — broader context |
| `timestamp` | Most recent | Memory is "refreshed" |
| `recallCount` | Preserved | Reconsolidation history maintained |
| `valence` | From newer | Most recent emotional assessment |

---

## Integration

Deduplication runs during the ingestion pipeline **after embedding but before writing**. If a merge occurs, no new record is created — the existing record is updated in-place.

---

## Next Steps

- :material-clock: [**Prospective — Future Intents**](prospective.md) — time-triggered reminders
- :material-brain: [**Architecture**](architecture.md) — where deduplication fits in the pipeline
