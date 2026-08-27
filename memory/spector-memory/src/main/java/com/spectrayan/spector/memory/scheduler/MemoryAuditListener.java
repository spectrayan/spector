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
package com.spectrayan.spector.memory.scheduler;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Top-level Quartz {@link JobListener} responsible for recording job execution timing,
 * completion statuses, domain reports, and error messages into bounded ring buffers.
 *
 * @since 1.4.0
 */
public final class MemoryAuditListener implements JobListener {

    private static final Logger log = LoggerFactory.getLogger(MemoryAuditListener.class);
    private static final int MAX_AUDIT_HISTORY_PER_TASK = 500;

    private final String listenerName;
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<TaskRunAuditRecord>> taskAuditStore = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<TaskRunAuditRecord> recentAuditHistory = new ConcurrentLinkedDeque<>();

    public MemoryAuditListener(String name) {
        this.listenerName = name != null && !name.isBlank() ? name : "GlobalMemoryAuditListener";
    }

    public MemoryAuditListener() {
        this("GlobalMemoryAuditListener");
    }

    @Override
    public String getName() {
        return listenerName;
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        context.put("exec_start_instant", Instant.now());
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
        String taskId = context.getJobDetail().getKey().getName();
        String group = context.getJobDetail().getKey().getGroup();
        log.warn("Task execution vetoed: [{}] in namespace [{}]", taskId, group);
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        Instant start = (Instant) context.get("exec_start_instant");
        if (start == null) {
            start = Instant.now();
        }
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);
        String taskId = context.getJobDetail().getKey().getName();
        String namespaceId = context.getJobDetail().getKey().getGroup();
        String runId = context.getFireInstanceId() != null ? context.getFireInstanceId() : UUID.randomUUID().toString();

        TaskRunAuditRecord record = new TaskRunAuditRecord(
                runId,
                taskId,
                namespaceId,
                start,
                end,
                duration,
                jobException == null ? "SUCCESS" : "FAILED",
                context.getResult(),
                jobException != null ? jobException.getMessage() : null
        );

        // Record into per-task audit ring buffer (keyed by "namespace:taskId")
        String key = namespaceId + ":" + taskId;
        var taskDeque = taskAuditStore.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        taskDeque.addFirst(record);
        while (taskDeque.size() > MAX_AUDIT_HISTORY_PER_TASK) {
            taskDeque.removeLast();
        }

        // Record into global recent history ring buffer
        recentAuditHistory.addFirst(record);
        while (recentAuditHistory.size() > MAX_AUDIT_HISTORY_PER_TASK * 2) {
            recentAuditHistory.removeLast();
        }

        if (jobException != null) {
            log.error("Task [{}] in namespace [{}] failed after {}ms: {}", taskId, namespaceId, duration.toMillis(), jobException.getMessage());
        } else {
            log.debug("Task [{}] in namespace [{}] completed in {}ms", taskId, namespaceId, duration.toMillis());
        }
    }

    /**
     * Returns the execution audit history for a specific task in a namespace.
     */
    public List<TaskRunAuditRecord> getAuditHistory(String namespaceId, String taskId, int limit) {
        String key = namespaceId + ":" + taskId;
        var deque = taskAuditStore.get(key);
        if (deque == null) {
            return List.of();
        }
        return deque.stream().limit(Math.max(1, limit)).toList();
    }

    /**
     * Returns the most recent execution audit history for a namespace.
     */
    public List<TaskRunAuditRecord> getRecentAuditHistory(String namespaceId, int limit) {
        return recentAuditHistory.stream()
                .filter(r -> namespaceId.equals(r.namespaceId()))
                .limit(Math.max(1, limit))
                .toList();
    }

    /**
     * Clears all audit history for a given namespace when deregistered.
     */
    public void clearNamespace(String namespaceId) {
        taskAuditStore.keySet().removeIf(k -> k.startsWith(namespaceId + ":"));
        recentAuditHistory.removeIf(r -> namespaceId.equals(r.namespaceId()));
    }
}
