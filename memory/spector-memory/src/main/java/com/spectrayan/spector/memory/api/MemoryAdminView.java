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
package com.spectrayan.spector.memory.api;

import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.memory.scheduler.MemoryScheduler;

import java.util.List;

/**
 * Administration, statistics, identity application, and telemetry view.
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
