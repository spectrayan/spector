---
title: "Salience & Importance"
description: "How Spector computes, personalizes, and evolves memory importance — from dopamine-inspired surprise detection to persona-based modulation and enterprise salience profiles."
tags:
  - salience
  - importance
  - cognitive-memory
  - persona
---

# :material-star-shooting: Salience & Importance

> **TL;DR**: Every memory gets an importance score (0.05–10.0) computed by an adaptive surprise detector. Users personalize importance via **salience profiles** — declaring interests, disinterests, and a cognitive persona in natural language. Personas modulate how memories are emotionally colored, how strongly high-arousal events resist decay, and which topic dimensions are amplified. The system merges profiles hierarchically (tenant → agent → user) and can re-score all existing memories when preferences change.

---

## Overview

Importance is the single most influential signal in Spector's recall ranking. A memory with importance 8.0 will surface far more readily than one with importance 0.3, even if both are semantically similar to the query.

The importance system has four stages:

```mermaid
flowchart LR
    A["Stage 1\nNovelty Detection\nHow surprising is this?"] --> B["Stage 2\nSalience Boost\nDoes it match user interests?"]
    B --> P["Stage 2b\nPersona Modulation\nValence bias, arousal sensitivity"]
    P --> C["Stage 3\nOngoing Evolution\nRetrieval reinforcement,\ntemporal decay"]

    style A fill:#f39c12,color:white
    style B fill:#1a73e8,color:white
    style P fill:#9b59b6,color:white
    style C fill:#27ae60,color:white
```

---

## Stage 1: Novelty Detection

### The Dopamine Model

The **SurpriseDetector** is modeled after dopamine prediction error signaling in neuroscience:

!!! quote "The Biological Principle"
    The brain is a prediction engine. If you eat a normal breakfast, you forget it in an hour. If the toaster catches fire, a dopamine spike sears the event into your brain forever. Memory strength scales with **prediction error** — the gap between what was expected and what actually happened.

### How It Works

At ingestion time, Spector computes the **L2 distance** from the new memory's embedding to the nearest existing memory. This distance is scored against a running baseline using **Welford's online algorithm** for numerically stable mean/variance:

```
z-score = (distanceToNearest - μ) / σ
```

The z-score is mapped to importance via a **shifted sigmoid**:

$$\text{importance} = 0.05 + \sigma\!\left(1.2 \times (z - 1.0)\right) \times 9.95$$

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'fontSize': '14px'}}}%%
graph LR
    Z_NEG["z ≪ 0<br/>Very similar"] -->|"importance ≈ 0.05"| BORING["😐 Mundane"]
    Z_0["z ≈ 0<br/>Typical content"] -->|"importance ≈ 0.3"| NORMAL["📝 Normal"]
    Z_1["z ≈ 1.0<br/>Moderately novel"] -->|"importance ≈ 5.0"| INTERESTING["💡 Interesting"]
    Z_2["z ≈ 2.0<br/>Quite novel"] -->|"importance ≈ 8.0"| SURPRISING["⚡ Surprising"]
    Z_3["z ≫ 3.0<br/>Extreme outlier"] -->|"importance ≈ 10.0"| FLASHBULB["🔥 Flashbulb!"]

    style BORING fill:#95a5a6,color:white
    style NORMAL fill:#3498db,color:white
    style INTERESTING fill:#f39c12,color:white
    style SURPRISING fill:#e67e22,color:white
    style FLASHBULB fill:#e74c3c,color:white
```

!!! tip "Why a sigmoid instead of step function?"
    A step function (e.g., "z > 2 = important") collapses 95% of memories to the same value, making ranking meaningless. The continuous sigmoid produces **unique scores for every memory**, enabling fine-grained ranking.

!!! tip "Why z-scores instead of fixed thresholds?"
    Different embedding models produce vastly different distance ranges. `nomic-embed-text` (768-dim) has different L2 distributions than `all-MiniLM-L6-v2` (384-dim). Z-score normalization **adapts automatically** to any model.

### Dual Surprise: Spatial + Temporal

The V2 surprise detector adds **temporal novelty** — a recurrence after a long gap is itself surprising:

=== "First Occurrence"
    ```
    Memory: "Database crashed due to OOM"
    Spatial surprise: HIGH (never seen this before)
    Temporal surprise: N/A
    Combined importance: 7.5
    ```

=== "Same Day Recurrence"
    ```
    Memory: "Database crashed again"
    Spatial surprise: LOW (similar to recent memory)
    Temporal surprise: LOW (just saw this)
    Combined importance: 0.8
    ```

=== "6 Months Later"
    ```
    Memory: "Database crashed again"
    Spatial surprise: LOW (known topic)
    Temporal surprise: HIGH (6-month gap)
    Combined importance: 6.2
    ```

The dual signal uses configurable weights:

$$\text{combined}_z = \alpha \times \text{spatial}_z + \beta \times \text{temporal}_z$$

Default: α = 0.6 (spatial), β = 0.4 (temporal).

### Warmup Period

The surprise detector requires a minimum of **20 observations** before adaptive scoring activates. During warmup, all memories receive a default importance of 1.0 to prevent artificially extreme scores from an empty baseline.

### Flashbulb Memory

When the z-score exceeds the flashbulb threshold (default: 3.0), the memory receives special treatment:

- Importance set to **10.0** (maximum)
- **Pinned flag** set — exempt from temporal decay
- Routed to the **episodic tier** regardless of default routing
- Never a candidate for automatic consolidation or forgetting

!!! example "Use Case"
    An AI coding agent encounters `OutOfMemoryError` for the first time (z-score: 4.2). This triggers flashbulb encoding — the error memory is pinned at maximum importance and will always surface when the agent encounters memory-related issues.

---

## Stage 2: Salience Profiles

### What Is a Salience Profile?

A **SalienceProfile** declares what matters to an entity — a tenant, an agent, or a user. It modifies the raw importance score from Stage 1:

```java
var profile = SalienceProfile.builder()
    .interest("database performance", InterestLevel.CRITICAL)
    .interest("Kubernetes orchestration", InterestLevel.HIGH)
    .disinterest("meeting notes", InterestLevel.IGNORE)
    .icnuWeights(new IcnuWeights(0.40f, 0.10f, 0.40f, 0.10f))
    .alpha(0.5f)
    .beta(0.5f)
    .build();
```

### How Interests Work

Users express interests in **natural language**. The enterprise layer pre-computes embedding vectors when the profile is saved. At ingestion time, the engine computes **cosine similarity** between the memory embedding and each interest embedding:

```
Memory: "PostgreSQL query optimizer regression"

Interest: "database performance" (CRITICAL, multiplier=2.0)
  cosine("database performance", memory) = 0.82  → above threshold (0.5)
  boost = 2.0 × 0.82 = 1.64

Final importance = base_importance × 1.64
```

!!! success "Semantic, Not Keyword"
    "PostgreSQL query optimizer regression" matches "database performance" because their embedding vectors are close — **no keyword overlap needed**. This is fundamentally different from tag-based or keyword-based matching.

### Interest Levels

| Level | Multiplier | Effect |
|---|---|---|
| `CRITICAL` | 2.0× | Doubles importance of matching memories |
| `HIGH` | 1.5× | 50% boost |
| `NORMAL` | 1.0× | No change (neutral) |
| `LOW` | 0.5× | Halves importance (mild suppression) |
| `IGNORE` | 0.1× | Near-total suppression |

### Disinterests (Dampeners)

Disinterests work the same way but **reduce** importance:

```
Memory: "Notes from weekly standup meeting"

Disinterest: "meeting notes" (IGNORE, multiplier=0.1)
  cosine("meeting notes", memory) = 0.91  → above threshold
  dampen = 0.1 × 0.91 = 0.091

Final importance = base_importance × 0.091  → nearly suppressed
```

### Topic Boost Algorithm

```
boost = 1.0

for each interest:
    sim = cosine(memoryEmbedding, interest.embedding)
    if sim > similarityThreshold (default 0.5):
        boost = max(boost, level.multiplier × sim)

for each disinterest:
    sim = cosine(memoryEmbedding, disinterest.embedding)
    if sim > similarityThreshold:
        boost = min(boost, level.multiplier × sim)

return max(0.01, boost)   // floor prevents zero importance
```

Performance: O(N × dims) where N = number of interests. For 10 interests × 768 dims = 7,680 FLOPs — negligible.

---

## Hierarchical Merge

In multi-tenant deployments, salience profiles merge at three levels:

```mermaid
graph TB
    T["Tenant Profile<br/><i>Org-wide defaults (authoritative)</i>"] --> A["Agent Profile<br/><i>Per-agent specialization (additive)</i>"]
    A --> U["User Profile<br/><i>Individual preferences (additive)</i>"]
    U --> E["Effective Profile<br/><i>Used for ingestion + recall</i>"]

    style T fill:#e74c3c,color:white
    style A fill:#f39c12,color:white
    style U fill:#1a73e8,color:white
    style E fill:#27ae60,color:white
```

### Override Policies

The tenant controls what agents and users can customize:

| Policy | Topic Boosts | Topic Dampeners | ICNU Weights | α/β Scoring | Flashbulb |
|---|---|---|---|---|---|
| `TENANT_ONLY` | :material-close: | :material-close: | :material-close: | :material-close: | :material-close: |
| `ADDITIVE_TOPICS` | :material-check: add | :material-check: add | :material-close: | :material-close: | :material-close: |
| `FULL_OVERRIDE` | :material-check: all | :material-check: all | :material-check: | :material-check: | :material-check: |

### Merge Rules

- **Topics**: UNION semantics — child adds new topics. On conflict (same topic string), **tenant wins**.
- **ICNU weights**: Child replaces parent when policy allows. Otherwise tenant's weights are locked.
- **α/β scoring**: Child replaces parent when allowed.
- **Flashbulb threshold**: `MIN(tenant, child)` when allowed — the most sensitive setting wins.
- **Similarity threshold**: `MAX(tenant, child)` — the most restrictive wins.

### Example

=== "Tenant Profile (hospital-a)"
    ```yaml
    interests:
      - "patient safety" → CRITICAL
      - "HIPAA compliance" → HIGH
    icnuWeights: I=0.30, C=0.20, N=0.30, U=0.20
    flashbulbThreshold: 2.5
    policy: ADDITIVE_TOPICS
    ```

=== "Agent Profile (medication-checker)"
    ```yaml
    interests:
      - "drug interactions" → CRITICAL     # ✅ ADDED (new topic)
      - "patient safety" → HIGH            # ❌ BLOCKED (tenant wins)
    ```

=== "User Profile (dr-smith)"
    ```yaml
    interests:
      - "cardiology" → HIGH                # ✅ ADDED
    disinterests:
      - "administrative tasks" → LOW       # ✅ ADDED
    ```

=== "Effective (Merged)"
    ```yaml
    interests:
      - "patient safety" → CRITICAL        # from tenant (authoritative)
      - "HIPAA compliance" → HIGH          # from tenant
      - "drug interactions" → CRITICAL     # from agent (additive)
      - "cardiology" → HIGH               # from user (additive)
    disinterests:
      - "administrative tasks" → LOW       # from user
    icnuWeights: I=0.30, C=0.20, N=0.30, U=0.20  # from tenant (locked)
    flashbulbThreshold: 2.5                         # from tenant (locked)
    ```

---

## Stage 2b: Persona-Based Modulation

Beyond topic interests, salience profiles can include a **cognitive persona** — a set of traits that modulate how *all* memories are processed, not just topic-matched ones.

### What Is a Persona?

A persona defines the cognitive disposition of an agent or user. Think of it as the agent's "personality" for memory processing:

```mermaid
graph TB
    PERSONA["Cognitive Persona"] --> VB["Valence Bias\n(optimistic ↔ pessimistic)"]
    PERSONA --> AS["Arousal Sensitivity\n(calm ↔ reactive)"]
    PERSONA --> SR["Self-Relevance Boost\n(detached ↔ ego-centric)"]

    VB --> EFFECT1["Shifts baseline emotional\ncoloring of all memories"]
    AS --> EFFECT2["Modulates how strongly\nhigh-arousal events resist decay"]
    SR --> EFFECT3["Boosts memories containing\nentities matching the persona's identity"]

    style PERSONA fill:#9b59b6,color:white
    style EFFECT1 fill:#27ae60,color:white
    style EFFECT2 fill:#f39c12,color:white
    style EFFECT3 fill:#1a73e8,color:white
```

### Valence Bias

Valence bias shifts the emotional baseline for importance calculation:

| Bias | Range | Effect |
|---|---|---|
| **Optimistic** | +20 to +80 | Positive memories get higher importance; negative memories are dampened |
| **Neutral** | 0 | No bias (default) |
| **Pessimistic** | -80 to -20 | Negative memories (errors, threats, failures) get higher importance |

**Example**: A security-focused agent with pessimistic valence bias (−60) will naturally amplify threat memories and dampen success stories — the cognitive equivalent of a security auditor's professional paranoia.

### Arousal Sensitivity

Arousal sensitivity controls how strongly emotional intensity affects decay resistance:

- **High sensitivity** (1.5×–2.0×): High-arousal events (z-score outliers, flashbulb memories) resist decay far more strongly. Even moderately arousing events get elevated persistence.
- **Normal sensitivity** (1.0×): Default behavior — arousal affects decay resistance according to the standard Amygdala model.
- **Low sensitivity** (0.5×–0.8×): Emotionally intense events decay at nearly the same rate as neutral ones. Useful for analytical agents that should weight all data equally.

!!! example "Practical Impact"
    A customer support agent with **high arousal sensitivity** remembers escalated complaints and angry customer interactions far longer than routine tickets. A data analytics agent with **low arousal sensitivity** treats all data points with equal temporal persistence.

### Self-Relevance Boost

When the persona has an identity (name, role, domain), memories containing entities that match the persona's identity receive an additional importance boost. This models the psychological **self-reference effect** — people remember information better when it relates to themselves.

| Self-Relevance | Boost | Use Case |
|---|---|---|
| **Strong** | 1.5×–2.0× | Personal assistant that strongly prioritizes user-specific memories |
| **Moderate** | 1.2× | Default — mild preference for self-relevant information |
| **None** | 1.0× | Analytical agent — treats all entities equally |

### Persona in the Ingestion Pipeline

Persona modulation occurs after topic boosting (Stage 2) and before the flashbulb decision:

```mermaid
flowchart LR
    BASE["Raw importance\n(from surprise detector)"] --> TOPIC["Topic boost\n(interest matching)"]
    TOPIC --> VALENCE["Valence bias\n(shift emotional baseline)"]
    VALENCE --> AROUSAL["Arousal sensitivity\n(modulate decay resistance)"]
    AROUSAL --> SELF["Self-relevance\n(entity identity boost)"]
    SELF --> FINAL["Final importance\n(written to header)"]

    style BASE fill:#95a5a6,color:white
    style TOPIC fill:#1a73e8,color:white
    style VALENCE fill:#9b59b6,color:white
    style AROUSAL fill:#f39c12,color:white
    style SELF fill:#e74c3c,color:white
    style FINAL fill:#27ae60,color:white
```

### Configuring a Persona

Personas are defined as part of the salience profile:

```json
{
  "interests": ["database performance", "security"],
  "persona": {
    "name": "Security Auditor",
    "valenceBias": -40,
    "arousalSensitivity": 1.8,
    "selfRelevanceBoost": 1.2,
    "identity": ["security", "compliance", "audit"]
  }
}
```

Personas merge with the same hierarchical rules as topic interests — tenant persona traits are authoritative, agent/user personas are additive when allowed by the override policy.

---

## Stage 3: Ongoing Evolution

Importance is not static after ingestion:

### Retrieval Reinforcement
Every time a memory is recalled, its importance gets a small boost — **Hebbian "fire together, wire together."** Frequently useful memories become progressively easier to surface.

### Temporal Decay
Memories that are never recalled gradually lose importance during sleep consolidation cycles. This prevents the memory store from being dominated by old, unused memories.

### Co-activation Strengthening
Memories frequently retrieved together form **Hebbian associations** in the 4-layer cognitive graph. These associations create retrieval clusters — recalling one memory pulls related memories along with it.

---

## ICNU Fusion Weights

The final recall score combines four signals:

| Signal | Letter | Default | What It Measures |
|---|---|---|---|
| **I**mportance | I | 0.25 | Novelty/surprise from ingestion (this system) |
| **C**o-activation | C | 0.25 | Hebbian association strength |
| **N**ovelty | N | 0.25 | Freshness — how recently created |
| **U**rgency | U | 0.25 | User-specified priority flags |

Users override these via their salience profile:

```java
// A user who cares mostly about recency and importance:
.icnuWeights(new IcnuWeights(0.40f, 0.10f, 0.40f, 0.10f))
```

---

## Re-scoring Strategies

When a salience profile changes, existing memories may have stale importance scores. Three strategies handle this:

| Strategy | Behavior | Cost | Use Case |
|---|---|---|---|
| `RECALL_ONLY` | No re-score — new profile applies at recall time | Zero | Temporary experiments |
| `LAZY` | Re-score each memory on next access | Amortized | Gradual preference shifts |
| `BACKGROUND` | Full re-score in background thread | O(N) | Major preference changes |

Background re-scoring iterates all memories, dequantizes their vectors, recomputes `computeTopicBoost()`, and writes the updated importance back to the synaptic header. Multiple concurrent requests are coalesced — only one background re-score runs at a time.

---

## API Reference

### Salience Management Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/api/v1/salience/profile` | `GET` | Get effective merged profile |
| `/api/v1/salience/profile` | `PUT` | Update profile + trigger re-score |
| `/api/v1/salience/profile` | `DELETE` | Reset to neutral |
| `/api/v1/salience/rescore` | `POST` | Trigger manual background re-score |
| `/api/v1/salience/status` | `GET` | Check re-score progress |

### MCP Tools

Salience can also be managed via the MCP protocol:

```json
{
  "method": "tools/call",
  "params": {
    "name": "memory_compute_importance",
    "arguments": { "text": "PostgreSQL query optimizer" }
  }
}
```

---

## Full Ingestion Pipeline

Here's where importance fits in the complete ingestion flow, including persona modulation:

```mermaid
flowchart TD
    TEXT["1. Receive text"] --> EMBED["2. Embed → vector"]
    EMBED --> ENCRYPT["3. Encrypt text (AES-256-GCM)"]
    ENCRYPT --> TAGS["4. Encode tags (HMAC blind index)"]
    TAGS --> NEAREST["5. Find nearest memory (L2 distance)"]
    NEAREST --> SURPRISE["6. Novelty Detection\nz-score → raw importance"]
    SURPRISE --> SALIENCE["7. Salience Profile\ntopicBoost × raw importance"]
    SALIENCE --> PERSONA["8. Persona Modulation\nvalence bias + arousal sensitivity"]
    PERSONA --> FLASH{"9. z ≥ flashbulb\nthreshold?"}
    FLASH -->|Yes| PIN["10a. Pin as flashbulb\nimportance = 10.0"]
    FLASH -->|No| WRITE["10b. Write to tier"]
    PIN --> WAL["11. Encrypt + append WAL"]
    WRITE --> WAL

    style SURPRISE fill:#f39c12,color:white
    style SALIENCE fill:#1a73e8,color:white
    style PERSONA fill:#9b59b6,color:white
    style PIN fill:#e74c3c,color:white
```

---

## Next Steps

- :material-flash: [**Dopamine — Surprise Detection**](dopamine.md) — the biological model in detail
- :material-chart-bar: [**Importance Fusion (ICNU)**](importance-fusion.md) — the four-signal fusion
- :material-sleep: [**Hippocampus — Sleep Consolidation**](hippocampus.md) — how importance decays
- :material-shield-lock: [**Encryption at Rest**](../architecture/encryption-at-rest.md) — how encrypted data interacts with importance
- :material-brain: [**Cognitive Profiles**](cognitive-profiles.md) — how profiles interact with importance
- :material-bell-ring: [**Event Notifications**](../architecture/event-notifications.md) — how importance changes trigger events
