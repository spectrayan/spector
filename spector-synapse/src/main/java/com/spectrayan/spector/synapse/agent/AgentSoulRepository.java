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
package com.spectrayan.spector.synapse.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC-backed repository for agent souls.
 *
 * <p>Uses {@link JdbcTemplate} with the H2 database configured in Synapse.
 * All JSON fields (personality, tools) are serialized as CLOB columns.</p>
 */
@Repository
public class AgentSoulRepository {

    private static final Logger log = LoggerFactory.getLogger(AgentSoulRepository.class);
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AgentSoulRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** Save or update an agent soul. */
    public AgentSoul save(AgentSoul soul) {
        String id = soul.id() != null ? soul.id() : UUID.randomUUID().toString();
        Instant now = Instant.now();

        String personalityJson = toJson(soul.personality());
        String toolsJson = toJson(soul.tools());

        int updated = jdbc.update("""
                MERGE INTO agent_souls (id, name, description, system_prompt, personality, model, tools, created_at, updated_at)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, soul.name(), soul.description(), soul.systemPrompt(),
                personalityJson, soul.model(), toolsJson,
                soul.createdAt() != null ? soul.createdAt() : now, now);

        log.info("[AgentSoul] Saved soul '{}' (id={})", soul.name(), id);
        return new AgentSoul(id, soul.name(), soul.description(), soul.systemPrompt(),
                soul.personality(), soul.model(), soul.tools(),
                soul.createdAt() != null ? soul.createdAt() : now, now);
    }

    /** Find an agent soul by ID. */
    public Optional<AgentSoul> findById(String id) {
        List<AgentSoul> results = jdbc.query(
                "SELECT * FROM agent_souls WHERE id = ?",
                new AgentSoulRowMapper(), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    /** List all agent souls. */
    public List<AgentSoul> findAll() {
        return jdbc.query("SELECT * FROM agent_souls ORDER BY created_at DESC",
                new AgentSoulRowMapper());
    }

    /** Delete an agent soul by ID. */
    public boolean delete(String id) {
        int deleted = jdbc.update("DELETE FROM agent_souls WHERE id = ?", id);
        return deleted > 0;
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private class AgentSoulRowMapper implements RowMapper<AgentSoul> {
        @Override
        public AgentSoul mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                String personalityJson = rs.getString("personality");
                String toolsJson = rs.getString("tools");

                Map<String, String> personality = personalityJson != null
                        ? mapper.readValue(personalityJson, MAP_TYPE) : Map.of();
                List<String> tools = toolsJson != null
                        ? mapper.readValue(toolsJson, LIST_TYPE) : List.of();

                return new AgentSoul(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("system_prompt"),
                        personality,
                        rs.getString("model"),
                        tools,
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                );
            } catch (Exception e) {
                throw new SQLException("Failed to map agent soul row", e);
            }
        }
    }
}
