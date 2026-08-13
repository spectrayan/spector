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
package com.spectrayan.spector.synapse.controller;

import com.spectrayan.spector.batch.SpectorBatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Paths;
import java.util.Map;

/**
 * REST controller exposing Spector Memory migration, import, and export Spring Batch capabilities.
 */
@RestController
@RequestMapping("/api/v1/migration")
public class MigrationController {

    private static final Logger log = LoggerFactory.getLogger(MigrationController.class);

    private final SpectorBatchService batchService;

    public MigrationController(SpectorBatchService batchService) {
        this.batchService = batchService;
    }

    /**
     * Triggers an asynchronous memory export job.
     */
    @PostMapping("/export")
    public ResponseEntity<Map<String, Object>> exportMemory(
            @RequestParam(defaultValue = "default") String namespace,
            @RequestParam String outputPath) {
        log.info("[REST] Received export request for namespace='{}' -> {}", namespace, outputPath);
        try {
            JobExecution execution = batchService.runExportJob(namespace, Paths.get(outputPath));
            return ResponseEntity.accepted().body(Map.of(
                    "executionId", execution.getId(),
                    "status", execution.getStatus().toString(),
                    "exitCode", execution.getExitStatus().getExitCode(),
                    "startTime", execution.getStartTime() != null ? execution.getStartTime().toString() : ""
            ));
        } catch (Exception e) {
            log.error("[REST] Export job initiation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Triggers an asynchronous memory import job.
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importMemory(
            @RequestParam String bundlePath,
            @RequestParam(defaultValue = "default") String targetNamespace) {
        log.info("[REST] Received import request from {} -> namespace='{}'", bundlePath, targetNamespace);
        try {
            JobExecution execution = batchService.runImportJob(Paths.get(bundlePath), targetNamespace);
            return ResponseEntity.accepted().body(Map.of(
                    "executionId", execution.getId(),
                    "status", execution.getStatus().toString(),
                    "exitCode", execution.getExitStatus().getExitCode(),
                    "startTime", execution.getStartTime() != null ? execution.getStartTime().toString() : ""
            ));
        } catch (Exception e) {
            log.error("[REST] Import job initiation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Polls status for a given batch job execution ID.
     */
    @GetMapping("/jobs/{executionId}")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable long executionId) {
        JobExecution execution = batchService.getJobExecution(executionId);
        if (execution == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "executionId", execution.getId(),
                "jobName", execution.getJobInstance().getJobName(),
                "status", execution.getStatus().toString(),
                "exitCode", execution.getExitStatus().getExitCode(),
                "exitDescription", execution.getExitStatus().getExitDescription(),
                "createTime", execution.getCreateTime() != null ? execution.getCreateTime().toString() : "",
                "endTime", execution.getEndTime() != null ? execution.getEndTime().toString() : ""
        ));
    }
}
