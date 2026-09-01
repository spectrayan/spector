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

import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.memory.scheduler.MemoryScheduler;

import java.util.List;

/**
 * Interface Segregation (ISP): Administration, statistics, identity application, and telemetry view.
 *
 * @since 1.4.0
 */
public interface MemoryAdminView {

    int totalMemories();

    int memoryCount(MemoryType type);

    void setSalienceProfile(SalienceProfile profile);

    void setSoulVersion(short version);

    default void applyIdentity(
            SoulContext primarySoul,
            List<SoulContext> soulStack,
            SalienceProfile salience) {
        if (salience != null) {
            setSalienceProfile(salience);
        }
        if (primarySoul != null) {
            setSoulVersion(primarySoul.soulVersion());
        }
    }

    SalienceProfile salienceProfile();

    float computeTopicBoost(String text);

    float computeSelfRelevanceBoost(String text);

    SpectorMemoryAdmin admin();

    default MemoryScheduler scheduler() {
        return admin().scheduler();
    }
}
