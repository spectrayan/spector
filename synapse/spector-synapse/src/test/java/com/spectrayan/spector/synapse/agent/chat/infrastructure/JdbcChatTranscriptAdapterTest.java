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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ChatSessionRecord;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ChatSessionSummary;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ChatTurnView;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ToolCardView;
import com.spectrayan.spector.synapse.error.SynapseNotFoundException;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JdbcChatTranscriptAdapter Tests (Milestone 1)")
class JdbcChatTranscriptAdapterTest {

    private JdbcClient jdbc;
    private JdbcChatTranscriptAdapter adapter;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:transcript-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");

        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        jdbc = JdbcClient.create(ds);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        adapter = new JdbcChatTranscriptAdapter(jdbc, objectMapper);
    }

    @Test
    @DisplayName("createSession persists session record with ACTIVE status")
    void testCreateSession() {
        var session = adapter.createSession("session-1", "Project Discussion");
        assertThat(session.id()).isEqualTo("session-1");
        assertThat(session.title()).isEqualTo("Project Discussion");
        assertThat(session.status()).isEqualTo("ACTIVE");
        assertThat(session.archived()).isFalse();

        Optional<ChatSessionRecord> retrieved = adapter.getSession("session-1");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().title()).isEqualTo("Project Discussion");
    }

    @Test
    @DisplayName("listSessions returns summaries ordered by updated_at with prompt preview")
    void testListSessions() {
        adapter.createSession("s1", "Alpha");
        adapter.createSession("s2", "Beta");

        adapter.startTurn("t1", "s1", 0, "qwen3.5", 2);
        adapter.appendEvent("e1", "t1", 0, "user", "{\"text\":\"What is Spector?\"}");

        List<ChatSessionSummary> summaries = adapter.listSessions(10);
        assertThat(summaries).hasSize(2);
        // s1 updated last due to startTurn
        assertThat(summaries.get(0).sessionId()).isEqualTo("s1");
        assertThat(summaries.get(0).preview()).isEqualTo("What is Spector?");
        assertThat(summaries.get(0).messageCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("renameSession updates title and updated_at")
    void testRenameSession() {
        adapter.createSession("s1", "Original Title");
        adapter.renameSession("s1", "Updated Title");

        var session = adapter.getSession("s1").orElseThrow();
        assertThat(session.title()).isEqualTo("Updated Title");

        assertThatThrownBy(() -> adapter.renameSession("non-existent", "Title"))
                .isInstanceOf(SynapseNotFoundException.class);
    }

    @Test
    @DisplayName("deleteSession cascades to turns, events, and checkpoints")
    void testDeleteSessionCascade() {
        adapter.createSession("s1", "To Delete");
        adapter.startTurn("t1", "s1", 0, "model", 0);
        adapter.appendEvent("e1", "t1", 0, "user", "{\"text\":\"Hi\"}");

        // Add dummy checkpoint for s1
        jdbc.sql("INSERT INTO GRAPH_CHECKPOINT (thread_id, checkpoint_id, state, written_at) VALUES ('s1', 'cp1', X'0102', CURRENT_TIMESTAMP)").update();

        adapter.deleteSession("s1");

        assertThat(adapter.getSession("s1")).isEmpty();
        int turnCount = jdbc.sql("SELECT COUNT(*) FROM CHAT_TURN WHERE session_id = 's1'").query(Integer.class).single();
        assertThat(turnCount).isEqualTo(0);
        int eventCount = jdbc.sql("SELECT COUNT(*) FROM CHAT_EVENT WHERE turn_id = 't1'").query(Integer.class).single();
        assertThat(eventCount).isEqualTo(0);
        int cpCount = jdbc.sql("SELECT COUNT(*) FROM GRAPH_CHECKPOINT WHERE thread_id = 's1'").query(Integer.class).single();
        assertThat(cpCount).isEqualTo(0);
    }

    @Test
    @DisplayName("loadTurns reconstructs full turn structure from chronological events")
    void testLoadTurnsReconstruction() {
        adapter.createSession("s1", "Chat");
        adapter.startTurn("t1", "s1", 0, "qwen3.5", 3);

        adapter.appendEvent("e0", "t1", 0, "user", "{\"text\":\"Recall Austin lease\"}");
        adapter.appendEvent("e1", "t1", 1, "thinking", "{\"text\":\"Analyzing user query...\",\"elapsedMs\":200}");
        adapter.appendEvent("e2", "t1", 2, "tool_call", "{\"callId\":\"c1\",\"name\":\"memory_recall\",\"arguments\":{\"query\":\"Austin lease\"}}");
        adapter.appendEvent("e3", "t1", 3, "tool_result", "{\"callId\":\"c1\",\"name\":\"memory_recall\",\"status\":\"success\",\"preview\":\"Found lease\",\"elapsedMs\":150}");
        adapter.appendEvent("e4", "t1", 4, "token", "{\"text\":\"Your lease \"}");
        adapter.appendEvent("e5", "t1", 5, "token", "{\"text\":\"expires in August.\"}");
        adapter.appendEvent("e6", "t1", 6, "done", "{\"summary\":\"Completed\",\"usage\":{\"inputTokens\":120,\"outputTokens\":45}}");

        adapter.updateTurnStatus("t1", "DONE", 120, 45, 850);

        List<ChatTurnView> turns = adapter.loadTurns("s1");
        assertThat(turns).hasSize(1);
        ChatTurnView turn = turns.get(0);

        assertThat(turn.turnId()).isEqualTo("t1");
        assertThat(turn.status()).isEqualTo("DONE");
        assertThat(turn.primedMemories()).isEqualTo(3);
        assertThat(turn.user().text()).isEqualTo("Recall Austin lease");
        assertThat(turn.thinking().text()).isEqualTo("Analyzing user query...");
        assertThat(turn.thinking().elapsedMs()).isEqualTo(200);
        assertThat(turn.assistant().text()).isEqualTo("Your lease expires in August.");
        assertThat(turn.usage().inputTokens()).isEqualTo(120);
        assertThat(turn.usage().outputTokens()).isEqualTo(45);
        assertThat(turn.usage().totalTokens()).isEqualTo(165);

        assertThat(turn.tools()).hasSize(1);
        ToolCardView tool = turn.tools().get(0);
        assertThat(tool.callId()).isEqualTo("c1");
        assertThat(tool.name()).isEqualTo("memory_recall");
        assertThat(tool.status()).isEqualTo("success");
        assertThat(tool.preview()).isEqualTo("Found lease");
        assertThat(tool.elapsedMs()).isEqualTo(150);
        assertThat(tool.arguments()).containsEntry("query", "Austin lease");
    }

    @Test
    @DisplayName("countTurns returns accurate count of turns")
    void testCountTurns() {
        adapter.createSession("s1", "Chat");
        assertThat(adapter.countTurns("s1")).isEqualTo(0);

        adapter.startTurn("t1", "s1", 0, "model", 0);
        adapter.startTurn("t2", "s1", 1, "model", 0);
        assertThat(adapter.countTurns("s1")).isEqualTo(2);
    }
}
