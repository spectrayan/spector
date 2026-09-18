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
package com.spectrayan.spector.synapse.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task that triggers background memory consolidation periodically.
 */
@Component
public class ConsolidationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ConsolidationScheduler.class);

    private final MemoryService memoryService;

    public ConsolidationScheduler(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    /**
     * Periodically triggers memory consolidation.
     * Uses fixedDelayString configured via {@code spector.memory.consolidation.interval}
     * with a fallback default of 6 hours (21600000 ms).
     */
    @Scheduled(fixedDelayString = "${spector.memory.consolidation.interval:21600000}", initialDelay = 60000)
    public void scheduleConsolidation() {
        log.info("ConsolidationScheduler: Starting periodic background consolidation task...");
        try {
            memoryService.consolidateAll();
        } catch (Exception e) {
            log.error("ConsolidationScheduler: periodic memory consolidation failed", e);
        }
    }

    /**
     * Periodically triggers circadian sleep reflection across all cached memory instances.
     * Uses fixedDelayString configured via {@code spector.memory.circadian.interval}
     * with a fallback default of 1 hour (3600000 ms).
     */
    @Scheduled(fixedDelayString = "${spector.memory.circadian.interval:3600000}", initialDelay = 120000)
    public void scheduleCircadianReflection() {
        log.info("ConsolidationScheduler: Starting periodic background circadian reflection task...");
        try {
            memoryService.reflectAll();
        } catch (Exception e) {
            log.error("ConsolidationScheduler: periodic circadian reflection failed", e);
        }
    }
}
