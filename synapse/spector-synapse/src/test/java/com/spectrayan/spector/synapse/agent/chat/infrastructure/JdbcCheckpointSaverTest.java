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

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.langchain4j.serializer.std.LC4jStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JdbcCheckpointSaver Tests (Milestone 1)")
class JdbcCheckpointSaverTest {

    private JdbcClient jdbc;
    private JdbcCheckpointSaver saver;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:checkpoint-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");

        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        jdbc = JdbcClient.create(ds);
        var stateSerializer = new LC4jStateSerializer<>(AgentState::new);
        saver = new JdbcCheckpointSaver(jdbc, stateSerializer);
    }

    @Test
    @DisplayName("put saves checkpoint and returns updated RunnableConfig with checkpointId")
    void testPutCheckpoint() throws Exception {
        RunnableConfig config = RunnableConfig.builder().threadId("thread-100").build();

        Map<String, Object> stateMap = new HashMap<>();
        stateMap.put("counter", 42);
        stateMap.put("activeNode", "agent");

        Checkpoint checkpoint = Checkpoint.builder()
                .id("cp-001")
                .state(stateMap)
                .nodeId("agent")
                .nextNodeId("tools")
                .build();

        RunnableConfig returnedConfig = saver.put(config, checkpoint);

        assertThat(returnedConfig.checkPointId()).isPresent();
        assertThat(returnedConfig.checkPointId().get()).isEqualTo("cp-001");

        // Verify row in DB
        Optional<byte[]> blob = saver.loadCheckpoint("thread-100");
        assertThat(blob).isPresent();
        assertThat(blob.get().length).isGreaterThan(0);
    }

    @Test
    @DisplayName("get retrieves persisted Checkpoint accurately")
    void testGetCheckpoint() throws Exception {
        RunnableConfig config = RunnableConfig.builder().threadId("thread-200").build();

        Checkpoint checkpoint = Checkpoint.builder()
                .id("cp-002")
                .state(Map.of("message", "Hello World"))
                .nodeId("agent")
                .nextNodeId("tools")
                .build();

        saver.put(config, checkpoint);

        Optional<Checkpoint> loaded = saver.get(config);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getId()).isEqualTo("cp-002");
        assertThat(loaded.get().getNodeId()).isEqualTo("agent");
        assertThat(loaded.get().getNextNodeId()).isEqualTo("tools");
        assertThat(loaded.get().getState()).containsEntry("message", "Hello World");
    }

    @Test
    @DisplayName("thread isolation ensures checkpoints do not leak across threads")
    void testThreadIsolation() throws Exception {
        RunnableConfig configA = RunnableConfig.builder().threadId("thread-A").build();
        RunnableConfig configB = RunnableConfig.builder().threadId("thread-B").build();

        saver.put(configA, Checkpoint.builder().id("cp-A").nodeId("agent").nextNodeId("tools").state(Map.of("owner", "A")).build());
        saver.put(configB, Checkpoint.builder().id("cp-B").nodeId("agent").nextNodeId("tools").state(Map.of("owner", "B")).build());

        Optional<Checkpoint> getA = saver.get(configA);
        Optional<Checkpoint> getB = saver.get(configB);

        assertThat(getA).isPresent();
        assertThat(getA.get().getState()).containsEntry("owner", "A");

        assertThat(getB).isPresent();
        assertThat(getB.get().getState()).containsEntry("owner", "B");
    }

    @Test
    @DisplayName("release prunes checkpoint from database and returns Tag")
    void testReleaseCheckpoint() throws Exception {
        RunnableConfig config = RunnableConfig.builder().threadId("thread-300").build();

        saver.put(config, Checkpoint.builder().id("cp-003").nodeId("agent").nextNodeId("tools").state(Map.of("test", 1)).build());
        assertThat(saver.loadCheckpoint("thread-300")).isPresent();

        BaseCheckpointSaver.Tag tag = saver.release(config);
        assertThat(tag).isNotNull();
        assertThat(saver.loadCheckpoint("thread-300")).isEmpty();
        assertThat(saver.get(config)).isEmpty();
    }

    @Test
    @DisplayName("direct save, load, and prune operations succeed")
    void testDirectBlobOperations() {
        byte[] payload = new byte[]{1, 2, 3, 4, 5};
        saver.saveCheckpoint("direct-thread", "cp-direct", payload);

        Optional<byte[]> loaded = saver.loadCheckpoint("direct-thread");
        assertThat(loaded).isPresent();
        assertThat(loaded.get()).isEqualTo(payload);

        saver.pruneCheckpoint("direct-thread");
        assertThat(saver.loadCheckpoint("direct-thread")).isEmpty();
    }
}
