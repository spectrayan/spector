/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.api;

import com.spectrayan.spector.memory.aisme.continuity.IdentityTrajectorySnapshot;
import com.spectrayan.spector.memory.pathway.decide.relay.DecideReport;
import com.spectrayan.spector.memory.pathway.decide.relay.DecideSignal;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamMode;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamReport;
import com.spectrayan.spector.memory.pathway.express.relay.ExpressReport;
import com.spectrayan.spector.memory.pathway.express.relay.ExpressSignal;
import com.spectrayan.spector.memory.cortex.metamemory.MemoryInsight;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.neuromod.neurodivergent.IngestionHints;
import com.spectrayan.spector.memory.cortex.prospective.Reminder;
import com.spectrayan.spector.memory.pathway.wander.relay.WanderReport;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Interface Segregation (ISP): Consolidation, sleep/dream cycles, policy decision, suppression, and reflection operations.
 *
 * @since 1.4.0
 */
public interface MemoryReflection {

    void forget(String id);

    ReflectReport reflect();

    default ExpressReport express(ExpressSignal signal) {
        return ExpressReport.empty();
    }

    default WanderReport wander() {
        return WanderReport.empty();
    }

    default DreamReport dream(DreamMode mode) {
        return DreamReport.empty();
    }

    default DreamReport dream() {
        return dream(DreamMode.REM);
    }

    default DecideReport decide(DecideSignal signal) {
        return DecideReport.empty();
    }

    default List<IdentityTrajectorySnapshot> continuityHistory(int limit) {
        return List.of();
    }

    default float calculateLongitudinalDrift() {
        return 0.0f;
    }

    void consolidate();

    void reinforce(String memoryId, byte valence);

    default void reinforce(String memoryId, byte valence, IngestionHints updatedHints) {
        reinforce(memoryId, valence);
    }

    void suppress(String memoryId, String reason);

    void suppress(String memoryId);

    void unsuppress(String memoryId);

    void markResolved(String memoryId);

    void markUnresolved(String memoryId);

    MemoryInsight introspect(String topic);

    Reminder scheduleReminder(String text, Instant triggerAt, String... tags);

    Reminder scheduleReminder(String text, Duration delay, String... tags);

    int assertFact(String subject, String predicate, String object,
                   long validFrom, long validTo, float confidence);

    int assertFact(String subject, String predicate, String object,
                   long validFrom, long validTo, float confidence,
                   boolean allowCoexisting);

    int retractFact(int factId);
}
