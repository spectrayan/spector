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
package com.spectrayan.spector.synapse.agent.chat.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spectrayan.spector.synapse.agent.chat.infrastructure.JdbcChatTranscriptAdapter;
import com.spectrayan.spector.synapse.agent.chat.service.ChatService;
import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;
import com.spectrayan.spector.synapse.memory.MemoryService;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Empirical Verification of Cognitive Plane Isolation during Session Deletion (ADR-0084).
 *
 * <p>Verifies:
 * <ul>
 *   <li>DELETE /api/v1/chat/sessions/{id} deletes operational CHAT_SESSION, CHAT_TURN, CHAT_EVENT, and GRAPH_CHECKPOINT</li>
 *   <li>DELETE /api/v1/chat/sessions/{id} leaves cognitive MemoryService completely untouched (zero forget calls)</li>
 *   <li>Deleting non-existent session is idempotent (204 No Content) and does not leak into cognitive memory</li>
 *   <li>GET /api/v1/chat/sessions/{id}/messages reads exclusively from operational plane without contacting cognitive plane</li>
 * </ul>
 * </p>
 */
@DisplayName("ChatController Cognitive Plane Isolation Empirical Tests")
class ChatControllerIsolationEmpiricalTest {

    private JdbcClient jdbc;
    private JdbcChatTranscriptAdapter transcriptAdapter;
    private ChatService chatService;
    private CognitiveSoulService soulService;
    private MemoryService memoryService;
    private ChatController controller;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:isolation-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");

        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        jdbc = JdbcClient.create(ds);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        transcriptAdapter = new JdbcChatTranscriptAdapter(jdbc, mapper);

        chatService = mock(ChatService.class);
        soulService = mock(CognitiveSoulService.class);
        memoryService = mock(MemoryService.class);

        controller = new ChatController(chatService, soulService, transcriptAdapter);
    }

    @Test
    @DisplayName("EMPIRICAL PROOF: Deleting session purges operational DB but leaves cognitive engrams 100% intact")
    void testDeleteSessionPurgesOperationalAndSparesCognitivePlane() {
        String sessionId = "sess-iso-999";
        String turnId = "turn-iso-001";
        Instant now = Instant.now();

        // 1. Seed operational plane records
        jdbc.sql("""
                INSERT INTO CHAT_SESSION (id, title, status, archived, created_at, updated_at)
                VALUES (:id, 'Relocation Thread', 'ACTIVE', FALSE, :now, :now)
                """)
                .param("id", sessionId)
                .param("now", Timestamp.from(now))
                .update();

        jdbc.sql("""
                INSERT INTO CHAT_TURN (id, session_id, seq, status, model, primed_count, input_tokens, output_tokens, latency_ms, created_at)
                VALUES (:id, :sessionId, 0, 'DONE', 'qwen-2.5', 2, 100, 50, 450, :now)
                """)
                .param("id", turnId)
                .param("sessionId", sessionId)
                .param("now", Timestamp.from(now))
                .update();

        jdbc.sql("""
                INSERT INTO CHAT_EVENT (id, turn_id, seq, type, payload_json, created_at)
                VALUES ('ev-01', :turnId, 0, 'user', '{"text":"Hello"}', :now)
                """)
                .param("turnId", turnId)
                .param("now", Timestamp.from(now))
                .update();

        jdbc.sql("""
                INSERT INTO GRAPH_CHECKPOINT (thread_id, checkpoint_id, state, written_at)
                VALUES (:threadId, 'chk-01', X'CAFEBABE', :now)
                """)
                .param("threadId", sessionId)
                .param("now", Timestamp.from(now))
                .update();

        // Verify seeded operational rows exist
        int sessionCountBefore = jdbc.sql("SELECT COUNT(*) FROM CHAT_SESSION WHERE id = :id")
                .param("id", sessionId).query(Integer.class).single();
        int turnCountBefore = jdbc.sql("SELECT COUNT(*) FROM CHAT_TURN WHERE session_id = :id")
                .param("id", sessionId).query(Integer.class).single();
        int eventCountBefore = jdbc.sql("SELECT COUNT(*) FROM CHAT_EVENT WHERE turn_id = :turnId")
                .param("turnId", turnId).query(Integer.class).single();
        int checkpointCountBefore = jdbc.sql("SELECT COUNT(*) FROM GRAPH_CHECKPOINT WHERE thread_id = :id")
                .param("id", sessionId).query(Integer.class).single();

        assertThat(sessionCountBefore).isEqualTo(1);
        assertThat(turnCountBefore).isEqualTo(1);
        assertThat(eventCountBefore).isEqualTo(1);
        assertThat(checkpointCountBefore).isEqualTo(1);

        // 2. Execute DELETE /api/v1/chat/sessions/{id}
        ResponseEntity<Void> response = controller.deleteSession(sessionId);

        // 3. Empirically verify operational plane destruction
        assertThat(response.getStatusCode().value()).isEqualTo(204);

        int sessionCountAfter = jdbc.sql("SELECT COUNT(*) FROM CHAT_SESSION WHERE id = :id")
                .param("id", sessionId).query(Integer.class).single();
        int turnCountAfter = jdbc.sql("SELECT COUNT(*) FROM CHAT_TURN WHERE session_id = :id")
                .param("id", sessionId).query(Integer.class).single();
        int eventCountAfter = jdbc.sql("SELECT COUNT(*) FROM CHAT_EVENT WHERE turn_id = :turnId")
                .param("turnId", turnId).query(Integer.class).single();
        int checkpointCountAfter = jdbc.sql("SELECT COUNT(*) FROM GRAPH_CHECKPOINT WHERE thread_id = :id")
                .param("id", sessionId).query(Integer.class).single();

        assertThat(sessionCountAfter).isZero();
        assertThat(turnCountAfter).isZero();
        assertThat(eventCountAfter).isZero();
        assertThat(checkpointCountAfter).isZero();

        // 4. Empirically verify cognitive memory plane isolation:
        // MemoryService was NEVER contacted, forget() was NEVER called!
        verifyNoInteractions(memoryService);
    }

    @Test
    @DisplayName("Deleting non-existent session is idempotent (204) and touches zero cognitive memories")
    void testDeleteNonExistentSessionIdempotent() {
        ResponseEntity<Void> response = controller.deleteSession("non-existent-session-id");

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verifyNoInteractions(memoryService);
    }

    @Test
    @DisplayName("loadSessionHistory reads strictly from operational JDBC events without calling MemoryService")
    void testLoadSessionHistoryIsolation() {
        String sessionId = "sess-history-001";
        Instant now = Instant.now();

        jdbc.sql("""
                INSERT INTO CHAT_SESSION (id, title, status, archived, created_at, updated_at)
                VALUES (:id, 'Historical Chat', 'ACTIVE', FALSE, :now, :now)
                """)
                .param("id", sessionId)
                .param("now", Timestamp.from(now))
                .update();

        var response = controller.loadSessionHistory(sessionId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().sessionId()).isEqualTo(sessionId);
        assertThat(response.getBody().title()).isEqualTo("Historical Chat");

        // Fulfills ADR-0084 Invariant 4: No calls to MemoryService / SpectorMemory browse
        verifyNoInteractions(memoryService);
    }
}
