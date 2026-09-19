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
package com.spectrayan.spector.synapse.agent.chat.infrastructure;

import com.spectrayan.spector.synapse.agent.chat.service.GraphCheckpointPort;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.AbstractCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.langchain4j.serializer.std.LC4jStateSerializer;
import org.bsc.langgraph4j.serializer.Serializer;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.std.CheckpointListSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC-backed implementation of LangGraph4j's {@link BaseCheckpointSaver} persisting serialized
 * checkpoints to the {@code GRAPH_CHECKPOINT} table on the operational plane (ADR-0084).
 */
@Repository
public class JdbcCheckpointSaver extends AbstractCheckpointSaver implements GraphCheckpointPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcCheckpointSaver.class);

    private final JdbcClient jdbc;
    private final Serializer<LinkedList<Checkpoint>> serializer;

    public JdbcCheckpointSaver(JdbcClient jdbc, Serializer<LinkedList<Checkpoint>> serializer) {
        this.jdbc = Objects.requireNonNull(jdbc, "JdbcClient must not be null");
        this.serializer = Objects.requireNonNull(serializer, "Serializer must not be null");
    }

    public JdbcCheckpointSaver(JdbcClient jdbc, StateSerializer<? extends AgentState> stateSerializer) {
        this(jdbc, new CheckpointListSerializer(stateSerializer));
    }

    @Autowired
    public JdbcCheckpointSaver(JdbcClient jdbc) {
        this(jdbc, createDefaultStateSerializer());
    }

    private static StateSerializer<AgentState> createDefaultStateSerializer() {
        var stateSerializer = new LC4jStateSerializer<>(AgentState::new);
        stateSerializer.mapper().register(dev.langchain4j.data.message.ImageContent.class,
                new com.spectrayan.spector.synapse.agent.graph.serializer.SafeImageContentSerializer());
        return stateSerializer;
    }

    // ═══════════════════════════════════════════════════════════════
    // AbstractCheckpointSaver Template Methods
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {
        String threadId = threadId(config);
        Optional<byte[]> stateBytes = loadCheckpoint(threadId);

        if (stateBytes.isEmpty() || stateBytes.get().length == 0) {
            return new LinkedList<>();
        }

        try {
            return serializer.bytesToObject(stateBytes.get());
        } catch (Exception e) {
            log.error("[JdbcCheckpoint] Failed to deserialize checkpoints for thread '{}'", threadId, e);
            throw e;
        }
    }

    @Override
    protected void insertedCheckpoint(
            RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        persistCheckpoints(threadId(config), checkpoint.getId(), checkpoints);
    }

    @Override
    protected void updatedCheckpoint(
            RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        persistCheckpoints(threadId(config), checkpoint.getId(), checkpoints);
    }

    @Override
    protected BaseCheckpointSaver.Tag releaseCheckpoints(
            RunnableConfig config, LinkedList<Checkpoint> checkpoints) throws Exception {
        String threadId = threadId(config);
        pruneCheckpoint(threadId);
        return new BaseCheckpointSaver.Tag(threadId, checkpoints);
    }

    private void persistCheckpoints(String threadId, String checkpointId, LinkedList<Checkpoint> checkpoints) throws Exception {
        byte[] bytes = serializer.objectToBytes(checkpoints);
        saveCheckpoint(threadId, checkpointId, bytes);
    }

    // ═══════════════════════════════════════════════════════════════
    // Direct BLOB Persistence (GraphCheckpointPort)
    // ═══════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void saveCheckpoint(String threadId, String checkpointId, byte[] stateBytes) {
        Objects.requireNonNull(threadId, "threadId must not be null");
        Objects.requireNonNull(checkpointId, "checkpointId must not be null");
        Objects.requireNonNull(stateBytes, "stateBytes must not be null");

        Instant now = Instant.now();

        int updated = jdbc.sql("""
                UPDATE GRAPH_CHECKPOINT
                SET checkpoint_id = :checkpointId, state = :state, written_at = :writtenAt
                WHERE thread_id = :threadId
                """)
                .param("checkpointId", checkpointId)
                .param("state", stateBytes)
                .param("writtenAt", Timestamp.from(now))
                .param("threadId", threadId)
                .update();

        if (updated == 0) {
            try {
                jdbc.sql("""
                        INSERT INTO GRAPH_CHECKPOINT (thread_id, checkpoint_id, state, written_at)
                        VALUES (:threadId, :checkpointId, :state, :writtenAt)
                        """)
                        .param("threadId", threadId)
                        .param("checkpointId", checkpointId)
                        .param("state", stateBytes)
                        .param("writtenAt", Timestamp.from(now))
                        .update();
            } catch (DataIntegrityViolationException ex) {
                // Another concurrent thread won the race to insert; update the existing row
                jdbc.sql("""
                        UPDATE GRAPH_CHECKPOINT
                        SET checkpoint_id = :checkpointId, state = :state, written_at = :writtenAt
                        WHERE thread_id = :threadId
                        """)
                        .param("checkpointId", checkpointId)
                        .param("state", stateBytes)
                        .param("writtenAt", Timestamp.from(now))
                        .param("threadId", threadId)
                        .update();
            }
        }

        log.debug("[JdbcCheckpoint] Saved checkpoint '{}' for thread '{}' ({} bytes)",
                checkpointId, threadId, stateBytes.length);
    }

    @Override
    public Optional<byte[]> loadCheckpoint(String threadId) {
        Objects.requireNonNull(threadId, "threadId must not be null");
        return jdbc.sql("SELECT state FROM GRAPH_CHECKPOINT WHERE thread_id = :threadId")
                .param("threadId", threadId)
                .query((rs, rowNum) -> rs.getBytes("state"))
                .optional();
    }

    @Override
    @Transactional
    public void pruneCheckpoint(String threadId) {
        Objects.requireNonNull(threadId, "threadId must not be null");
        jdbc.sql("DELETE FROM GRAPH_CHECKPOINT WHERE thread_id = :threadId")
                .param("threadId", threadId)
                .update();
        log.debug("[JdbcCheckpoint] Pruned checkpoint for thread '{}'", threadId);
    }
}
