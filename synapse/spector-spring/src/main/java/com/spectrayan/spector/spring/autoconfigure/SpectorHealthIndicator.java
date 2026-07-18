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
package com.spectrayan.spector.spring.autoconfigure;

import com.spectrayan.spector.core.simd.SimdCapability;
import com.spectrayan.spector.memory.SpectorMemory;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * Spring Boot Actuator health indicator for Spector.
 *
 * <p>Reports SIMD capability and cognitive memory tier counts at {@code /actuator/health}.</p>
 */
@Component
@ConditionalOnClass({HealthIndicator.class, SpectorMemory.class})
public class SpectorHealthIndicator implements HealthIndicator {

    private final SpectorMemory memory;

    public SpectorHealthIndicator(SpectorMemory memory) {
        this.memory = memory;
    }

    @Override
    public Health health() {
        try {
            var builder = Health.up()
                    .withDetail("simd", SimdCapability.report());

            if (memory != null) {
                builder.withDetail("memory.total", memory.totalMemories());
                builder.withDetail("memory.working",
                        memory.memoryCount(com.spectrayan.spector.memory.model.MemoryType.WORKING));
                builder.withDetail("memory.episodic",
                        memory.memoryCount(com.spectrayan.spector.memory.model.MemoryType.EPISODIC));
                builder.withDetail("memory.semantic",
                        memory.memoryCount(com.spectrayan.spector.memory.model.MemoryType.SEMANTIC));
                builder.withDetail("memory.procedural",
                        memory.memoryCount(com.spectrayan.spector.memory.model.MemoryType.PROCEDURAL));
            }

            return builder.build();
        } catch (Exception e) {
            return Health.down()
                    .withException(e)
                    .build();
        }
    }
}
