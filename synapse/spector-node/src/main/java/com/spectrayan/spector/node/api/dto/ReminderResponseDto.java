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
package com.spectrayan.spector.node.api.dto;

import com.spectrayan.spector.memory.prospective.Reminder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Response DTO for {@code POST /memory/reminder}.
 *
 * @param id             the reminder's unique identifier
 * @param text           the reminder text
 * @param delaySeconds   the requested delay
 * @param triggerTime    ISO-8601 timestamp when the reminder will fire
 * @param synapticTags   bloom filter tag encoding (long)
 */
public record ReminderResponseDto(
        String id,
        String text,
        int delaySeconds,
        String triggerTime,
        long synapticTags
) {

    /**
     * Creates a response DTO from the domain model.
     */
    public static ReminderResponseDto from(Reminder reminder, int delaySeconds) {
        String triggerTime = Instant.now()
                .plus(delaySeconds, ChronoUnit.SECONDS)
                .toString();
        return new ReminderResponseDto(
                reminder.id(),
                reminder.text(),
                delaySeconds,
                triggerTime,
                reminder.synapticTags()
        );
    }
}
