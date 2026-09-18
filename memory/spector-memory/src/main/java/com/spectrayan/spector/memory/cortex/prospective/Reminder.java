/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.memory.cortex.prospective;

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
