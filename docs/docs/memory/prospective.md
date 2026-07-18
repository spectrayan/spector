---
title: "Prospective — Future Intents"
description: "Time-triggered memory reminders — the agent's ability to remember to do something in the future."
---

# 🔮 Prospective — Future Intents

> **Biological Analog**: **Prospective memory** is the ability to remember to perform an intended action in the future — "Remember to call the doctor at 3pm." Unlike retrospective memory (recalling the past), prospective memory is future-oriented and time-triggered.

---

## The Concept

An AI agent needs to remember not just *what happened*, but *what to do next*. Prospective memory enables:

- "Remind me to check the build in 10 minutes"
- "Flag this issue for follow-up tomorrow"
- "Alert when deployment completes"

---

## How It Works

```mermaid
flowchart TD
    SCHEDULE["Agent schedules reminder<br/><i>text + trigger time + tags</i>"] --> STORE["Stored in reminder queue<br/><i>sorted by trigger time</i>"]
    STORE --> WAIT["Waiting..."]
    WAIT --> CHECK{"Trigger time passed?"}
    CHECK -->|"Not yet"| WAIT
    CHECK -->|"Yes — due!"| INJECT["Inject into recall results<br/><b>with maximum score (10.0)</b>"]
    INJECT --> TOP["Appears at top<br/>of next recall"]

    style SCHEDULE fill:#4a90d9,color:white
    style INJECT fill:#e74c3c,color:white
    style TOP fill:#00b894,color:white
```

### Reminder Lifecycle

```mermaid
stateDiagram-v2
    [*] --> Scheduled: Agent calls schedule()
    Scheduled --> Due: Trigger time passes
    Due --> Delivered: Next recall() collects it
    Delivered --> [*]: Removed from queue

    note right of Scheduled: Waiting for trigger time
    note right of Due: Ready for collection
    note right of Delivered: Injected at top of results
```

Each reminder carries:

| Field | Description |
|---|---|
| **text** | The reminder content |
| **triggerAt** | When to surface the reminder |
| **tags** | Synaptic tags for contextual association |

---

## Where It Fits in the Pipeline

Due reminders are injected at **Step 2** of the recall pipeline — before scoring, ensuring they always appear at the top of results with maximum score (10.0):

```mermaid
flowchart TD
    RECALL["memory.recall(query)"] --> PROSPECTIVE["Step 2: Collect due reminders<br/><b>inject at score = 10.0</b>"]
    PROSPECTIVE --> SCAN["Steps 3–4: Tier scanning + scoring"]
    SCAN --> SUPPRESS["Step 4: Suppression filter"]
    SUPPRESS --> HAB["Step 5: Habituation + graphs"]
    HAB --> SORT["Final sort → Top-K<br/><i>reminders at top</i>"]

    style PROSPECTIVE fill:#e74c3c,color:white
    style SORT fill:#00b894,color:white
```

---

## Example Usage

```
Agent: "Remind me to check the deployment in 30 minutes"
→ memory.scheduleReminder(
      text: "Check deployment status",
      triggerAt: now + 30min,
      tags: ["deployment", "monitoring"]
  )

... 30 minutes later ...

Agent: memory.recall("what should I do?")
→ Result[0]: "Check deployment status" (score: 10.0, type: PROSPECTIVE)
```

---

## Next Steps

- :material-mirror: [**Metamemory — Self-Reflection**](metamemory.md) — memory health analytics
- :material-lightning-bolt: [**6-Phase Scoring Pipeline**](scoring-pipeline.md) — the full recall flow
