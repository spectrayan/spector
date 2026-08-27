/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.scheduler;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Public administrative contract for inspecting, triggering, pausing, rescheduling,
 * and auditing background cognitive and maintenance routines within a specific memory namespace.
 *
 * @since 1.4.0
 */
public interface MemoryScheduler extends AutoCloseable {

    /**
     * Returns the namespace ID bound to this scheduler.
     */
    String namespaceId();

    /**
     * Returns whether the scheduler is currently active and running.
     */
    boolean isRunning();

    /**
     * Lists all scheduled tasks registered within this namespace.
     */
    List<TaskStatus> listTasks();

    /**
     * Retrieves the status of a specific task by ID.
     *
     * @param taskId task identifier (e.g. "sleep-consolidation", "rem-dreaming")
     * @return optional task status descriptor
     */
    Optional<TaskStatus> getTask(String taskId);

    /**
     * Retrieves the most recent execution audit records for a specific task.
     *
     * @param taskId task identifier
     * @param limit  maximum number of records to return
     * @return list of audit records, newest first
     */
    List<TaskRunAuditRecord> getAuditHistory(String taskId, int limit);

    /**
     * Retrieves the most recent execution audit records across all tasks in this namespace.
     *
     * @param limit maximum number of records to return
     * @return list of audit records across all tasks, newest first
     */
    List<TaskRunAuditRecord> getRecentAuditHistory(int limit);

    /**
     * Triggers an immediate asynchronous execution of the specified task on virtual threads.
     *
     * @param taskId task identifier
     */
    void triggerNow(String taskId);

    /**
     * Pauses future scheduled executions of the specified task.
     *
     * @param taskId task identifier
     */
    void pause(String taskId);

    /**
     * Resumes scheduled execution of a previously paused task.
     *
     * @param taskId task identifier
     */
    void resume(String taskId);

    /**
     * Reschedules an existing task with a new fixed interval.
     *
     * @param taskId      task identifier
     * @param newInterval new interval between executions
     */
    void rescheduleInterval(String taskId, Duration newInterval);

    /**
     * Reschedules an existing task with a new Cron expression.
     *
     * @param taskId         task identifier
     * @param cronExpression standard 6-field Quartz cron expression (e.g. "0 0/15 * * * ?")
     */
    void rescheduleCron(String taskId, String cronExpression);

    /**
     * Gracefully shuts down the scheduler, draining in-flight jobs.
     */
    @Override
    void close();
}
