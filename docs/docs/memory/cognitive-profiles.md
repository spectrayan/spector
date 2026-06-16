---
title: "Cognitive Profiles — Adaptive Retrieval Modes"
description: "Cognitive profile system: BALANCED, DEBUGGING, HYPERFOCUS, DIVERGENT, CRITICAL, and other retrieval modes that adapt scoring to different cognitive tasks."
---

# Cognitive Profiles

Cognitive profiles are **pre-configured scoring presets** that modulate how the memory system prioritizes, retrieves, and consolidates information. They act as a thalamic filter — adjusting the balance between similarity-driven and importance-driven recall to match different task contexts.

## How Profiles Work

Every recall query is scored using the **fused cognitive score** formula:

$$
\text{score} = \alpha \cdot \text{similarity} + \beta \cdot \text{importance} \cdot \text{decay}
$$

Where:

- **α (alpha)** — Weight on vector similarity (how close is this memory to the query?)
- **β (beta)** — Weight on learned importance (how important was this memory at ingestion?)
- **α + β = 1.0** — Always normalized

A profile sets α, β, and optional modifiers (hyperfocus boost, lateral mode, episode pinning) to bias the scoring pipeline for a specific cognitive strategy.

## Built-in Profiles

### Standard Profiles

| Profile | α | β | Valence Filter | Best For |
|:---|:---:|:---:|:---:|:---|
| `BALANCED` | 0.6 | 0.4 | All | General-purpose recall |
| `EXPLORING` | 0.8 | 0.2 | All | Broad discovery, creative exploration |
| `DEBUGGING` | 0.3 | 0.7 | Negative only (≤ -10) | Precise error-matching, diagnostic search |
| `RECALLING` | 0.4 | 0.6 | Positive only (≥ +10) | Retrieving proven solutions and successes |
| `CRITICAL` | 0.2 | 0.8 | All | Security audits, compliance checks, high-stakes |

### Advanced Profiles — Neurodivergent

These profiles go beyond α/β tuning — they activate specialized scoring mechanics in the [6-Phase Pipeline](scoring-pipeline.md) and model specific neurocognitive patterns.

| Profile | α | β | Biological Analog | Special Mechanics |
|:---|:---:|:---:|:---|:---|
| `HYPERFOCUS` | 1.0 | 0.0 | Monotropism | [Focus Mode](focus-mode.md) — Zero decay, strict tag gate, boost multiplier |
| `SYSTEMATIZER` | 0.3 | 0.7 | Bottom-up processing (autism) | [Systemizer](focus-mode.md#systemizer) — Pins source episodes during consolidation |
| `DIVERGENT` | 0.8 | 0.2 | Reduced Latent Inhibition (ADHD) | [Explorer](lateral-retrieval.md) — Lateral cross-domain retrieval |
| `PARANOID_SENTINEL` | 0.2 | 0.8 | Amygdala threat-detection | Negative-only valence, mood-congruent threat recall |
| `THE_EXECUTOR` | 0.3 | 0.7 | Prefrontal executive function | Heaviside Cliff (strictness=10.0), no lateral retrieval |
| `HIGHLY_SENSITIVE` | 0.7 | 0.3 | Sensory Processing Sensitivity | Low flashbulb threshold, strong lateral inhibition |
| `DEFAULT_MODE_NETWORK` | 0.2 | 0.8 | Brain's resting state network | Skips Working + Episodic, Semantic + Procedural only |

---

## New Profile Deep Dives

### PARANOID_SENTINEL — Amygdala Threat Detection

**Biological analog:** The amygdala's threat-detection circuitry, which filters sensory input for potential dangers and amplifies recall of negative experiences (mood-congruent memory bias).

**Use case:** SRE agents, security auditors, compliance monitors. Only surfaces memories associated with negative outcomes — errors, failures, security incidents, regressions.

| Parameter | Value | Effect |
|:---|:---:|:---|
| α | 0.2 | Low similarity weight — severity matters more than closeness |
| β | 0.8 | High importance weight — prioritize severe failures |
| Valence range | [-128, -1] | **Negative memories only** — successes are invisible |

**How it works:**

- Only negative memories pass the valence filter in Phase 3 of the scorer. Successes, neutral logs, and positive outcomes are invisible.
- Importance-dominated — the severity of the past failure matters more than how closely it matches the current query.
- Query valence is set to -128 (maximum threat), triggering mood-congruent recall amplification.

!!! example "Scenario"
    Agent query: "deployment configuration" → BALANCED returns general config docs. PARANOID_SENTINEL returns only the config-related incidents: the time a bad config caused a 4-hour outage, the security CVE from an exposed config file, the memory leak from misconfigured thread pool.

### THE_EXECUTOR — Prefrontal Executive Function

**Biological analog:** The prefrontal cortex in full executive function mode — goal-directed, no tangential exploration, pure task completion.

**Use case:** Devin-style agentic task runners. Combined with Zeigarnik Effect (`markUnresolved()`) for tracking open tasks that resist decay.

| Parameter | Value | Effect |
|:---|:---:|:---|
| α | 0.3 | Moderate similarity weight |
| β | 0.7 | High importance weight |
| Strictness coefficient | 10.0 | Heaviside Cliff — 95% of candidates score near zero |
| Lateral mode | disabled | No cross-domain exploration |

**How it works:**

- **Heaviside Cliff scoring:** The strictness coefficient reshapes the similarity curve into a cliff function:

$$
\text{similarity} = \frac{1}{1 + d_{L2} \times 10.0}
$$

At strictness=1.0 (default), this is a gentle hyperbola. At strictness=10.0, it's a **cliff** — 95% of candidates score near zero, and only the closest matches survive.

- **Lateral retrieval disabled:** No DIVERGENT-style cross-domain exploration. Results must be directly relevant.
- **Zeigarnik integration:** Unresolved tasks (flagged via `markUnresolved()`) resist time-decay entirely — their decay bucket is clamped to 0.

### HIGHLY_SENSITIVE — Sensory Processing Sensitivity

**Biological analog:** Enhanced sensory processing depth (Aron & Aron, 1997). The highly sensitive brain processes stimuli more deeply, captures finer environmental details, and has a lower threshold for emotional activation.

| Parameter | Value | Effect |
|:---|:---:|:---|
| α | 0.7 | High similarity weight — capture nuanced matches |
| β | 0.3 | Lower importance weight |
| Flashbulb threshold | 2.0 (default: 3.0) | Pins more moments as permanent memories |
| Inhibition floor | 0.3 | Stronger lateral inhibition — memories stay distinct |
| Min importance | 0.01 | Nothing is too small to remember |

**How it works:**

- **Lower flashbulb threshold (2.0 vs 3.0):** Captures more "important" moments as flashbulb memories. Events that BALANCED would consider routine, HIGHLY_SENSITIVE pins permanently.
- **Stronger lateral inhibition (0.3 floor):** Less interference between memories. Each memory maintains its distinctiveness rather than blurring with similar neighbors.
- **minImportance=0.01:** Nothing is too small to remember. Subtle signals that other profiles would round down to zero are preserved.

!!! tip "Ideal for"
    Medical reasoning, quality assurance, code review, accessibility testing — anywhere subtle signals could be critical.

### DEFAULT_MODE_NETWORK — "Shower Thoughts"

**Biological analog:** The brain's default mode network (DMN), which activates during rest, mind-wandering, and unfocused cognition. The DMN surfaces deep, consolidated knowledge rather than recent events.

| Parameter | Value | Effect |
|:---|:---:|:---|
| α | 0.2 | Low similarity weight |
| β | 0.8 | High importance weight — deep knowledge |
| Searched tiers | Semantic + Procedural only | Skips Working + Episodic |

**How it works:**

- **Skips Working and Episodic tiers entirely.** Only Semantic (consolidated facts) and Procedural (learned procedures) are searched.
- **α=0.2, β=0.8:** Importance-dominated. The DMN isn't looking for direct matches — it surfaces whatever the agent "knows deeply" about a topic.
- **No recency bias:** Since Episodic is skipped, all results are from long-term consolidated memory. No "what happened today" noise.

!!! example "Scenario"
    Agent is stuck on a performance problem → switches to DEFAULT_MODE_NETWORK → surfaces a deep architectural principle from 3 months ago that reframes the problem entirely. This is the computational equivalent of "sleeping on it."

---

## Usage

### Via Profile Preset

```
memory.recall("database deadlock", profile: HYPERFOCUS)
```

### Via Recall Options

```
memory.recall("performance optimization",
    profile: DIVERGENT,
    topK: 20,
    lateralDistanceThreshold: 1.5)
```

### Via MCP Tool

```json
{
  "name": "memory_recall",
  "arguments": {
    "query": "database deadlock",
    "profile": "HYPERFOCUS",
    "top_k": 10
  }
}
```

---

## Profile Selection Guide

```mermaid
flowchart TD
    A["What is the agent doing?"] --> B{"Focused on\none topic?"}
    B -->|Yes| C{"Need encyclopedic\ndetail?"}
    C -->|Yes| D["SYSTEMATIZER"]
    C -->|No| E["HYPERFOCUS"]
    B -->|No| F{"Exploring new\nterritory?"}
    F -->|Yes| G{"Want cross-domain\ninsights?"}
    G -->|Yes| H["DIVERGENT"]
    G -->|No| I["EXPLORING"]
    F -->|No| J{"Task execution\nor debugging?"}
    J -->|"Executing tasks"| J2["THE_EXECUTOR"]
    J -->|"Debugging"| K["DEBUGGING"]
    J -->|"Threat hunting"| M["PARANOID_SENTINEL"]
    J -->|"Need deep insight"| N["DEFAULT_MODE_NETWORK"]
    J -->|"Detail-sensitive"| O["HIGHLY_SENSITIVE"]
    J -->|No| L["BALANCED"]
```

---

## Agent Self-Extension

Agents can dynamically switch profiles during a conversation:

1. **Start with `BALANCED`** for general context
2. **Switch to `HYPERFOCUS`** when a specific topic is identified (e.g., user mentions "database deadlock")
3. **Switch to `DIVERGENT`** when stuck — lateral results may surface unexpected solutions
4. **Switch to `SYSTEMATIZER`** when building a comprehensive knowledge base

The hyperfocus system supports TTL-based activation with agent self-extension:

```mermaid
flowchart LR
    DETECT["Agent detects<br/>focused topic"] --> ACTIVATE["Activate hyperfocus<br/><i>tags: database, deadlock</i>"]
    ACTIVATE --> BOOST["Matching memories<br/>get boost multiplier"]
    BOOST --> CHECK{"Topic continues?"}
    CHECK -->|"Yes"| EXTEND["Extend TTL"]
    CHECK -->|"No — TTL expires"| DEACTIVATE["Auto-deactivate<br/><i>default: 30 min</i>"]

    style ACTIVATE fill:#e74c3c,color:white
    style DEACTIVATE fill:#95a5a6,color:white
```

---

## Result Metadata

Each result carries a retrieval mode indicating how it was retrieved:

| Mode | Meaning |
|:---|:---|
| `STANDARD` | Normal similarity + importance scoring |
| `LATERAL` | Cross-domain retrieval via the Explorer dual-heap |
| `HYPERFOCUS` | Tag-matched with zero decay and boost multiplier |

Agents can use this metadata to adjust their reasoning — for example, treating `LATERAL` results with more caution, or presenting `HYPERFOCUS` results with higher confidence.

## What's Next

- [Focus Mode](focus-mode.md) — Deep dive on HYPERFOCUS and SYSTEMATIZER
- [Explorer — Lateral Retrieval](lateral-retrieval.md) — Cross-domain dual-heap mechanics
- [Importance Fusion (ICNU)](importance-fusion.md) — Sigmoid-gated importance with dopaminergic I×N interaction
- [Synapse — Tags & Scoring](synapse.md) — Versioned header layouts (V1/V2/V3) and arousal-modulated decay
- [Hebbian — Association Learning](hebbian.md) — STDP with directed causal edges
- [Labs — Research Roadmap](../labs/roadmap.md) — Neuromodulatory Gain, Executive Dysfunction Profile
