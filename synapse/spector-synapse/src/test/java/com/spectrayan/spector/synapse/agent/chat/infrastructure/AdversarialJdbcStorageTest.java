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
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ChatSessionSummary;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ChatTurnView;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.langchain4j.serializer.std.LC4jStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Adversarial Empirical Verification: JdbcChatTranscriptAdapter & JdbcCheckpointSaver")
class AdversarialJdbcStorageTest {

    private JdbcClient jdbc;
    private JdbcChatTranscriptAdapter transcriptAdapter;
    private JdbcCheckpointSaver checkpointSaver;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:adversarial-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");

        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        jdbc = JdbcClient.create(ds);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        transcriptAdapter = new JdbcChatTranscriptAdapter(jdbc, objectMapper);

        var stateSerializer = new LC4jStateSerializer<>(AgentState::new);
        checkpointSaver = new JdbcCheckpointSaver(jdbc, stateSerializer);
    }

    // =========================================================================
    // 1. CONCURRENCY CHALLENGES
    // =========================================================================

    @Nested
    @DisplayName("1. Concurrency & Race Conditions")
    class ConcurrencyTests {

        @Test
        @DisplayName("Race Condition: Concurrent saveCheckpoint calls on NON-EXISTENT thread ID")
        void testConcurrentInitialSaveCheckpointOnSameThread() throws Exception {
            int threadCount = 16;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            String targetThreadId = "race-thread-initial";
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await(5, TimeUnit.SECONDS);
                        byte[] state = ("payload-" + idx).getBytes();
                        checkpointSaver.saveCheckpoint(targetThreadId, "cp-" + idx, state);
                        successCount.incrementAndGet();
                    } catch (Throwable t) {
                        failureCount.incrementAndGet();
                        exceptions.add(t);
                    }
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
            executor.shutdown();

            System.out.println("Concurrent Initial Save Results: Success=" + successCount.get()
                    + ", Failures=" + failureCount.get());

            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(failureCount.get()).isZero();
            assertThat(exceptions).isEmpty();
            // Verify that at least one row was persisted in the database
            Optional<byte[]> loaded = checkpointSaver.loadCheckpoint(targetThreadId);
            assertThat(loaded).isPresent();
        }

        @Test
        @DisplayName("Race Condition: Concurrent saver.put calls on NON-EXISTENT thread ID")
        void testConcurrentInitialPutOnSameThread() throws Exception {
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            String targetThreadId = "race-thread-put";
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger duplicateKeyCount = new AtomicInteger(0);

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await(5, TimeUnit.SECONDS);
                        RunnableConfig config = RunnableConfig.builder().threadId(targetThreadId).build();
                        Checkpoint cp = Checkpoint.builder()
                                .id("cp-put-" + idx)
                                .nodeId("node")
                                .nextNodeId("next")
                                .state(Map.of("step", idx))
                                .build();
                        checkpointSaver.put(config, cp);
                        successCount.incrementAndGet();
                    } catch (org.springframework.dao.DuplicateKeyException dke) {
                        duplicateKeyCount.incrementAndGet();
                    } catch (Throwable t) {
                        // Other exceptions
                    }
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
            executor.shutdown();

            System.out.println("Concurrent Initial Put: Success=" + successCount.get()
                    + ", DuplicateKey=" + duplicateKeyCount.get());

            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(duplicateKeyCount.get()).isZero();
            Optional<byte[]> loaded = checkpointSaver.loadCheckpoint(targetThreadId);
            assertThat(loaded).isPresent();
        }

        @Test
        @DisplayName("Concurrent updates on EXISTING thread ID with 20 parallel threads")
        void testConcurrentUpdatesOnExistingThread() throws Exception {
            String targetThreadId = "thread-existing-race";
            checkpointSaver.saveCheckpoint(targetThreadId, "cp-init", "initial-data".getBytes());

            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            AtomicInteger successCount = new AtomicInteger(0);
            List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await(5, TimeUnit.SECONDS);
                        checkpointSaver.saveCheckpoint(targetThreadId, "cp-" + idx, ("data-" + idx).getBytes());
                        successCount.incrementAndGet();
                    } catch (Throwable t) {
                        exceptions.add(t);
                    }
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
            executor.shutdown();

            assertThat(exceptions).as("Concurrent updates on existing row must not throw exceptions").isEmpty();
            assertThat(successCount.get()).isEqualTo(threadCount);

            Optional<byte[]> loaded = checkpointSaver.loadCheckpoint(targetThreadId);
            assertThat(loaded).isPresent();
        }

        @Test
        @DisplayName("Concurrent saves and gets across 50 distinct threads in parallel")
        void testConcurrentMultiThreadIsolation() throws Exception {
            int threadCount = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                tasks.add(() -> {
                    startLatch.await();
                    String tId = "distinct-thread-" + idx;
                    RunnableConfig config = RunnableConfig.builder().threadId(tId).build();

                    Checkpoint cp = Checkpoint.builder()
                            .id("cp-" + idx)
                            .nodeId("node-" + idx)
                            .nextNodeId("next-" + idx)
                            .state(Map.of("threadIdx", idx, "name", "Worker-" + idx))
                            .build();

                    checkpointSaver.put(config, cp);

                    Optional<Checkpoint> fetched = checkpointSaver.get(config);
                    return fetched.isPresent()
                            && fetched.get().getId().equals("cp-" + idx)
                            && fetched.get().getState().get("threadIdx").equals(idx);
                });
            }

            List<Future<Boolean>> futures = new ArrayList<>();
            for (var task : tasks) {
                futures.add(executor.submit(task));
            }

            startLatch.countDown();

            for (var f : futures) {
                assertThat(f.get(10, TimeUnit.SECONDS)).isTrue();
            }
            executor.shutdown();

            int totalRows = jdbc.sql("SELECT COUNT(*) FROM GRAPH_CHECKPOINT").query(Integer.class).single();
            assertThat(totalRows).isEqualTo(threadCount);
        }

        @Test
        @DisplayName("Concurrent turn starting and event appending within the same session")
        void testConcurrentTurnAndEventAppends() throws Exception {
            String sessionId = "concurrent-session";
            transcriptAdapter.createSession(sessionId, "Concurrency Stress");

            int turns = 10;
            int eventsPerTurn = 5;
            ExecutorService executor = Executors.newFixedThreadPool(turns);
            CountDownLatch latch = new CountDownLatch(1);

            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < turns; t++) {
                final int turnSeq = t;
                final String turnId = "turn-" + t;
                futures.add(executor.submit(() -> {
                    latch.await();
                    transcriptAdapter.startTurn(turnId, sessionId, turnSeq, "model-x", 1);
                    for (int e = 0; e < eventsPerTurn; e++) {
                        transcriptAdapter.appendEvent("event-" + turnId + "-" + e, turnId, e, "token",
                                "{\"text\":\"chunk " + e + "\"}");
                    }
                    transcriptAdapter.updateTurnStatus(turnId, "DONE", 10, 20, 100);
                    return null;
                }));
            }

            latch.countDown();

            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
            executor.shutdown();

            List<ChatTurnView> reconstructed = transcriptAdapter.loadTurns(sessionId);
            assertThat(reconstructed).hasSize(turns);
            for (ChatTurnView tv : reconstructed) {
                assertThat(tv.status()).isEqualTo("DONE");
                assertThat(tv.assistant().text()).contains("chunk 0", "chunk 4");
            }
        }
    }

    // =========================================================================
    // 2. DEEP CASCADE DELETION CHALLENGES
    // =========================================================================

    @Nested
    @DisplayName("2. Deep Cascade Deletion & Zero-Orphan Invariant")
    class CascadeDeletionTests {

        @Test
        @DisplayName("Deep Cascade: 10 turns, 60 events, checkpoints; delete parent cleans 100% records")
        void testDeepCascadeCleanAllTables() {
            String victimSession = "session-victim";
            String controlSession = "session-control";

            transcriptAdapter.createSession(victimSession, "Victim Session");
            transcriptAdapter.createSession(controlSession, "Control Session");

            // Populate victim session with 10 turns and 6 events each (60 events total)
            for (int t = 0; t < 10; t++) {
                String turnId = "v-turn-" + t;
                transcriptAdapter.startTurn(turnId, victimSession, t, "qwen3.5", t);

                transcriptAdapter.appendEvent("v-ev-usr-" + t, turnId, 0, "user",
                        "{\"text\":\"Query " + t + "\"}");
                transcriptAdapter.appendEvent("v-ev-thk-" + t, turnId, 1, "thinking",
                        "{\"text\":\"Thinking " + t + "\",\"elapsedMs\":120}");
                transcriptAdapter.appendEvent("v-ev-tc-" + t, turnId, 2, "tool_call",
                        "{\"callId\":\"call-" + t + "\",\"name\":\"search\",\"arguments\":{\"q\":\"val\"}}");
                transcriptAdapter.appendEvent("v-ev-tr-" + t, turnId, 3, "tool_result",
                        "{\"callId\":\"call-" + t + "\",\"status\":\"success\",\"preview\":\"found\"}");
                transcriptAdapter.appendEvent("v-ev-tok-" + t, turnId, 4, "token",
                        "{\"text\":\"Answer " + t + "\"}");
                transcriptAdapter.appendEvent("v-ev-dn-" + t, turnId, 5, "done",
                        "{\"usage\":{\"inputTokens\":50,\"outputTokens\":25}}");

                transcriptAdapter.updateTurnStatus(turnId, "COMPLETED", 50, 25, 200);
            }

            // Populate control session with 2 turns and 2 events each
            for (int t = 0; t < 2; t++) {
                String turnId = "c-turn-" + t;
                transcriptAdapter.startTurn(turnId, controlSession, t, "qwen3.5", 0);
                transcriptAdapter.appendEvent("c-ev-usr-" + t, turnId, 0, "user", "{\"text\":\"Hello control\"}");
                transcriptAdapter.appendEvent("c-ev-tok-" + t, turnId, 1, "token", "{\"text\":\"Hi!\"}");
                transcriptAdapter.updateTurnStatus(turnId, "COMPLETED", 10, 5, 50);
            }

            // Checkpoints for both
            checkpointSaver.saveCheckpoint(victimSession, "cp-victim-1", "state-victim".getBytes());
            checkpointSaver.saveCheckpoint(controlSession, "cp-control-1", "state-control".getBytes());

            // Pre-delete assertion
            assertThat(jdbc.sql("SELECT COUNT(*) FROM CHAT_SESSION").query(Integer.class).single()).isEqualTo(2);
            assertThat(jdbc.sql("SELECT COUNT(*) FROM CHAT_TURN").query(Integer.class).single()).isEqualTo(12);
            assertThat(jdbc.sql("SELECT COUNT(*) FROM CHAT_EVENT").query(Integer.class).single()).isEqualTo(64);
            assertThat(jdbc.sql("SELECT COUNT(*) FROM GRAPH_CHECKPOINT").query(Integer.class).single()).isEqualTo(2);

            // DELETE VICTIM SESSION
            transcriptAdapter.deleteSession(victimSession);

            // POST-DELETE ASSERTIONS: 100% cascade cleanup
            assertThat(transcriptAdapter.getSession(victimSession)).isEmpty();

            int remainingSessions = jdbc.sql("SELECT COUNT(*) FROM CHAT_SESSION").query(Integer.class).single();
            int remainingTurns = jdbc.sql("SELECT COUNT(*) FROM CHAT_TURN").query(Integer.class).single();
            int remainingEvents = jdbc.sql("SELECT COUNT(*) FROM CHAT_EVENT").query(Integer.class).single();
            int remainingCheckpoints = jdbc.sql("SELECT COUNT(*) FROM GRAPH_CHECKPOINT").query(Integer.class).single();

            // Victim records must be exactly 0
            int orphanTurns = jdbc.sql("SELECT COUNT(*) FROM CHAT_TURN WHERE session_id = :id")
                    .param("id", victimSession).query(Integer.class).single();
            int orphanEvents = jdbc.sql("SELECT COUNT(*) FROM CHAT_EVENT WHERE id LIKE 'v-ev-%'")
                    .query(Integer.class).single();
            int orphanCheckpoints = jdbc.sql("SELECT COUNT(*) FROM GRAPH_CHECKPOINT WHERE thread_id = :id")
                    .param("id", victimSession).query(Integer.class).single();

            assertThat(orphanTurns).as("Victim turns count must be 0").isEqualTo(0);
            assertThat(orphanEvents).as("Victim events count must be 0").isEqualTo(0);
            assertThat(orphanCheckpoints).as("Victim checkpoints count must be 0").isEqualTo(0);

            // Control session must remain 100% intact
            assertThat(remainingSessions).isEqualTo(1);
            assertThat(remainingTurns).isEqualTo(2);
            assertThat(remainingEvents).isEqualTo(4);
            assertThat(remainingCheckpoints).isEqualTo(1);

            List<ChatTurnView> controlTurns = transcriptAdapter.loadTurns(controlSession);
            assertThat(controlTurns).hasSize(2);
            assertThat(controlTurns.get(0).user().text()).isEqualTo("Hello control");
            assertThat(controlTurns.get(0).assistant().text()).isEqualTo("Hi!");
        }

        @Test
        @DisplayName("Idempotent Deletion: calling deleteSession multiple times or on non-existent session is safe")
        void testIdempotentDeleteSession() {
            String nonExistent = "does-not-exist-" + System.nanoTime();
            assertThatCode(() -> transcriptAdapter.deleteSession(nonExistent)).doesNotThrowAnyException();

            String s1 = "session-to-double-delete";
            transcriptAdapter.createSession(s1, "Temp");
            transcriptAdapter.deleteSession(s1);
            assertThatCode(() -> transcriptAdapter.deleteSession(s1)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Cascade cleanup on turn without events, and empty session")
        void testEmptySessionCascade() {
            String emptySession = "empty-session";
            transcriptAdapter.createSession(emptySession, "Empty");
            transcriptAdapter.deleteSession(emptySession);

            assertThat(transcriptAdapter.getSession(emptySession)).isEmpty();
        }
    }

    // =========================================================================
    // 3. MALFORMED / CORRUPT DATA HANDLING
    // =========================================================================

    @Nested
    @DisplayName("3. Corrupt BLOBs & Malformed JSON Resilience")
    class CorruptDataTests {

        @Test
        @DisplayName("Corrupt JSON in CHAT_EVENT payload: loadTurns does not crash, ignores bad events")
        void testMalformedJsonInChatEvent() {
            String sessionId = "malformed-json-session";
            transcriptAdapter.createSession(sessionId, "Malformed JSON Test");
            transcriptAdapter.startTurn("t-bad", sessionId, 0, "test-model", 0);

            // Event 0: valid user prompt
            transcriptAdapter.appendEvent("e-0", "t-bad", 0, "user", "{\"text\":\"Valid Prompt\"}");
            // Event 1: malformed / unparseable JSON syntax
            transcriptAdapter.appendEvent("e-1", "t-bad", 1, "thinking", "{broken-json-not-valid-syntax!!!");
            // Event 2: JSON array instead of JSON object
            transcriptAdapter.appendEvent("e-2", "t-bad", 2, "thinking", "[\"unexpected\",\"array\"]");
            // Event 3: valid thinking
            transcriptAdapter.appendEvent("e-3", "t-bad", 3, "thinking", "{\"text\":\"Recovered thinking\",\"elapsedMs\":50}");
            // Event 4: tool_call with null arguments and malformed callId
            transcriptAdapter.appendEvent("e-4", "t-bad", 4, "tool_call", "{\"name\":\"search\"}");
            // Event 5: valid token
            transcriptAdapter.appendEvent("e-5", "t-bad", 5, "token", "{\"text\":\"Visible answer\"}");
            // Event 6: completely empty string
            transcriptAdapter.appendEvent("e-6", "t-bad", 6, "token", "");
            // Event 7: valid done
            transcriptAdapter.appendEvent("e-7", "t-bad", 7, "done", "{\"usage\":{\"inputTokens\":10,\"outputTokens\":20}}");

            List<ChatTurnView> turns = transcriptAdapter.loadTurns(sessionId);
            assertThat(turns).hasSize(1);
            ChatTurnView turn = turns.get(0);

            assertThat(turn.user().text()).isEqualTo("Valid Prompt");
            assertThat(turn.thinking().text()).isEqualTo("Recovered thinking");
            assertThat(turn.assistant().text()).isEqualTo("Visible answer");
            assertThat(turn.usage().inputTokens()).isEqualTo(10);
            assertThat(turn.usage().outputTokens()).isEqualTo(20);
        }

        @Test
        @DisplayName("Corrupt first_prompt_json in listSessions falls back to session title")
        void testMalformedFirstPromptJsonFallback() {
            String sessionId = "bad-prompt-session";
            transcriptAdapter.createSession(sessionId, "Fallback Title");
            transcriptAdapter.startTurn("t-p", sessionId, 0, "model", 0);

            // User event has corrupt JSON
            transcriptAdapter.appendEvent("e-corrupt", "t-p", 0, "user", "Corrupted raw text string not json");

            List<ChatSessionSummary> summaries = transcriptAdapter.listSessions(10);
            assertThat(summaries).isNotEmpty();

            ChatSessionSummary summary = summaries.stream()
                    .filter(s -> s.sessionId().equals(sessionId))
                    .findFirst()
                    .orElseThrow();

            assertThat(summary.preview()).isEqualTo("Fallback Title");
        }

        @Test
        @DisplayName("Direct BLOB load with corrupt, random, or zero-byte data")
        void testDirectBlobCorruption() {
            String threadId = "corrupt-blob-thread";
            byte[] garbage = new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF, 0x00, 0x42};

            checkpointSaver.saveCheckpoint(threadId, "cp-garbage", garbage);

            Optional<byte[]> loaded = checkpointSaver.loadCheckpoint(threadId);
            assertThat(loaded).isPresent();
            assertThat(loaded.get()).isEqualTo(garbage);

            // Empty BLOB
            checkpointSaver.saveCheckpoint(threadId, "cp-empty", new byte[0]);
            Optional<byte[]> loadedEmpty = checkpointSaver.loadCheckpoint(threadId);
            assertThat(loadedEmpty).isPresent();
            assertThat(loadedEmpty.get()).isEmpty();
        }

        @Test
        @DisplayName("Deserialization of corrupt BLOB via CheckpointSaver throws informative exception")
        void testDeserializationOfCorruptBlobFailsExplicitly() {
            String threadId = "bad-serializer-blob";
            byte[] corruptBytes = "this is not a valid serialized Checkpoint list".getBytes();

            // Insert directly into DB
            jdbc.sql("INSERT INTO GRAPH_CHECKPOINT (thread_id, checkpoint_id, state, written_at) VALUES (:t, 'cp-bad', :b, CURRENT_TIMESTAMP)")
                    .param("t", threadId)
                    .param("b", corruptBytes)
                    .update();

            RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

            // CheckpointSaver.get() calls loadCheckpoints() which attempts deserialization
            assertThatThrownBy(() -> checkpointSaver.get(config))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Extremely large event payload (100 KiB) is stored and loaded accurately")
        void testLargeEventPayload() {
            String sessionId = "large-payload-session";
            transcriptAdapter.createSession(sessionId, "Large Payload");
            transcriptAdapter.startTurn("t-large", sessionId, 0, "model", 0);

            String largeText = "A".repeat(100_000);
            String payload = "{\"text\":\"" + largeText + "\"}";

            transcriptAdapter.appendEvent("e-large", "t-large", 0, "token", payload);

            List<ChatTurnView> turns = transcriptAdapter.loadTurns(sessionId);
            assertThat(turns).hasSize(1);
            assertThat(turns.get(0).assistant().text()).hasSize(100_000);
        }

        @Test
        @DisplayName("Type mismatch in tool_call arguments (string/array instead of Map) is handled safely")
        void testTypeMismatchInToolArguments() {
            String sessionId = "type-mismatch-session";
            transcriptAdapter.createSession(sessionId, "Type Mismatch");
            transcriptAdapter.startTurn("t-mismatch", sessionId, 0, "model", 0);

            // tool_call where arguments is a raw String instead of Map
            transcriptAdapter.appendEvent("e-bad-args-1", "t-mismatch", 0, "tool_call",
                    "{\"callId\":\"c1\",\"name\":\"calc\",\"arguments\":\"arg-as-string\"}");
            // tool_call where arguments is a List instead of Map
            transcriptAdapter.appendEvent("e-bad-args-2", "t-mismatch", 1, "tool_call",
                    "{\"callId\":\"c2\",\"name\":\"calc\",\"arguments\":[1,2,3]}");
            // valid token
            transcriptAdapter.appendEvent("e-valid", "t-mismatch", 2, "token", "{\"text\":\"done\"}");

            // Reconstructing turn must not throw ClassCastException to caller
            List<ChatTurnView> turns = transcriptAdapter.loadTurns(sessionId);
            assertThat(turns).hasSize(1);
            assertThat(turns.get(0).assistant().text()).isEqualTo("done");
        }

        @Test
        @DisplayName("Foreign key constraints prevent orphan turns and events from entering database")
        void testForeignKeyEnforcement() {
            // Inserting a turn with non-existent session_id must fail via foreign key
            assertThatThrownBy(() -> transcriptAdapter.startTurn("t-orphan", "non-existent-session", 0, "model", 0))
                    .isInstanceOf(DataAccessException.class);

            // Inserting an event with non-existent turn_id must fail via foreign key
            assertThatThrownBy(() -> transcriptAdapter.appendEvent("e-orphan", "non-existent-turn", 0, "token", "{}"))
                    .isInstanceOf(DataAccessException.class);
        }
    }
}
