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

import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;
import com.spectrayan.spector.commons.concurrent.VirtualThreadPool;
import com.spectrayan.spector.memory.DreamPathway;
import com.spectrayan.spector.memory.PartitionManager;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.graph.GraphEnrichmentDaemon;
import com.spectrayan.spector.memory.hippocampus.CircadianPolicy;
import com.spectrayan.spector.memory.scheduler.jobs.*;
import com.spectrayan.spector.memory.sync.CheckpointDaemon;
import org.quartz.*;
import org.quartz.impl.DirectSchedulerFactory;
import org.quartz.impl.matchers.EverythingMatcher;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.simpl.RAMJobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unified, multi-tenant Quartz implementation of {@link MemoryScheduler}.
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><b>Single Scheduler for All Namespaces</b>: A single Quartz engine manages all namespaces, partitioned by job group ({@code group = namespaceId}).</li>
 *   <li><b>Configurable JobStore</b>: Uses standalone {@link RAMJobStore} by default, or consumes any custom/injected {@link Scheduler} (e.g. from Spring Boot Synapse with JDBC/RAM).</li>
 *   <li><b>JobStore Metadata Integration</b>: Task schedules and descriptions are stored in Quartz {@link JobDetail} and {@link Trigger} metadata.</li>
 *   <li><b>Execution Auditing</b>: Captures execution metrics and reports via {@link MemoryAuditListener}.</li>
 *   <li><b>Virtual Thread Concurrency</b>: Powered by {@link VirtualThreadPool} delegating directly to Java 25 virtual threads.</li>
 * </ul>
 *
 * @since 1.4.0
 */
public final class QuartzMemoryScheduler implements MemoryScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuartzMemoryScheduler.class);
    private static final String DEFAULT_STANDALONE_SCHEDULER_NAME = "spector-standalone-scheduler";
    private static final MemoryAuditListener GLOBAL_AUDIT_LISTENER = new MemoryAuditListener("GlobalMemoryAuditListener");

    public static final String TASK_SLEEP_CONSOLIDATION = "sleep-consolidation";
    public static final String TASK_REM_DREAMING = "rem-dreaming";
    public static final String TASK_CHECKPOINT = "checkpoint";
    public static final String TASK_DMN_WANDERING = "dmn-wandering";
    public static final String TASK_HOMEOSTATIC_DECAY = "homeostatic-decay";
    public static final String TASK_GRAPH_ENRICHMENT = "graph-enrichment";

    private final String namespaceId;
    private final Scheduler quartzScheduler;
    private final boolean ownsLifecycle;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final MemoryAuditListener auditListener;

    public QuartzMemoryScheduler(
            String namespaceId,
            SpectorMemory memory,
            CircadianPolicy circadianPolicy,
            DreamPathway dreamPathway,
            PartitionManager partitionManager,
            AismeConfig aismeConfig,
            CheckpointDaemon checkpointDaemon,
            GraphEnrichmentDaemon graphEnrichmentDaemon,
            Runnable dmnDaemon,
            Runnable decayDaemon,
            long checkpointIntervalSeconds,
            Executor suppliedExecutor,
            Scheduler suppliedScheduler) {

        this.namespaceId = namespaceId != null && !namespaceId.isBlank() ? namespaceId : "default";
        this.auditListener = GLOBAL_AUDIT_LISTENER;

        if (suppliedScheduler != null) {
            this.quartzScheduler = suppliedScheduler;
            this.ownsLifecycle = false;
        } else {
            this.quartzScheduler = resolveDefaultStandaloneScheduler(suppliedExecutor);
            this.ownsLifecycle = false;
        }

        try {
            ensureAuditListenerRegistered(this.quartzScheduler, this.auditListener);

            if (!this.quartzScheduler.isStarted()) {
                this.quartzScheduler.start();
            }

            // Register core memory tasks under group = namespaceId
            registerTasks(memory, circadianPolicy, dreamPathway, partitionManager, aismeConfig,
                    checkpointDaemon, graphEnrichmentDaemon, dmnDaemon, decayDaemon, checkpointIntervalSeconds);

            this.active.set(true);
            log.info("QuartzMemoryScheduler initialized for namespace [{}]", this.namespaceId);

        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to initialize QuartzMemoryScheduler for namespace " + this.namespaceId, e);
        }
    }

    private static synchronized void ensureAuditListenerRegistered(Scheduler scheduler, MemoryAuditListener listener) {
        try {
            if (scheduler.getListenerManager().getJobListener(listener.getName()) == null) {
                scheduler.getListenerManager().addJobListener(listener, EverythingMatcher.allJobs());
            }
        } catch (SchedulerException e) {
            log.warn("Failed to register MemoryAuditListener on scheduler: {}", e.getMessage());
        }
    }

    private static synchronized Scheduler resolveDefaultStandaloneScheduler(Executor suppliedExecutor) {
        DirectSchedulerFactory factory = DirectSchedulerFactory.getInstance();
        try {
            Scheduler existing = factory.getScheduler(DEFAULT_STANDALONE_SCHEDULER_NAME);
            if (existing != null && !existing.isShutdown()) {
                return existing;
            }

            Executor targetExecutor = suppliedExecutor != null ? suppliedExecutor : ConcurrentTasks.virtualExecutor();
            VirtualThreadPool threadPool = new VirtualThreadPool(targetExecutor);
            threadPool.setInstanceName(DEFAULT_STANDALONE_SCHEDULER_NAME);
            threadPool.initialize();

            factory.createScheduler(DEFAULT_STANDALONE_SCHEDULER_NAME, "STANDALONE_PRIMARY",
                    threadPool, new RAMJobStore());
            Scheduler scheduler = factory.getScheduler(DEFAULT_STANDALONE_SCHEDULER_NAME);
            scheduler.start();
            return scheduler;
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to initialize default standalone Quartz scheduler", e);
        }
    }

    private void registerTasks(
            SpectorMemory memory,
            CircadianPolicy circadianPolicy,
            DreamPathway dreamPathway,
            PartitionManager partitionManager,
            AismeConfig aismeConfig,
            CheckpointDaemon checkpointDaemon,
            GraphEnrichmentDaemon graphEnrichmentDaemon,
            Runnable dmnDaemon,
            Runnable decayDaemon,
            long checkpointIntervalSeconds) throws SchedulerException {

        // 1. Sleep Consolidation (Circadian reflect())
        if (circadianPolicy != null && circadianPolicy.timeTrigger() != null
                && !circadianPolicy.timeTrigger().isZero() && !circadianPolicy.timeTrigger().isNegative()) {
            JobDataMap map = new JobDataMap();
            map.put("memoryInstance", memory);
            map.put("namespaceId", namespaceId);

            long intervalMs = circadianPolicy.timeTrigger().toMillis();
            scheduleJobInternal(TASK_SLEEP_CONSOLIDATION,
                    "Hippocampal sleep consolidation — episodic-to-semantic promotion and Hebbian decay",
                    SleepConsolidationJob.class, map,
                    SimpleScheduleBuilder.simpleSchedule().withIntervalInMilliseconds(intervalMs).repeatForever(),
                    intervalMs);
        }

        // 2. Offline REM & Creative Dreaming
        if (dreamPathway != null && dreamPathway.config() != null && dreamPathway.config().enabled()) {
            JobDataMap map = new JobDataMap();
            map.put("dreamPathway", dreamPathway);
            map.put("partitionManager", partitionManager);
            map.put("aismeConfig", aismeConfig);
            map.put("namespaceId", namespaceId);

            int freqSeconds = Math.max(10, dreamPathway.config().dreamCycleFrequency() * 30);
            scheduleJobInternal(TASK_REM_DREAMING,
                    "Offline REM and creative dreaming — counterfactual policy evaluation and generative scene synthesis",
                    RemDreamJob.class, map,
                    SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(freqSeconds).repeatForever(),
                    10_000L);
        }

        // 3. Storage Checkpointing (DISK mode)
        if (checkpointDaemon != null && checkpointIntervalSeconds > 0) {
            JobDataMap map = new JobDataMap();
            map.put("checkpointDaemon", checkpointDaemon);
            map.put("namespaceId", namespaceId);

            scheduleJobInternal(TASK_CHECKPOINT,
                    "Storage Checkpoint — WAL compaction, index sync, and off-heap memory snapshotting",
                    CheckpointJob.class, map,
                    SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds((int) checkpointIntervalSeconds).repeatForever(),
                    checkpointIntervalSeconds * 1000L);
        }

        // 4. Default Mode Network (DMN Wandering)
        if (dmnDaemon != null) {
            JobDataMap map = new JobDataMap();
            map.put("dmnDaemon", dmnDaemon);
            map.put("namespaceId", namespaceId);

            int dmnSeconds = aismeConfig != null ? Math.max(10, aismeConfig.dmnIdleIntervalSeconds()) : 60;
            scheduleJobInternal(TASK_DMN_WANDERING,
                    "Default Mode Network (DMN) — spontaneous mind-wandering and predictive narrative associations",
                    DmnWanderingJob.class, map,
                    SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(dmnSeconds).repeatForever(),
                    15_000L);
        }

        // 5. Homeostatic Synaptic Decay
        if (decayDaemon != null) {
            JobDataMap map = new JobDataMap();
            map.put("decayDaemon", decayDaemon);
            map.put("namespaceId", namespaceId);

            int decaySeconds = aismeConfig != null ? Math.max(10, aismeConfig.backgroundDecayIntervalSeconds()) : 60;
            scheduleJobInternal(TASK_HOMEOSTATIC_DECAY,
                    "Homeostatic Synaptic Decay — energy regulation and baseline synaptic normalization",
                    HomeostaticDecayJob.class, map,
                    SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(decaySeconds).repeatForever(),
                    20_000L);
        }

        // 6. Graph Enrichment
        if (graphEnrichmentDaemon != null) {
            JobDataMap map = new JobDataMap();
            map.put("graphEnrichmentDaemon", graphEnrichmentDaemon);
            map.put("namespaceId", namespaceId);

            scheduleJobInternal(TASK_GRAPH_ENRICHMENT,
                    "Graph Enrichment — async entity extraction, hypergraph relation synthesis, and temporal knowledge sync",
                    GraphEnrichmentJob.class, map,
                    SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(30).repeatForever(),
                    10_000L);
        }
    }

    private void scheduleJobInternal(
            String taskId,
            String description,
            Class<? extends Job> jobClass,
            JobDataMap dataMap,
            ScheduleBuilder<?> scheduleBuilder,
            long initialDelayMs) throws SchedulerException {

        JobDetail job = JobBuilder.newJob(jobClass)
                .withIdentity(taskId, namespaceId)
                .withDescription(description)
                .usingJobData(dataMap)
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(taskId + "-trigger", namespaceId)
                .withDescription(description)
                .startAt(Date.from(Instant.now().plusMillis(Math.max(1000L, initialDelayMs))))
                .withSchedule(scheduleBuilder)
                .build();

        if (quartzScheduler.checkExists(job.getKey())) {
            quartzScheduler.deleteJob(job.getKey());
        }
        quartzScheduler.scheduleJob(job, trigger);
    }

    @Override
    public String namespaceId() {
        return namespaceId;
    }

    @Override
    public boolean isRunning() {
        return active.get() && !quartzSchedulerIsShutdown();
    }

    private boolean quartzSchedulerIsShutdown() {
        try {
            return quartzScheduler.isShutdown();
        } catch (SchedulerException e) {
            return true;
        }
    }

    @Override
    public List<TaskStatus> listTasks() {
        List<TaskStatus> list = new ArrayList<>();
        try {
            for (JobKey jobKey : quartzScheduler.getJobKeys(GroupMatcher.jobGroupEquals(namespaceId))) {
                JobDetail detail = quartzScheduler.getJobDetail(jobKey);
                List<? extends Trigger> triggers = quartzScheduler.getTriggersOfJob(jobKey);
                Trigger tr = triggers.isEmpty() ? null : triggers.get(0);
                Trigger.TriggerState state = tr != null ? quartzScheduler.getTriggerState(tr.getKey()) : Trigger.TriggerState.NONE;

                Date prevFire = tr != null ? tr.getPreviousFireTime() : null;
                if (prevFire == null) {
                    var history = auditListener.getAuditHistory(namespaceId, jobKey.getName(), 1);
                    if (!history.isEmpty()) {
                        prevFire = Date.from(history.get(0).startTime());
                    }
                }

                list.add(new TaskStatus(
                        jobKey.getName(),
                        namespaceId,
                        state.name(),
                        prevFire,
                        tr != null ? tr.getNextFireTime() : null,
                        detail != null && detail.getDescription() != null ? detail.getDescription() : "Memory background task"
                ));
            }
        } catch (SchedulerException e) {
            log.error("Failed to list scheduled tasks for namespace {}", namespaceId, e);
        }
        return list;
    }

    @Override
    public Optional<TaskStatus> getTask(String taskId) {
        try {
            JobKey jobKey = JobKey.jobKey(taskId, namespaceId);
            if (!quartzScheduler.checkExists(jobKey)) {
                return Optional.empty();
            }

            JobDetail detail = quartzScheduler.getJobDetail(jobKey);
            List<? extends Trigger> triggers = quartzScheduler.getTriggersOfJob(jobKey);
            Trigger tr = triggers.isEmpty() ? null : triggers.get(0);
            Trigger.TriggerState state = tr != null ? quartzScheduler.getTriggerState(tr.getKey()) : Trigger.TriggerState.NONE;

            Date prevFire = tr != null ? tr.getPreviousFireTime() : null;
            if (prevFire == null) {
                var history = auditListener.getAuditHistory(namespaceId, taskId, 1);
                if (!history.isEmpty()) {
                    prevFire = Date.from(history.get(0).startTime());
                }
            }

            return Optional.of(new TaskStatus(
                    jobKey.getName(),
                    namespaceId,
                    state.name(),
                    prevFire,
                    tr != null ? tr.getNextFireTime() : null,
                    detail != null && detail.getDescription() != null ? detail.getDescription() : "Memory background task"
            ));
        } catch (SchedulerException e) {
            log.error("Failed to get task [{}] for namespace {}", taskId, namespaceId, e);
            return Optional.empty();
        }
    }

    @Override
    public List<TaskRunAuditRecord> getAuditHistory(String taskId, int limit) {
        return auditListener.getAuditHistory(namespaceId, taskId, limit);
    }

    @Override
    public List<TaskRunAuditRecord> getRecentAuditHistory(int limit) {
        return auditListener.getRecentAuditHistory(namespaceId, limit);
    }

    @Override
    public void triggerNow(String taskId) {
        try {
            JobKey jobKey = JobKey.jobKey(taskId, namespaceId);
            if (!quartzScheduler.checkExists(jobKey)) {
                throw new IllegalArgumentException("Task '" + taskId + "' not found in namespace " + namespaceId);
            }
            quartzScheduler.triggerJob(jobKey);
            log.debug("Triggered immediate execution for task [{}] in namespace [{}]", taskId, namespaceId);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to trigger task " + taskId + " in namespace " + namespaceId, e);
        }
    }

    @Override
    public void pause(String taskId) {
        try {
            JobKey jobKey = JobKey.jobKey(taskId, namespaceId);
            quartzScheduler.pauseJob(jobKey);
            log.info("Paused task [{}] in namespace [{}]", taskId, namespaceId);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to pause task " + taskId, e);
        }
    }

    @Override
    public void resume(String taskId) {
        try {
            JobKey jobKey = JobKey.jobKey(taskId, namespaceId);
            quartzScheduler.resumeJob(jobKey);
            log.info("Resumed task [{}] in namespace [{}]", taskId, namespaceId);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to resume task " + taskId, e);
        }
    }

    @Override
    public void rescheduleInterval(String taskId, Duration newInterval) {
        Objects.requireNonNull(newInterval, "newInterval must not be null");
        try {
            TriggerKey triggerKey = TriggerKey.triggerKey(taskId + "-trigger", namespaceId);
            Trigger newTrigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .startAt(Date.from(Instant.now().plusMillis(newInterval.toMillis())))
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInMilliseconds(newInterval.toMillis())
                            .repeatForever())
                    .build();

            quartzScheduler.rescheduleJob(triggerKey, newTrigger);
            log.info("Rescheduled task [{}] in namespace [{}] with interval {}ms", taskId, namespaceId, newInterval.toMillis());
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to reschedule task " + taskId, e);
        }
    }

    @Override
    public void rescheduleCron(String taskId, String cronExpression) {
        Objects.requireNonNull(cronExpression, "cronExpression must not be null");
        try {
            TriggerKey triggerKey = TriggerKey.triggerKey(taskId + "-trigger", namespaceId);
            Trigger newTrigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                    .build();

            quartzScheduler.rescheduleJob(triggerKey, newTrigger);
            log.info("Rescheduled task [{}] in namespace [{}] with cron [{}]", taskId, namespaceId, cronExpression);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to reschedule task " + taskId + " with cron " + cronExpression, e);
        }
    }

    @Override
    public void close() {
        if (active.compareAndSet(true, false)) {
            try {
                log.info("Deregistering tasks for namespace [{}]", namespaceId);
                var jobKeys = quartzScheduler.getJobKeys(GroupMatcher.jobGroupEquals(namespaceId));
                if (!jobKeys.isEmpty()) {
                    quartzScheduler.deleteJobs(new ArrayList<>(jobKeys));
                }
                auditListener.clearNamespace(namespaceId);
                if (ownsLifecycle) {
                    quartzScheduler.shutdown(true);
                }
            } catch (SchedulerException e) {
                log.warn("Error during QuartzMemoryScheduler close for namespace {}: {}", namespaceId, e.getMessage());
            }
        }
    }

    public Scheduler rawQuartzScheduler() {
        return quartzScheduler;
    }
}
