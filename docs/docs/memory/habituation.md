---
title: "Habituation — Anti-Filter Bubble"
description: "How HabituationPenalty prevents repetitive recall by attenuating scores for frequently-returned memories."
---

# 😴 Habituation — Anti-Filter Bubble

> **Package**: `com.spectrayan.spector.memory.habituation`
>
> **Biological Analog**: **Habituation** is the simplest form of learning — a decrease in response to a stimulus after repeated presentations. You stop hearing the ticking clock after a few minutes. The brain allocates attention to *novel* stimuli, not repeated ones. This prevents sensory overload and enables adaptation.

---

## The Problem

Without habituation, an AI agent repeatedly recalls the same "most relevant" memories — creating a **filter bubble**. If memory A has the highest similarity score, it dominates every recall, crowding out potentially useful but slightly-less-similar memories.

```
Query 1: "database issues" → [A, B, C, D, E]     ← A dominates
Query 2: "database issues" → [A, B, C, D, E]     ← Same results!
Query 3: "database issues" → [A, B, C, D, E]     ← Filter bubble
```

### With Habituation

```
Query 1: "database issues" → [A, B, C, D, E]     ← Fresh results
Query 2: "database issues" → [B, C, A, D, F]     ← A drops, F emerges
Query 3: "database issues" → [C, F, B, G, D]     ← New memories surface
```

---

## HabituationPenalty

The `HabituationPenalty` tracks recall frequency per memory ID and computes a decay penalty:

```java
public final class HabituationPenalty {
    
    private final ConcurrentHashMap<String, Integer> recallCounts 
        = new ConcurrentHashMap<>();
    private final float decayRate;  // default: 0.85
    
    /**
     * Records a recall event and returns the habituation penalty.
     * First recall: 1.0 (no penalty). Each subsequent recall multiplies
     * the penalty by decayRate (default: 0.85).
     *
     * @param memoryId the memory being recalled
     * @return penalty multiplier [0.0 – 1.0]
     */
    public float recordAndComputePenalty(String memoryId) {
        int count = recallCounts.merge(memoryId, 1, Integer::sum);
        if (count <= 1) return 1.0f;  // First recall — no penalty
        return (float) Math.pow(decayRate, count - 1);
    }
    
    /**
     * Batch penalty computation for multiple IDs (P7 optimization).
     */
    public float[] batchPenalty(String[] ids) {
        float[] penalties = new float[ids.length];
        for (int i = 0; i < ids.length; i++) {
            penalties[i] = recordAndComputePenalty(ids[i]);
        }
        return penalties;
    }
}
```

### Penalty Curve

| Recall # | Penalty (rate=0.85) | Effect |
|---|---|---|
| 1st | 1.00 | Full score |
| 2nd | 0.85 | 15% reduction |
| 3rd | 0.72 | 28% reduction |
| 5th | 0.52 | Half score |
| 10th | 0.20 | 80% reduction |
| 20th | 0.04 | Nearly eliminated |

!!! info "Decay Rate Configuration"
    The default decay rate of 0.85 provides a balance between novelty and relevance. A higher rate (0.95) creates a gentler penalty — useful when the agent genuinely needs to recall the same memory frequently. A lower rate (0.70) aggressively surfaces new content.

---

## Integration with RecallPipeline

Habituation is applied at **Step 5** of the recall pipeline — after scoring but before final ranking:

```java
// In RecallPipeline.recall()

// Step 5: Apply habituation penalty (anti-filter-bubble)
for (int i = 0; i < allResults.size(); i++) {
    CognitiveResult r = allResults.get(i);
    float habPenalty = habituationPenalty.recordAndComputePenalty(r.id());
    if (habPenalty < 1.0f) {
        allResults.set(i, new CognitiveResult(
            r.id(), r.text(), r.score() * habPenalty, 
            r.importance(), r.ageDays(),
            r.recallCount(), r.valence(), r.memoryType(), r.source(),
            r.synapticTags(), r.decayFactor(), r.ltpAdjustedDecay()));
    }
}
```

**Key**: The penalty multiplies the `score()` field — it doesn't modify the underlying memory. Habituation is a recall-time effect, not a storage-time effect.

---

## Interaction with Other Systems

| System | Interaction |
|---|---|
| **Reconsolidation** | Habituation reduces recall score, but reconsolidation *increases* the memory's durability. A frequently-recalled memory resists temporal decay (fewer buckets) but gets a lower score on repeated queries. |
| **Surprise Detection** | New, surprising memories start with high importance and no habituation penalty — they naturally dominate initial queries. |
| **Suppression** | If a memory is fully suppressed, habituation is irrelevant — it's excluded at Step 4 before habituation is applied. |

---

## Next Steps

- :material-cancel: [**Inhibition — Suppression**](inhibition.md) — explicit memory blocking
- :material-link: [**Hebbian — Association Learning**](hebbian.md) — how co-activation creates associations
- :material-lightning-bolt: [**6-Phase Scoring Pipeline**](scoring-pipeline.md) — the full recall pipeline
