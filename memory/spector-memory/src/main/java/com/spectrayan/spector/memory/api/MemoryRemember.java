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
package com.spectrayan.spector.memory.api;

import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.ConversationRole;
import com.spectrayan.spector.memory.model.ImportanceResult;
import com.spectrayan.spector.memory.model.RememberContext;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.api.SourceModality;
import com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints;

import java.nio.file.Path;

/**
 * Ingestion and memory encoding operations (remember) on cognitive memory.
 *
 * @since 1.4.0
 */
public interface MemoryRemember {

    default String namespaceId() { return "default"; }

    default AutoCloseable acquireLease() { return () -> {}; }

    void remember(String id, String text, MemoryType type, MemorySource source, String... tags);

    void remember(String id, String text, MemoryType type, MemorySource source, RememberHints hints, String... tags);

    void remember(String id, String text, MemoryType type, MemorySource source, RememberContext context, String... tags);

    /**
     * Ingest a memory item with a pre-computed vector embedding.
     *
     * <p>When {@code vector} is provided (non-null and non-empty), it is used
     * directly, bypassing text embedding generation.</p>
     *
     * @param id unique memory identifier
     * @param text content of the memory
     * @param vector pre-computed embedding vector (if null, embedding will be generated)
     * @param type target cognitive tier
     * @param source origin source of the memory
     * @param context optional rich contextual metadata
     * @param tags optional semantic tags
     */
    void remember(String id, String text, float[] vector, MemoryType type, MemorySource source, RememberContext context, String... tags);

    /**
     * Ingest a memory item with a pre-computed vector embedding and default context.
     *
     * @param id unique memory identifier
     * @param text content of the memory
     * @param vector pre-computed embedding vector (if null, embedding will be generated)
     * @param type target cognitive tier
     * @param source origin source of the memory
     * @param tags optional semantic tags
     */
    default void remember(String id, String text, float[] vector, MemoryType type, MemorySource source, String... tags) {
        remember(id, text, vector, type, source, (RememberContext) null, tags);
    }

    void remember(String id, String text, MemoryType type, String... tags);

    String remember(String text, MemoryType type, MemorySource source, String... tags);

    String remember(String text, MemoryType type, MemorySource source, RememberHints hints, String... tags);

    String remember(String text, MemoryType type, MemorySource source, RememberContext context, String... tags);

    default String rememberFile(Path filePath, String text, MemoryType type, MemorySource source, String... tags) {
        String effectiveText = (text != null && !text.isBlank()) ? text : filePath.getFileName().toString();
        RememberContext context = RememberContext.builder()
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

    ImportanceResult estimateImportance(String text, RememberHints hints);

    default ImportanceResult estimateImportance(String text) {
        return estimateImportance(text, null);
    }

    default void updateChunkConfig(com.spectrayan.spector.commons.chunker.ChunkConfig config) {}
}
