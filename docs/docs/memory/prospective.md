---
title: "Prospective — Future Intents"
description: "ProspectiveScheduler enables time-triggered memory reminders — the agent's ability to remember to do something in the future."
---

# 🔮 Prospective — Future Intents

> **Package**: `com.spectrayan.spector.memory.prospective`
>
> **Biological Analog**: **Prospective memory** is the ability to remember to perform an intended action in the future — "Remember to call the doctor at 3pm." Unlike retrospective memory (recalling the past), prospective memory is future-oriented and time-triggered.

---

## The Concept

An AI agent needs to remember not just *what happened*, but *what to do next*. Prospective memory enables:

- "Remind me to check the build in 10 minutes"
- "Flag this issue for follow-up tomorrow"
- "Alert when deployment completes"

---

## ProspectiveScheduler

```java
public final class ProspectiveScheduler {

    /**
     * Schedules a prospective reminder.
     *
     * @param text     reminder text
     * @param triggerAt when to surface the reminder
     * @param tags     synaptic tags for contextual association
     * @return the scheduled Reminder
     */
    public Reminder schedule(String text, Instant triggerAt, String... tags) {
        long synapticTags = SynapticTagEncoder.encode(tags);
        String id = "prospective-" + UUID.randomUUID();
        Reminder reminder = new Reminder(id, text, triggerAt, synapticTags, tags);
        reminders.add(reminder);
        return reminder;
    }

    /**
     * Collects all reminders whose trigger time has passed.
     * Called at Step 2 of the RecallPipeline.
     */
    public List<Reminder> collectDue() {
        Instant now = Instant.now();
        List<Reminder> due = new ArrayList<>();
        reminders.removeIf(r -> {
            if (r.triggerAt().isBefore(now)) {
                due.add(r);
                return true;
            }
            return false;
        });
        return due;
    }
}
```

## Reminder Record

```java
public record Reminder(
    String id,
    String text,
    Instant triggerAt,
    long synapticTags,
    String[] tags
) {}
```

---

## Integration with Recall

Due reminders are injected at **Step 2** of the `RecallPipeline` with maximum score (10.0), ensuring they always appear at the top of results:

```java
// In RecallPipeline.recall()
List<Reminder> dueReminders = prospectiveScheduler.collectDue();
for (Reminder r : dueReminders) {
    allResults.add(new CognitiveResult(
        r.id(), r.text(), 10.0f, 10.0f, 0f,
        (short) 0, (byte) 0, MemoryType.WORKING, MemorySource.PROCEDURAL,
        new String[]{"prospective"}, 1.0f, 1.0f));
}
```

---

## Next Steps

- :material-mirror: [**Metamemory — Self-Reflection**](metamemory.md) — memory health analytics
- :material-lightning-bolt: [**6-Phase Scoring Pipeline**](scoring-pipeline.md) — the full recall flow
