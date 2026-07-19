/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
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
            memoryService.consolidate();
        } catch (Exception e) {
            log.error("ConsolidationScheduler: periodic memory consolidation failed", e);
        }
    }
}
