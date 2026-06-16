---
title: "Amygdala — Emotional Valence"
description: "How Spector adds emotional coloring to memories — enabling agents to recall by mood, sentiment, and outcome quality."
---

# 😱 Amygdala — Emotional Valence

> **Biological Analog**: The **amygdala** is the brain's emotional processor. It assigns emotional significance to experiences — fear, joy, anger, relief — which profoundly influences how memories are encoded, stored, and retrieved. Emotionally charged memories are remembered more vividly and last longer.

---

## The Concept

Every memory in Spector carries a **valence score** — a single byte (`-128` to `+127`) representing its emotional coloring:

| Range | Meaning | Examples |
|---|---|---|
| `-128` to `-50` | **Strongly negative** | Critical errors, data loss, security breaches |
| `-50` to `-10` | **Mildly negative** | Warnings, slow performance, minor bugs |
| `-10` to `+10` | **Neutral** | Factual information, routine operations |
| `+10` to `+50` | **Mildly positive** | Successful deployments, optimizations |
| `+50` to `+127` | **Strongly positive** | Major breakthroughs, user praise, goals achieved |

---

## How It Works

The valence tracker computes emotional coloring from two signals:

```mermaid
flowchart TD
    TEXT["Memory text content"] --> SENTIMENT["Content-based sentiment<br/><i>keyword analysis</i>"]
    SOURCE["Memory source type"] --> BIAS["Source-based bias<br/><i>e.g., errors → negative</i>"]

    SENTIMENT --> FUSE["Fused valence score<br/><b>clamped to [-128, +127]</b>"]
    BIAS --> FUSE

    FUSE --> NEG["Negative valence<br/><i>errors, failures, warnings</i>"]
    FUSE --> ZERO["Neutral valence<br/><i>factual, routine</i>"]
    FUSE --> POS["Positive valence<br/><i>successes, breakthroughs</i>"]

    style NEG fill:#e74c3c,color:white
    style ZERO fill:#95a5a6,color:white
    style POS fill:#2ecc71,color:white
```

---

## Valence-Filtered Recall

The most powerful use of valence is in **recall filtering**. Agents can filter by emotional range to answer different types of questions:

### "What went wrong?" — Negative Memories

```
memory.recall("database connection",
    topK: 10,
    maxValence: -10,       // Only negative memories
    tags: ["database", "error"])
```

### "What worked well?" — Positive Memories

```
memory.recall("deployment strategy",
    topK: 5,
    minValence: +10,       // Only positive memories
    tags: ["deployment"])
```

### Full Emotional Range (Default)

By default, no valence filter is applied — the agent sees the full emotional spectrum. The valence still influences recall indirectly because the flashbulb policy pins emotionally intense memories at higher importance.

---

## Where It Fits in the Pipeline

Valence filtering happens at **Phase 3** of the 6-phase scorer — before the expensive SIMD vector math:

```mermaid
flowchart LR
    P1["Phase 1<br/>Tombstone"] --> P2["Phase 2<br/>Tag Gate"]
    P2 --> P3["Phase 3<br/><b>Valence Filter</b><br/><i>~2 cycles</i>"]
    P3 --> P4["Phase 4<br/>Importance"]
    P4 --> P5["Phase 5<br/>SIMD L2<br/><i>~200 cycles</i>"]
    P5 --> P6["Phase 6<br/>Fused Score"]

    style P3 fill:#e74c3c,color:white
    style P5 fill:#0984e3,color:white
```

**Cost**: 2 CPU cycles — a single byte read and two comparisons. Records outside the valence range are eliminated before Phase 5's ~200-cycle SIMD computation.

---

## Storage

Valence is stored in the 64-byte synaptic header as a single signed byte:

```
Offset 30: [1B valence] — signed byte [-128 to +127]
```

This costs exactly **1 byte per memory** — negligible overhead for a powerful filtering dimension.

---

## Next Steps

- :material-link: [**Hebbian — Association Learning**](hebbian.md) — "neurons that fire together wire together"
- :material-head-cog: [**Dopamine — Surprise Detection**](dopamine.md) — auto-importance scoring
- :material-lightning-bolt: [**6-Phase Scoring Pipeline**](scoring-pipeline.md) — where valence filtering happens
