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

import com.spectrayan.spector.memory.pathway.reflect.ReflectCheckpoint;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepStatus;
import com.spectrayan.spector.memory.pathway.reflect.spi.ReflectCheckpointStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File-backed checkpoint store persisting checkpoints as individual JSON documents
 * with atomic rename semantics for crash safety.
 *
 * @since 1.5.0
 */
public final class FileReflectCheckpointStore implements ReflectCheckpointStore {

    private static final Logger log = LoggerFactory.getLogger(FileReflectCheckpointStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path storageDir;
    private final Map<String, ReflectCheckpoint> memoryFallback = new ConcurrentHashMap<>();

    public FileReflectCheckpointStore(Path storageDir) {
        this.storageDir = Objects.requireNonNull(storageDir, "storageDir cannot be null");
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            log.warn("Failed to create checkpoint directory {}: {}", storageDir, e.getMessage());
        }
    }

    @Override
    public Optional<ReflectCheckpoint> load(String sweepId) {
        if (sweepId == null || sweepId.isBlank()) {
            return Optional.empty();
        }

        Path checkpointFile;
        try {
            checkpointFile = checkpointPath(sweepId);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid checkpoint sweepId: {}", sweepId);
            return Optional.ofNullable(memoryFallback.get(sweepId));
        }

        Path baseDir = storageDir.toAbsolutePath().normalize();
        if (!checkpointFile.startsWith(baseDir)) {
            log.warn("Path traversal check failed for sweepId: {}", sweepId);
            return Optional.ofNullable(memoryFallback.get(sweepId));
        }

        if (Files.exists(checkpointFile)) {
            try {
                byte[] bytes = Files.readAllBytes(checkpointFile);
                JsonNode root = MAPPER.readTree(bytes);
                ReflectCheckpoint cp = parseCheckpoint(root, sweepId);
                memoryFallback.put(sweepId, cp);
                return Optional.of(cp);
            } catch (Exception e) {
                log.warn("Failed to read checkpoint from {}: {}", checkpointFile, e.getMessage());
                return Optional.ofNullable(memoryFallback.get(sweepId));
            }
        } else {
            memoryFallback.remove(sweepId);
            return Optional.empty();
        }
    }

    @Override
    public void save(ReflectCheckpoint checkpoint) {
        if (checkpoint == null || checkpoint.sweepId() == null || checkpoint.sweepId().isBlank()) {
            return;
        }

        String sweepId = checkpoint.sweepId();
        memoryFallback.put(sweepId, checkpoint);

        Path targetFile;
        try {
            targetFile = checkpointPath(sweepId);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid sweepId for checkpoint save: {}", sweepId);
            return;
        }

        Path baseDir = storageDir.toAbsolutePath().normalize();
        if (!targetFile.startsWith(baseDir)) {
            log.warn("Path traversal check failed for checkpoint save: {}", sweepId);
            return;
        }

        Path tempFile = baseDir.resolve(targetFile.getFileName().toString() + ".tmp").normalize();
        if (!tempFile.startsWith(baseDir)) {
            log.warn("Path traversal check failed for checkpoint temp file: {}", sweepId);
            return;
        }

        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("sweepId", checkpoint.sweepId());
            root.put("partitionSeq", checkpoint.partitionSeq());
            root.put("lastCompletedSessionId", checkpoint.lastCompletedSessionId());
            root.put("lastCompletedTurnOffset", checkpoint.lastCompletedTurnOffset());
            root.put("sessionsCompleted", checkpoint.sessionsCompleted());
            root.put("factsIngested", checkpoint.factsIngested());
            root.put("turnsMarked", checkpoint.turnsMarked());
            root.put("backlogRemaining", checkpoint.backlogRemaining());
            root.put("updatedAt", checkpoint.updatedAt().toString());
            root.put("status", checkpoint.status().name());

            byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
            Files.write(tempFile, bytes);
            try {
                Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ae) {
                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.warn("Failed to persist checkpoint to disk for sweep {}: {}", sweepId, e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void delete(String sweepId) {
        if (sweepId == null || sweepId.isBlank()) return;
        memoryFallback.remove(sweepId);
        try {
            Path targetFile = checkpointPath(sweepId);
            Path baseDir = storageDir.toAbsolutePath().normalize();
            if (targetFile.startsWith(baseDir)) {
                Files.deleteIfExists(targetFile);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid sweepId for checkpoint deletion: {}", sweepId);
        } catch (IOException e) {
            log.warn("Failed to delete checkpoint file for sweep {}: {}", sweepId, e.getMessage());
        }
    }

    private ReflectCheckpoint parseCheckpoint(JsonNode root, String fallbackSweepId) {
        String sweepId = root.has("sweepId") ? root.get("sweepId").asText() : fallbackSweepId;
        int partitionSeq = root.has("partitionSeq") ? root.get("partitionSeq").asInt() : 0;
        long lastCompletedSessionId = root.has("lastCompletedSessionId") ? root.get("lastCompletedSessionId").asLong() : 0L;
        long lastCompletedTurnOffset = root.has("lastCompletedTurnOffset") ? root.get("lastCompletedTurnOffset").asLong() : 0L;
        int sessionsCompleted = root.has("sessionsCompleted") ? root.get("sessionsCompleted").asInt() : 0;
        int factsIngested = root.has("factsIngested") ? root.get("factsIngested").asInt() : 0;
        int turnsMarked = root.has("turnsMarked") ? root.get("turnsMarked").asInt() : 0;
        int backlogRemaining = root.has("backlogRemaining") ? root.get("backlogRemaining").asInt() : 0;

        Instant updatedAt = Instant.now();
        if (root.has("updatedAt")) {
            try {
                updatedAt = Instant.parse(root.get("updatedAt").asText());
            } catch (Exception ignored) {
            }
        }

        ReflectSweepStatus status = ReflectSweepStatus.IDLE;
        if (root.has("status")) {
            try {
                status = ReflectSweepStatus.valueOf(root.get("status").asText());
            } catch (Exception ignored) {
            }
        }

        return new ReflectCheckpoint(
                sweepId,
                partitionSeq,
                lastCompletedSessionId,
                lastCompletedTurnOffset,
                sessionsCompleted,
                factsIngested,
                turnsMarked,
                backlogRemaining,
                updatedAt,
                status
        );
    }

    private Path checkpointPath(String sweepId) {
        if (sweepId == null || sweepId.isBlank()) {
            throw new IllegalArgumentException("sweepId cannot be null or blank");
        }
        String safeName = sweepId.replaceAll("[^a-zA-Z0-9_-]", "_") + ".checkpoint.json";
        Path baseDir = storageDir.toAbsolutePath().normalize();
        Path resolved = baseDir.resolve(safeName).normalize();
        if (!resolved.startsWith(baseDir) || !baseDir.equals(resolved.getParent())) {
            throw new IllegalArgumentException("Invalid sweepId path: " + sweepId);
        }
        return resolved;
    }
}
