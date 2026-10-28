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
package com.spectrayan.spector.synapse.idempotency;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.cluster.idempotency.IdempotencyPersistenceBackend;
import com.spectrayan.spector.cluster.idempotency.RequestFingerprint;
import com.spectrayan.spector.cluster.idempotency.StoredIdempotentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC-backed {@link IdempotencyPersistenceBackend} storing committed {@code 2xx} responses
 * in the {@code idempotency_records} table so idempotent replay survives process restarts.
 */
public final class JdbcIdempotencyPersistenceBackend implements IdempotencyPersistenceBackend {

    private static final Logger log = LoggerFactory.getLogger(JdbcIdempotencyPersistenceBackend.class);
    private static final TypeReference<Map<String, List<String>>> HEADERS_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcIdempotencyPersistenceBackend(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        ensureTableExists();
    }

    private void ensureTableExists() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS idempotency_records (
                        namespace_scope       VARCHAR(256)  NOT NULL,
                        idempotency_key       VARCHAR(256)  NOT NULL,
                        request_method        VARCHAR(16)   NOT NULL,
                        request_path          VARCHAR(1024) NOT NULL,
                        payload_sha256        VARCHAR(64)   NOT NULL,
                        status_code           INT           NOT NULL,
                        response_headers_json CLOB          NOT NULL,
                        response_body         BLOB          NOT NULL,
                        committed_at_ms       BIGINT        NOT NULL,
                        expires_at_ms         BIGINT        NOT NULL,
                        PRIMARY KEY (namespace_scope, idempotency_key)
                    )
                    """);
        } catch (Exception e) {
            log.debug("Could not initialize idempotency_records table automatically: {}", e.getMessage());
        }
    }

    @Override
    public Optional<PersistedEntry> findActive(String namespaceScope, String idempotencyKey, long nowEpochMs) {
        try {
            List<PersistedEntry> rows = jdbcTemplate.query(
                    """
                    SELECT request_method, request_path, payload_sha256,
                           status_code, response_headers_json, response_body,
                           committed_at_ms, expires_at_ms
                      FROM idempotency_records
                     WHERE namespace_scope = ?
                       AND idempotency_key = ?
                    """,
                    (rs, rowNum) -> {
                        String method = rs.getString("request_method");
                        String path = rs.getString("request_path");
                        String sha256 = rs.getString("payload_sha256");
                        int status = rs.getInt("status_code");
                        String headersJson = rs.getString("response_headers_json");
                        byte[] body = rs.getBytes("response_body");
                        long committedAt = rs.getLong("committed_at_ms");
                        long expiresAt = rs.getLong("expires_at_ms");
                        Map<String, List<String>> headers = parseHeaders(headersJson);
                        return new PersistedEntry(
                                namespaceScope,
                                idempotencyKey,
                                new RequestFingerprint(method, path, sha256),
                                new StoredIdempotentResponse(status, headers, body, committedAt),
                                expiresAt
                        );
                    },
                    namespaceScope,
                    idempotencyKey
            );

            if (rows.isEmpty()) {
                return Optional.empty();
            }
            PersistedEntry entry = rows.getFirst();
            if (nowEpochMs >= entry.expiresAtEpochMs()) {
                delete(namespaceScope, idempotencyKey);
                return Optional.empty();
            }
            return Optional.of(entry);
        } catch (Exception e) {
            log.warn("Failed reading idempotency record for scope='{}': {}", namespaceScope, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void save(PersistedEntry entry, int maxEntries, long nowEpochMs) {
        try {
            jdbcTemplate.update("DELETE FROM idempotency_records WHERE expires_at_ms <= ?", nowEpochMs);
            jdbcTemplate.update(
                    "DELETE FROM idempotency_records WHERE namespace_scope = ? AND idempotency_key = ?",
                    entry.namespaceScope(),
                    entry.idempotencyKey()
            );
            String headersJson = objectMapper.writeValueAsString(entry.response().headers());
            jdbcTemplate.update(
                    """
                    INSERT INTO idempotency_records (
                        namespace_scope, idempotency_key, request_method, request_path,
                        payload_sha256, status_code, response_headers_json, response_body,
                        committed_at_ms, expires_at_ms
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    entry.namespaceScope(),
                    entry.idempotencyKey(),
                    entry.fingerprint().method(),
                    entry.fingerprint().path(),
                    entry.fingerprint().payloadSha256(),
                    entry.response().statusCode(),
                    headersJson,
                    entry.response().body(),
                    entry.response().committedAtEpochMs(),
                    entry.expiresAtEpochMs()
            );

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM idempotency_records",
                    Integer.class
            );
            if (count != null && count > maxEntries) {
                int excess = count - maxEntries;
                jdbcTemplate.update(
                        """
                        DELETE FROM idempotency_records
                         WHERE (namespace_scope, idempotency_key) IN (
                             SELECT namespace_scope, idempotency_key
                               FROM idempotency_records
                              ORDER BY committed_at_ms ASC
                              FETCH FIRST ? ROWS ONLY
                         )
                        """,
                        excess
                );
            }
        } catch (Exception e) {
            log.warn("Failed persisting idempotency record for scope='{}': {}",
                    entry.namespaceScope(), e.getMessage());
        }
    }

    @Override
    public void delete(String namespaceScope, String idempotencyKey) {
        try {
            jdbcTemplate.update(
                    "DELETE FROM idempotency_records WHERE namespace_scope = ? AND idempotency_key = ?",
                    namespaceScope,
                    idempotencyKey
            );
        } catch (Exception e) {
            log.debug("Failed deleting idempotency record for scope='{}': {}", namespaceScope, e.getMessage());
        }
    }

    private Map<String, List<String>> parseHeaders(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, HEADERS_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
