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
package com.spectrayan.spector.synapse.scheduler;

import com.spectrayan.spector.memory.scheduler.TaskRunAuditRecord;
import com.spectrayan.spector.memory.scheduler.TaskStatus;
import com.spectrayan.spector.synapse.memory.MemoryService;
import com.spectrayan.spector.synapse.scheduler.TaskDto.RescheduleCronRequest;
import com.spectrayan.spector.synapse.scheduler.TaskDto.RescheduleIntervalRequest;
import com.spectrayan.spector.synapse.scheduler.TaskDto.TaskActionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

/**
 * REST API for inspecting, controlling, and auditing in-memory background tasks
 * within the caller's memory namespace.
 *
 * <h3>Architecture</h3>
 * <p>Resolves the calling tenant's isolated {@link com.spectrayan.spector.memory.scheduler.MemoryScheduler}
 * via {@link MemoryService}, ensuring full multi-tenant isolation and virtual-thread execution safety.</p>
 *
 * @since 1.4.0
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskManagementController {

    private static final Logger log = LoggerFactory.getLogger(TaskManagementController.class);

    private final MemoryService memoryService;

    public TaskManagementController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    /**
     * Lists all registered background cognitive and maintenance tasks in this namespace.
     */
    @GetMapping
    public ResponseEntity<List<TaskStatus>> listTasks() {
        return ResponseEntity.ok(memoryService.listTasks());
    }

    /**
     * Retrieves status for a specific task.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TaskStatus> getTask(@PathVariable("id") String id) {
        return memoryService.getTask(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Triggers an immediate asynchronous execution of the specified task.
     */
    @PostMapping("/{id}/trigger")
    public ResponseEntity<TaskActionResponse> triggerTask(@PathVariable("id") String id) {
        log.info("REST: Triggering task [{}]", id);
        memoryService.triggerTask(id);
        return ResponseEntity.ok(new TaskActionResponse("TRIGGERED", id, "Task triggered asynchronously"));
    }

    /**
     * Pauses future scheduled executions of the specified task.
     */
    @PostMapping("/{id}/pause")
    public ResponseEntity<TaskActionResponse> pauseTask(@PathVariable("id") String id) {
        log.info("REST: Pausing task [{}]", id);
        memoryService.pauseTask(id);
        return ResponseEntity.ok(new TaskActionResponse("PAUSED", id, "Task paused successfully"));
    }

    /**
     * Resumes scheduled execution of a previously paused task.
     */
    @PostMapping("/{id}/resume")
    public ResponseEntity<TaskActionResponse> resumeTask(@PathVariable("id") String id) {
        log.info("REST: Resuming task [{}]", id);
        memoryService.resumeTask(id);
        return ResponseEntity.ok(new TaskActionResponse("RESUMED", id, "Task resumed successfully"));
    }

    /**
     * Reschedules an existing task with a new fixed interval in seconds.
     */
    @PostMapping("/{id}/reschedule-interval")
    public ResponseEntity<TaskActionResponse> rescheduleInterval(
            @PathVariable("id") String id,
            @RequestBody RescheduleIntervalRequest request) {

        if (request.intervalSeconds() <= 0) {
            return ResponseEntity.badRequest()
                    .body(new TaskActionResponse("ERROR", id, "intervalSeconds must be positive"));
        }
        memoryService.rescheduleTaskInterval(id, Duration.ofSeconds(request.intervalSeconds()));
        return ResponseEntity.ok(new TaskActionResponse("RESCHEDULED", id,
                "Task rescheduled to interval " + request.intervalSeconds() + "s"));
    }

    /**
     * Reschedules an existing task with a new Quartz cron expression.
     */
    @PostMapping("/{id}/reschedule-cron")
    public ResponseEntity<TaskActionResponse> rescheduleCron(
            @PathVariable("id") String id,
            @RequestBody RescheduleCronRequest request) {

        if (request.cronExpression() == null || request.cronExpression().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new TaskActionResponse("ERROR", id, "cronExpression must not be blank"));
        }
        memoryService.rescheduleTaskCron(id, request.cronExpression());
        return ResponseEntity.ok(new TaskActionResponse("RESCHEDULED", id,
                "Task rescheduled with cron [" + request.cronExpression() + "]"));
    }

    /**
     * Retrieves execution audit history for a specific task.
     */
    @GetMapping("/{id}/audit")
    public ResponseEntity<List<TaskRunAuditRecord>> getTaskAuditHistory(
            @PathVariable("id") String id,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {

        return ResponseEntity.ok(memoryService.getTaskAuditHistory(id, limit));
    }

    /**
     * Retrieves recent execution audit history across all tasks in this namespace.
     */
    @GetMapping("/audit")
    public ResponseEntity<List<TaskRunAuditRecord>> getRecentAuditHistory(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {

        return ResponseEntity.ok(memoryService.getRecentAuditHistory(limit));
    }
}
