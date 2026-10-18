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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.AssistantMessageView;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ChatSessionRecord;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ChatSessionSummary;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ChatTurnView;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ThinkingView;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.TokenUsageDto;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ToolCardView;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.UserMessageView;
import com.spectrayan.spector.synapse.agent.chat.service.ChatTranscriptPort;
import com.spectrayan.spector.synapse.error.SynapseNotFoundException;
import com.spectrayan.spector.synapse.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC implementation of {@link ChatTranscriptPort} using Spring's {@link JdbcClient}.
 *
 * <p>Coordinates durable session management, turn recording, and chronological event replay
 * strictly on the operational relational plane (ADR-0084), scoped per authenticated owner,
 * tenant, and namespace (Issue #1068).</p>
 */
@Repository
public class JdbcChatTranscriptAdapter implements ChatTranscriptPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcChatTranscriptAdapter.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public JdbcChatTranscriptAdapter(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = Objects.requireNonNull(jdbc, "JdbcClient must not be null");
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper must not be null");
    }

    private static String currentOwnerId() {
        String userId = SecurityUtils.getUserId();
        return (userId != null && !userId.isBlank()) ? userId : "default";
    }

    private static String currentTenantId() {
        String tenantId = SecurityUtils.getTenantId();
        return (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";
    }

    private static String currentNamespaceId() {
        if (MemoryScope.isNamespaceActive()) {
            String ns = MemoryScope.namespaceId();
            if (ns != null && !ns.isBlank()) {
                return ns;
            }
        }
        return "default";
    }

    @Override
    @Transactional
    public ChatSessionRecord createSession(String sessionId, String title) {
        return createSession(sessionId, title, currentOwnerId(), currentTenantId(), currentNamespaceId());
    }

    @Override
    @Transactional
    public ChatSessionRecord createSession(
            String sessionId,
            String title,
            String ownerAccountId,
            String tenantId,
            String namespaceId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Instant now = Instant.now();
        String effectiveTitle = (title != null && !title.isBlank()) ? title.trim() : "New Chat";
        String effOwner = (ownerAccountId != null && !ownerAccountId.isBlank()) ? ownerAccountId.trim() : currentOwnerId();
        String effTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId.trim() : currentTenantId();
        String effNamespace = (namespaceId != null && !namespaceId.isBlank()) ? namespaceId.trim() : currentNamespaceId();

        try {
            jdbc.sql("""
                    INSERT INTO CHAT_SESSION (id, title, status, archived, owner_account_id, tenant_id, namespace_id, created_at, updated_at)
                    VALUES (:id, :title, 'ACTIVE', FALSE, :ownerAccountId, :tenantId, :namespaceId, :createdAt, :updatedAt)
                    """)
                    .param("id", sessionId)
                    .param("title", effectiveTitle)
                    .param("ownerAccountId", effOwner)
                    .param("tenantId", effTenant)
                    .param("namespaceId", effNamespace)
                    .param("createdAt", Timestamp.from(now))
                    .param("updatedAt", Timestamp.from(now))
                    .update();

            return new ChatSessionRecord(sessionId, effectiveTitle, "ACTIVE", false, effOwner, effTenant, effNamespace, now, now);
        } catch (DataAccessException e) {
            log.error("[JdbcTranscript] Failed to create session '{}'", sessionId, e);
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, e, "session_id", "Failed to create chat session: " + sessionId);
        }
    }

    @Override
    public List<ChatSessionSummary> listSessions(int limit) {
        int effLimit = limit > 0 ? Math.min(limit, 100) : 20;
        String ownerAccountId = currentOwnerId();
        String tenantId = currentTenantId();

        String sql = """
                SELECT s.id, s.title, s.updated_at,
                       (SELECT COUNT(*) FROM CHAT_TURN t WHERE t.session_id = s.id) AS turn_count,
                       (SELECT e.payload_json FROM CHAT_EVENT e
                        JOIN CHAT_TURN t ON e.turn_id = t.id
                        WHERE t.session_id = s.id AND e.type = 'user'
                        ORDER BY t.seq ASC, e.seq ASC LIMIT 1) AS first_prompt_json
                FROM CHAT_SESSION s
                WHERE s.archived = FALSE
                  AND s.owner_account_id = :ownerAccountId
                  AND s.tenant_id = :tenantId
                ORDER BY s.updated_at DESC
                LIMIT :limit
                """;

        return jdbc.sql(sql)
                .param("ownerAccountId", ownerAccountId)
                .param("tenantId", tenantId)
                .param("limit", effLimit)
                .query((rs, rowNum) -> {
                    String id = rs.getString("id");
                    String title = rs.getString("title");
                    Timestamp updatedAt = rs.getTimestamp("updated_at");
                    int turnCount = rs.getInt("turn_count");
                    String firstPromptJson = rs.getString("first_prompt_json");

                    String preview = title;
                    if (firstPromptJson != null && !firstPromptJson.isBlank()) {
                        try {
                            Map<String, Object> map = mapper.readValue(firstPromptJson, MAP_TYPE);
                            Object text = map.get("text");
                            if (text != null) {
                                String s = text.toString();
                                preview = s.length() > 100 ? s.substring(0, 100) + "..." : s;
                            }
                        } catch (Exception ignored) {
                            // Fallback to title on unparseable JSON
                        }
                    }

                    return new ChatSessionSummary(
                            id,
                            preview != null ? preview : "New Chat",
                            updatedAt != null ? updatedAt.toInstant() : Instant.now(),
                            turnCount
                    );
                })
                .list();
    }

    @Override
    public Optional<ChatSessionRecord> getSession(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        return jdbc.sql("""
                SELECT id, title, status, archived, owner_account_id, tenant_id, namespace_id, created_at, updated_at
                FROM CHAT_SESSION
                WHERE id = :id
                  AND owner_account_id = :ownerAccountId
                  AND tenant_id = :tenantId
                """)
                .param("id", sessionId)
                .param("ownerAccountId", currentOwnerId())
                .param("tenantId", currentTenantId())
                .query(this::mapSessionRecord)
                .optional();
    }

    @Override
    @Transactional
    public void renameSession(String sessionId, String title) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(title, "title must not be null");

        int updated = jdbc.sql("""
                UPDATE CHAT_SESSION
                SET title = :title, updated_at = :updatedAt
                WHERE id = :id
                  AND archived = FALSE
                  AND owner_account_id = :ownerAccountId
                  AND tenant_id = :tenantId
                """)
                .param("id", sessionId)
                .param("title", title.trim())
                .param("updatedAt", Timestamp.from(Instant.now()))
                .param("ownerAccountId", currentOwnerId())
                .param("tenantId", currentTenantId())
                .update();

        if (updated == 0) {
            throw new SynapseNotFoundException("ChatSession", sessionId);
        }
    }

    @Override
    @Transactional
    public void deleteSession(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        String ownerAccountId = currentOwnerId();
        String tenantId = currentTenantId();

        // Deleting from CHAT_SESSION cascades to CHAT_TURN and CHAT_EVENT via DB foreign key,
        // scoped strictly to the caller's owned session.
        int deleted = jdbc.sql("""
                DELETE FROM CHAT_SESSION
                WHERE id = :id
                  AND owner_account_id = :ownerAccountId
                  AND tenant_id = :tenantId
                """)
                .param("id", sessionId)
                .param("ownerAccountId", ownerAccountId)
                .param("tenantId", tenantId)
                .update();

        if (deleted > 0) {
            // Clean up checkpoints explicitly for thread_id only when the caller owned the session
            jdbc.sql("DELETE FROM GRAPH_CHECKPOINT WHERE thread_id = :threadId")
                    .param("threadId", sessionId)
                    .update();
        }

        log.info("[JdbcTranscript] Deleted operational session '{}' for owner='{}' tenant='{}' (rows={})",
                sessionId, ownerAccountId, tenantId, deleted);
    }

    @Override
    @Transactional
    public void startTurn(String turnId, String sessionId, int seq, String model, int primedCount) {
        if (getSession(sessionId).isEmpty()) {
            throw new SynapseNotFoundException("ChatSession", sessionId);
        }
        Instant now = Instant.now();
        jdbc.sql("""
                INSERT INTO CHAT_TURN (id, session_id, seq, status, model, primed_count, input_tokens, output_tokens, latency_ms, created_at)
                VALUES (:id, :sessionId, :seq, 'RUNNING', :model, :primedCount, 0, 0, 0, :createdAt)
                """)
                .param("id", turnId)
                .param("sessionId", sessionId)
                .param("seq", seq)
                .param("model", model)
                .param("primedCount", primedCount)
                .param("createdAt", Timestamp.from(now))
                .update();

        jdbc.sql("""
                UPDATE CHAT_SESSION
                SET updated_at = :now
                WHERE id = :id
                  AND owner_account_id = :ownerAccountId
                  AND tenant_id = :tenantId
                """)
                .param("id", sessionId)
                .param("now", Timestamp.from(now))
                .param("ownerAccountId", currentOwnerId())
                .param("tenantId", currentTenantId())
                .update();
    }

    @Override
    @Transactional
    public void updateTurnStatus(String turnId, String status, int inTokens, int outTokens, long latencyMs) {
        Instant now = Instant.now();
        jdbc.sql("""
                UPDATE CHAT_TURN
                SET status = :status, input_tokens = :inTokens, output_tokens = :outTokens, latency_ms = :latencyMs
                WHERE id = :id
                """)
                .param("id", turnId)
                .param("status", status)
                .param("inTokens", inTokens)
                .param("outTokens", outTokens)
                .param("latencyMs", latencyMs)
                .update();

        jdbc.sql("""
                UPDATE CHAT_SESSION
                SET updated_at = :now
                WHERE id = (SELECT session_id FROM CHAT_TURN WHERE id = :turnId)
                """)
                .param("turnId", turnId)
                .param("now", Timestamp.from(now))
                .update();
    }

    @Override
    @Transactional
    public void appendEvent(String eventId, String turnId, int seq, String type, String payloadJson) {
        jdbc.sql("""
                INSERT INTO CHAT_EVENT (id, turn_id, seq, type, payload_json, created_at)
                VALUES (:id, :turnId, :seq, :type, :payloadJson, :createdAt)
                """)
                .param("id", eventId)
                .param("turnId", turnId)
                .param("seq", seq)
                .param("type", type)
                .param("payloadJson", payloadJson)
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }

    @Override
    public int countTurns(String sessionId) {
        Integer count = jdbc.sql("""
                SELECT COUNT(*)
                FROM CHAT_TURN t
                JOIN CHAT_SESSION s ON t.session_id = s.id
                WHERE t.session_id = :sessionId
                  AND s.owner_account_id = :ownerAccountId
                  AND s.tenant_id = :tenantId
                """)
                .param("sessionId", sessionId)
                .param("ownerAccountId", currentOwnerId())
                .param("tenantId", currentTenantId())
                .query(Integer.class)
                .single();
        return count != null ? count : 0;
    }

    @Override
    public List<ChatTurnView> loadTurns(String sessionId) {
        record TurnRow(String id, int seq, String status, int inTokens, int outTokens, int primedCount) {}

        List<TurnRow> turnRows = jdbc.sql("""
                SELECT t.id, t.seq, t.status, t.input_tokens, t.output_tokens, t.primed_count
                FROM CHAT_TURN t
                JOIN CHAT_SESSION s ON t.session_id = s.id
                WHERE t.session_id = :sessionId
                  AND s.owner_account_id = :ownerAccountId
                  AND s.tenant_id = :tenantId
                ORDER BY t.seq ASC
                """)
                .param("sessionId", sessionId)
                .param("ownerAccountId", currentOwnerId())
                .param("tenantId", currentTenantId())
                .query((rs, rowNum) -> new TurnRow(
                        rs.getString("id"),
                        rs.getInt("seq"),
                        rs.getString("status"),
                        rs.getInt("input_tokens"),
                        rs.getInt("output_tokens"),
                        rs.getInt("primed_count")
                ))
                .list();

        if (turnRows.isEmpty()) {
            return List.of();
        }

        List<ChatTurnView> turns = new ArrayList<>();
        for (TurnRow turn : turnRows) {
            List<EventRow> events = jdbc.sql("""
                    SELECT id, seq, type, payload_json
                    FROM CHAT_EVENT
                    WHERE turn_id = :turnId
                    ORDER BY seq ASC
                    """)
                    .param("turnId", turn.id)
                    .query((rs, rowNum) -> new EventRow(
                            rs.getString("id"),
                            rs.getInt("seq"),
                            rs.getString("type"),
                            rs.getString("payload_json")
                    ))
                    .list();

            turns.add(reconstructTurn(turn.id, turn.seq, turn.status, turn.inTokens, turn.outTokens, turn.primedCount, events));
        }

        return turns;
    }

    private record EventRow(String id, int seq, String type, String payloadJson) {}

    private ChatTurnView reconstructTurn(
            String turnId, int seq, String status, int inTokens, int outTokens, int primedCount, List<EventRow> events) {

        String userText = "";
        StringBuilder thinkingText = new StringBuilder();
        long thinkingElapsedMs = 0;
        StringBuilder assistantText = new StringBuilder();
        Map<String, ToolExecutionBuilder> toolBuilders = new LinkedHashMap<>();

        for (EventRow ev : events) {
            try {
                Map<String, Object> payload = mapper.readValue(ev.payloadJson, MAP_TYPE);
                switch (ev.type) {
                    case "user" -> {
                        Object t = payload.get("text");
                        if (t != null) userText = t.toString();
                    }
                    case "thinking" -> {
                        Object t = payload.get("text");
                        if (t != null) thinkingText.append(t);
                        Object el = payload.get("elapsedMs");
                        if (el instanceof Number n) thinkingElapsedMs = n.longValue();
                    }
                    case "token", "content" -> {
                        Object t = payload.get("text");
                        if (t != null) assistantText.append(t);
                    }
                    case "tool_call" -> {
                        String callId = (String) payload.get("callId");
                        String name = (String) payload.get("name");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> args = (Map<String, Object>) payload.get("arguments");
                        toolBuilders.put(callId, new ToolExecutionBuilder(callId, name, args));
                    }
                    case "tool_result" -> {
                        String callId = (String) payload.get("callId");
                        ToolExecutionBuilder b = toolBuilders.get(callId);
                        if (b != null) {
                            b.status = (String) payload.getOrDefault("status", "success");
                            b.preview = (String) payload.getOrDefault("preview", "");
                            Object tr = payload.get("truncated");
                            if (tr instanceof Boolean bool) b.truncated = bool;
                            Object el = payload.get("elapsedMs");
                            if (el instanceof Number n) b.elapsedMs = n.longValue();
                        }
                    }
                    case "done" -> {
                        if (inTokens == 0 && outTokens == 0 && payload.containsKey("usage")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> u = (Map<String, Object>) payload.get("usage");
                            if (u != null) {
                                inTokens = ((Number) u.getOrDefault("inputTokens", 0)).intValue();
                                outTokens = ((Number) u.getOrDefault("outputTokens", 0)).intValue();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[JdbcTranscript] Failed to parse event {}: {}", ev.id, e.getMessage());
            }
        }

        List<ToolCardView> toolViews = toolBuilders.values().stream()
                .map(ToolExecutionBuilder::build)
                .toList();

        return new ChatTurnView(
                turnId,
                seq,
                status,
                new UserMessageView(userText),
                new ThinkingView(thinkingText.toString(), thinkingElapsedMs),
                toolViews,
                new AssistantMessageView(assistantText.toString()),
                TokenUsageDto.of(inTokens, outTokens),
                primedCount
        );
    }

    private static class ToolExecutionBuilder {
        final String callId;
        final String name;
        final Map<String, Object> args;
        String status = "running";
        String preview = "";
        boolean truncated = false;
        long elapsedMs = 0;

        ToolExecutionBuilder(String callId, String name, Map<String, Object> args) {
            this.callId = callId;
            this.name = name;
            this.args = args != null ? args : Map.of();
        }

        ToolCardView build() {
            return new ToolCardView(callId, name, args, status, preview, truncated, elapsedMs);
        }
    }

    private ChatSessionRecord mapSessionRecord(ResultSet rs, int rowNum) throws SQLException {
        return new ChatSessionRecord(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("status"),
                rs.getBoolean("archived"),
                rs.getString("owner_account_id"),
                rs.getString("tenant_id"),
                rs.getString("namespace_id"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
