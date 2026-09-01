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
package com.spectrayan.spector.memory.api;

import com.spectrayan.spector.memory.RememberPathway;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.ConversationRole;
import com.spectrayan.spector.memory.model.ImportanceResult;
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;

import java.nio.file.Path;

/**
 * Interface Segregation (ISP): Ingestion operations on cognitive memory.
 *
 * @since 1.4.0
 */
public interface MemoryIngestion {

    RememberPathway target();

    default String namespaceId() { return "default"; }

    default AutoCloseable acquireLease() { return () -> {}; }

    void remember(String id, String text, MemoryType type, MemorySource source, String... tags);

    void remember(String id, String text, MemoryType type, MemorySource source, IngestionHints hints, String... tags);

    void remember(String id, String text, MemoryType type, MemorySource source, IngestionContext context, String... tags);

    void remember(String id, String text, MemoryType type, String... tags);

    String remember(String text, MemoryType type, MemorySource source, String... tags);

    String remember(String text, MemoryType type, MemorySource source, IngestionHints hints, String... tags);

    String remember(String text, MemoryType type, MemorySource source, IngestionContext context, String... tags);

    default String rememberFile(Path filePath, String text, MemoryType type, MemorySource source, String... tags) {
        String effectiveText = (text != null && !text.isBlank()) ? text : filePath.getFileName().toString();
        IngestionContext context = IngestionContext.builder()
                .metadata(SourceModality.ATTACHMENTS_KEY, filePath.toAbsolutePath().toString())
                .build();
        return remember(effectiveText, type, source, context, tags);
    }

    default long rememberEpisodic(ConversationRole role, int sequenceId,
                                   long timestampMs, long sessionId,
                                   byte[] body, short modelId,
                                   int tokenIn, int tokenOut,
                                   int latencyMs, long userId,
                                   short soulVersion, SourceModality modality) {
        throw new UnsupportedOperationException("Episodic log not supported by this implementation");
    }

    void scratchpad(String text);

    ImportanceResult estimateImportance(String text, IngestionHints hints);

    default ImportanceResult estimateImportance(String text) {
        return estimateImportance(text, null);
    }

    default void updateChunkConfig(com.spectrayan.spector.commons.chunker.ChunkConfig config) {}
}
