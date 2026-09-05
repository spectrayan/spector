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
package com.spectrayan.spector.memory.pathway.reflect.spi.local;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepProgress;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepSpec;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepStatus;
import com.spectrayan.spector.memory.pathway.reflect.spi.ReflectSweepExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-process reflection sweep executor.
 *
 * <p>Executes reflection sweeps synchronously on the calling thread, tracking progress
 * in-memory and conducting the reflection pathway through the memory kernel.</p>
 *
 * @since 1.5.0
 */
public final class InProcessReflectSweepExecutor implements ReflectSweepExecutor {

    private static final Logger log = LoggerFactory.getLogger(InProcessReflectSweepExecutor.class);

    public static final InProcessReflectSweepExecutor INSTANCE = new InProcessReflectSweepExecutor();

    private final Map<String, ReflectSweepProgress> progressMap = new ConcurrentHashMap<>();

    public InProcessReflectSweepExecutor() {
    }

    @Override
    public String name() {
        return "in-process";
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public ReflectReport execute(SpectorMemory memory, ReflectSweepSpec spec) {
        ReflectSweepSpec effectiveSpec = (spec != null) ? spec : ReflectSweepSpec.fullCycle();
        String sweepId = effectiveSpec.sweepId();
        Instant startedAt = Instant.now();

        log.info("InProcessReflectSweepExecutor: starting sweep '{}' (limit={}, companion={})",
                sweepId, effectiveSpec.sessionLimit(), effectiveSpec.runCompanionRelays());

        progressMap.put(sweepId, new ReflectSweepProgress(
                sweepId, 0, 0, 0, 0, ReflectSweepStatus.RUNNING, startedAt, startedAt));

        try {
            ReflectReport report;
            if (memory != null && memory.admin() != null) {
                report = memory.admin().reflectKernel(effectiveSpec);
            } else if (memory != null) {
                report = memory.reflect();
            } else {
                report = ReflectReport.empty();
            }

            progressMap.put(sweepId, new ReflectSweepProgress(
                    sweepId,
                    report.consolidatedCount(),
                    report.consolidatedCount(),
                    report.consolidatedCount(),
                    0,
                    ReflectSweepStatus.COMPLETE,
                    startedAt,
                    Instant.now()
            ));
            return report;
        } catch (Throwable t) {
            log.error("InProcessReflectSweepExecutor: sweep '{}' failed: {}", sweepId, t.getMessage(), t);
            progressMap.put(sweepId, new ReflectSweepProgress(
                    sweepId, 0, 0, 0, 0, ReflectSweepStatus.FAILED, startedAt, Instant.now()));
            if (t instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("In-process reflection sweep failed: " + t.getMessage(), t);
        }
    }

    @Override
    public ReflectSweepProgress progress(String sweepId) {
        if (sweepId == null) {
            return null;
        }
        return progressMap.get(sweepId);
    }
}
