package com.spectrayan.spector.memory.prospective;

import java.time.Instant;

/**
 * A scheduled memory reminder — surfaces at a future time regardless of query similarity.
 *
 * @param id          unique reminder identifier
 * @param text        the reminder text
 * @param triggerAt   when to surface this reminder
 * @param synapticTags Bloom filter tags for contextual association
 * @param created     when the reminder was created
 */
public record Reminder(
        String id,
        String text,
        Instant triggerAt,
        long synapticTags,
        Instant created
) {

    /**
     * Returns true if this reminder is due (trigger time has passed).
     */
    public boolean isDue() {
        return Instant.now().isAfter(triggerAt);
    }

    /**
     * Returns true if this reminder is due at the specified time.
     */
    public boolean isDueAt(Instant now) {
        return now.isAfter(triggerAt);
    }
}
