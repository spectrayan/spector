---
title: "Self-Tuning Retrieval — ProfileAdaptor"
description: "Spector's self-tuning retrieval system: a contextual bandit that automatically selects the best performing Cognitive Profile for each task context based on user reinforcement."
---

# Self-Tuning Retrieval (ProfileAdaptor)

Just as the biological brain unconsciously adjusts its retrieval characteristics based on task context and outcomes (e.g., sharpening focus under stress, or widening associative scope during brainstorming), Spector can automatically tune its retrieval strategy using the **ProfileAdaptor**.

By observing which retrieval results lead to successful agent actions, the self-tuning system learns the optimal [Cognitive Profile](cognitive-profiles.md) to apply for different search contexts.

---

## How It Works

The self-tuning retrieval system frames cognitive profile selection as a **Contextual Multi-Armed Bandit** problem, where:

1. **Context** is defined by the set of search tags specified in the query's synaptic filter.
2. **Arms** are the available [Cognitive Profiles](cognitive-profiles.md) (e.g., `BALANCED`, `HYPERFOCUS`, `DIVERGENT`, `DEBUGGING`).
3. **Reward** is derived from reinforcement feedback (positive or negative signals) sent by the calling application.

```mermaid
flowchart TD
    Q["Query: 'database lock' (tags: database, sql)"] --> Hash["Hash tags to define Context"]
    Hash --> Choice{"Has context met<br/>min signals (10)?"}
    
    Choice -->|No| Default["Use default profile<br/>(e.g., BALANCED)"]
    Choice -->|Yes| Strategy{"Epsilon-Greedy Choice<br/>(epsilon = 10%)"}
    
    Strategy -->|Exploit (90%)| Best["Select profile with<br/>highest positive EMA"]
    Strategy -->|Explore (10%)| Random["Select random profile<br/>to discover performance"]
    
    Default --> Recall["Execute Recall Pipeline"]
    Best --> Recall
    Random --> Recall
    
    Recall --> Action["Agent performs action<br/>using retrieved memories"]
    Action --> Feedback["User/System reinforce:<br/>memory.reinforce(..., positive=true)"]
    Feedback --> Update["Update profile's EMA positive rate<br/>(alpha = 0.15)"]
```

### 1. Context Hashing
When a query is received with `profile=auto`, the system extracts the search tags and sorts them alphabetically to ensure determinism. It hashes the tag list to produce a unique context key. Similar queries searching the same topic share this context and pool their learning.

### 2. Epsilon-Greedy Selection
To balance utilizing known successful profiles (exploitation) with discovering potentially better ones (exploration), the system uses an **Epsilon-Greedy** strategy with $\epsilon = 10\%$:
* **Exploitation (90% of the time):** The system selects the profile that currently has the highest Exponential Moving Average (EMA) positive rate for this context.
* **Exploration (10% of the time):** The system selects a random profile to gather new data.

### 3. Cold Start Fallback
Before the system has sufficient data to make statistical decisions, it uses a fallback mechanism:
* If a context hash has fewer than **10 reinforcement signals**, the system falls back to the default profile configured in the `SalienceProfile`, and finally to `BALANCED`.

### 4. Reinforcement Learning
When the calling application reinforces the outcome of a memory recall (such as an agent successfully completing a task using retrieved context), it credits the profile that retrieved that memory. 

The positive rate for the chosen profile is updated using an Exponential Moving Average:

$$
\text{EMA}_{t+1} = \text{EMA}_t \cdot (1 - \alpha) + \text{reward} \cdot \alpha
$$

Where:
* $\alpha = 0.15$ is the learning rate blending factor.
* $\text{reward} = 1.0$ for positive reinforcement, and $0.0$ for negative reinforcement.

---

## In Practice

### 1. Enabling Self-Tuning Retrieval

To activate the self-tuning adaptor, set the profile to `"auto"` in your recall options.

#### Via Java SDK
```java
var options = RecallOptions.builder()
    .autoProfile(true) // Automatically selects the best profile
    .build();

var results = memory.recall("database deadlock", options);
```

#### Via MCP Tool
```json
{
  "name": "memory_recall",
  "arguments": {
    "query": "database deadlock",
    "profile": "auto"
  }
}
```

### 2. Reinforcing Outcomes
When an action succeeds or fails, send feedback to reinforce the chosen retrieval path. Spector automatically tracks which profile was used to retrieve each memory and updates the contextual bandit statistics accordingly.

#### Via Java SDK
```java
// If the retrieved memory successfully resolved the issue
memory.reinforce(memoryId, true); // positive reinforcement

// If the retrieved memory was irrelevant or caused an error
memory.reinforce(memoryId, false); // negative reinforcement
```

---

## Persistence & Performance

* **Durable Learning:** Learned bandit statistics are snapshotted to the binary coactivation tracker file (`coactivation.tracker`) on engine shutdown and reloaded on startup.
* **Isolation:** Reinforcement statistics are isolated per memory space, ensuring that different agents do not pollute each other's learned retrieval preferences.
* **Performance:** Hashing and stats lookup are implemented using high-performance concurrent collections, completing in sub-microsecond time and adding zero overhead to the recall path.

---

## Key Takeaways

* **Zero-Configuration Optimization:** Agents don't need manual profile tuning; the system adapts to their workflows over time.
* **Exploration/Exploitation Balance:** Epsilon-greedy selection ensures the system continuously discovers optimal retrieval paths without getting stuck in local optima.
* **Resilient Failover:** Cold-start thresholds guarantee that General-Purpose `BALANCED` retrieval is used until reliable contextual statistics are collected.
