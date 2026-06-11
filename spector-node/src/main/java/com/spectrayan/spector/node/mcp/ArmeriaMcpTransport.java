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
package com.spectrayan.spector.node.mcp;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpResponseWriter;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.AbstractHttpService;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Native Armeria implementation of MCP transports.
 *
 * <p>Supports two transport modes on the same Armeria HTTP stack:</p>
 *
 * <h3>Streamable HTTP (MCP 2025-03-26 — recommended)</h3>
 * <p>A single endpoint handles all MCP traffic:</p>
 * <ul>
 *   <li>{@code POST /mcp} — JSON-RPC request → JSON response</li>
 *   <li>{@code GET  /mcp} — SSE stream for server-initiated notifications</li>
 *   <li>{@code DELETE /mcp} — Session termination</li>
 * </ul>
 * <p>Session management via {@code Mcp-Session-Id} header.</p>
 *
 * <h3>Legacy SSE (deprecated)</h3>
 * <ul>
 *   <li>{@code GET  /mcp/sse} — SSE stream (server → client)</li>
 *   <li>{@code POST /mcp/message?sessionId=xxx} — JSON-RPC messages (client → server)</li>
 * </ul>
 *
 * <h3>Streamable HTTP Protocol Flow</h3>
 * <ol>
 *   <li>Client POSTs {@code initialize} to {@code /mcp} (no session header)</li>
 *   <li>Server creates session, returns response + {@code Mcp-Session-Id} header</li>
 *   <li>Client POSTs {@code initialized} notification with session header</li>
 *   <li>Client POSTs tool calls, etc. with session header</li>
 *   <li>Optionally, client GETs {@code /mcp} for server-initiated notifications</li>
 *   <li>Client DELETEs {@code /mcp} to terminate session</li>
 * </ol>
 */
public class ArmeriaMcpTransport implements McpServerTransportProvider {

    private static final Logger log = LoggerFactory.getLogger(ArmeriaMcpTransport.class);

    private static final String SESSION_HEADER = "Mcp-Session-Id";
    private static final String MESSAGE_EVENT = "message";
    private static final String ENDPOINT_EVENT = "endpoint";

    /** Timeout for waiting for a response from session.handle(). */
    private static final long RESPONSE_TIMEOUT_SECONDS = 60;

    private final McpJsonMapper jsonMapper;

    // ── Shared state ─────────────────────────────────────────────────
    private final Map<String, McpServerSession> sessions = new ConcurrentHashMap<>();
    private final AtomicBoolean isClosing = new AtomicBoolean(false);
    private volatile McpServerSession.Factory sessionFactory;

    // ── Streamable HTTP state ────────────────────────────────────────
    private final Map<String, StreamableSessionTransport> streamableTransports = new ConcurrentHashMap<>();

    // ── Legacy SSE state ─────────────────────────────────────────────
    private final String legacyMessageEndpoint;
    private final Map<String, HttpResponseWriter> legacySseWriters = new ConcurrentHashMap<>();

    /**
     * Creates a transport with default settings.
     */
    public ArmeriaMcpTransport() {
        this(McpJsonDefaults.getMapper(), "/mcp/message");
    }

    /**
     * Creates a transport with custom JSON mapper and legacy message endpoint.
     *
     * @param jsonMapper            JSON mapper for serialization
     * @param legacyMessageEndpoint path for the legacy SSE message endpoint
     */
    public ArmeriaMcpTransport(McpJsonMapper jsonMapper, String legacyMessageEndpoint) {
        this.jsonMapper = jsonMapper;
        this.legacyMessageEndpoint = legacyMessageEndpoint;
    }

    // ── McpServerTransportProvider ────────────────────────────────────

    @Override
    public void setSessionFactory(McpServerSession.Factory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Mono<Void> notifyClients(String method, Object params) {
        if (sessions.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(sessions.values())
                .flatMap(session -> session.sendNotification(method, params)
                        .onErrorComplete())
                .then();
    }

    @Override
    public Mono<Void> notifyClient(String sessionId, String method, Object params) {
        return Mono.defer(() -> {
            McpServerSession session = sessions.values().stream()
                    .filter(s -> sessionId.equals(s.getId()))
                    .findFirst()
                    .orElse(null);
            if (session == null) {
                return Mono.empty();
            }
            return session.sendNotification(method, params);
        });
    }

    @Override
    public Mono<Void> closeGracefully() {
        isClosing.set(true);
        log.info("[MCP] Graceful shutdown: {} active sessions", sessions.size());
        return Flux.fromIterable(sessions.values())
                .flatMap(McpServerSession::closeGracefully)
                .then()
                .doOnSuccess(v -> {
                    // Close all Streamable HTTP notification writers
                    streamableTransports.values().forEach(t -> {
                        HttpResponseWriter w = t.notificationWriter;
                        if (w != null) w.close();
                    });
                    streamableTransports.clear();

                    // Close all legacy SSE writers
                    legacySseWriters.values().forEach(HttpResponseWriter::close);
                    legacySseWriters.clear();

                    sessions.clear();
                    log.info("[MCP] Shutdown complete");
                });
    }

    // ══════════════════════════════════════════════════════════════════
    //  Streamable HTTP Transport (MCP 2025-03-26)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Returns the unified Streamable HTTP service (mount at {@code /mcp}).
     *
     * <p>Handles POST (JSON-RPC request/response), GET (SSE notification stream),
     * and DELETE (session termination) on a single endpoint.</p>
     */
    public HttpService streamableHttpService() {
        return new AbstractHttpService() {

            // ── POST /mcp — JSON-RPC request → response ─────────────
            @Override
            protected HttpResponse doPost(ServiceRequestContext ctx, HttpRequest req) {
                if (isClosing.get()) {
                    return HttpResponse.of(HttpStatus.SERVICE_UNAVAILABLE);
                }
                if (sessionFactory == null) {
                    return jsonError(HttpStatus.INTERNAL_SERVER_ERROR,
                            McpSchema.ErrorCodes.INTERNAL_ERROR,
                            "MCP server not initialized");
                }

                return HttpResponse.of(
                    req.aggregate().thenCompose(agg -> {
                        try {
                            String body = agg.contentUtf8();
                            McpSchema.JSONRPCMessage message =
                                    McpSchema.deserializeJsonRpcMessage(jsonMapper, body);

                            // ── Resolve or create session ──
                            String sessionId = req.headers().get(SESSION_HEADER);
                            StreamableSessionTransport transport;
                            McpServerSession session;
                            boolean isNewSession = false;

                            if (sessionId == null || sessionId.isBlank()) {
                                // New session (first request, typically "initialize")
                                sessionId = UUID.randomUUID().toString();
                                transport = new StreamableSessionTransport(sessionId);
                                session = sessionFactory.create(transport);
                                sessions.put(sessionId, session);
                                streamableTransports.put(sessionId, transport);
                                isNewSession = true;
                                log.info("[MCP] Streamable HTTP session created: {}", sessionId);
                            } else {
                                session = sessions.get(sessionId);
                                transport = streamableTransports.get(sessionId);
                                if (session == null || transport == null) {
                                    return CompletableFuture.completedFuture(
                                            jsonError(HttpStatus.NOT_FOUND,
                                                    McpSchema.ErrorCodes.INTERNAL_ERROR,
                                                    "Session not found: " + sessionId));
                                }
                            }

                            // ── Determine if this is a request (expects response) ──
                            boolean isRequest = message instanceof McpSchema.JSONRPCRequest;
                            CompletableFuture<String> responseFuture = null;

                            if (isRequest) {
                                McpSchema.JSONRPCRequest jsonRpcRequest = (McpSchema.JSONRPCRequest) message;
                                responseFuture = new CompletableFuture<>();
                                transport.pendingResponses.put(jsonRpcRequest.id(), responseFuture);
                            }

                            final String sid = sessionId;
                            final CompletableFuture<String> rf = responseFuture;

                            // ── Handle the message ──
                            return session.handle(message)
                                    .then(Mono.defer(() -> {
                                        if (rf != null) {
                                            // Wait for the response from sendMessage()
                                            return Mono.fromFuture(rf);
                                        }
                                        // Notification — no response expected
                                        return Mono.just("");
                                    }))
                                    .map(responseJson -> {
                                        if (responseJson == null || responseJson.isEmpty()) {
                                            // Notification accepted — 202 with session ID
                                            return HttpResponse.of(
                                                    ResponseHeaders.builder(HttpStatus.ACCEPTED)
                                                            .add(SESSION_HEADER, sid)
                                                            .build());
                                        }
                                        // Request response — 200 with JSON body
                                        return HttpResponse.of(
                                                ResponseHeaders.builder(HttpStatus.OK)
                                                        .contentType(MediaType.JSON)
                                                        .add(SESSION_HEADER, sid)
                                                        .build(),
                                                HttpData.ofUtf8(responseJson));
                                    })
                                    .onErrorResume(e -> {
                                        log.error("[MCP] Error handling message for session {}: {}",
                                                sid, e.getMessage());
                                        return Mono.just(jsonError(
                                                HttpStatus.INTERNAL_SERVER_ERROR,
                                                McpSchema.ErrorCodes.INTERNAL_ERROR,
                                                e.getMessage()));
                                    })
                                    .toFuture();

                        } catch (Exception e) {
                            log.error("[MCP] Error deserializing message: {}", e.getMessage());
                            return CompletableFuture.completedFuture(
                                    jsonError(HttpStatus.BAD_REQUEST,
                                            McpSchema.ErrorCodes.PARSE_ERROR,
                                            "Invalid JSON-RPC message: " + e.getMessage()));
                        }
                    })
                );
            }

            // ── GET /mcp — SSE notification stream ──────────────────
            @Override
            protected HttpResponse doGet(ServiceRequestContext ctx, HttpRequest req) {
                if (isClosing.get()) {
                    return HttpResponse.of(HttpStatus.SERVICE_UNAVAILABLE);
                }

                String sessionId = req.headers().get(SESSION_HEADER);
                if (sessionId == null || sessionId.isBlank()) {
                    return jsonError(HttpStatus.BAD_REQUEST,
                            McpSchema.ErrorCodes.INVALID_REQUEST,
                            "Mcp-Session-Id header required for GET");
                }

                StreamableSessionTransport transport = streamableTransports.get(sessionId);
                if (transport == null) {
                    return jsonError(HttpStatus.NOT_FOUND,
                            McpSchema.ErrorCodes.INTERNAL_ERROR,
                            "Session not found: " + sessionId);
                }

                // Open SSE stream for server → client notifications
                HttpResponseWriter writer = HttpResponse.streaming();
                writer.write(ResponseHeaders.of(HttpStatus.OK,
                        "Content-Type", "text/event-stream",
                        "Cache-Control", "no-cache",
                        "Connection", "keep-alive",
                        SESSION_HEADER, sessionId));

                transport.notificationWriter = writer;
                log.info("[MCP] Notification stream opened for session {}", sessionId);

                writer.whenComplete().thenRun(() -> {
                    transport.notificationWriter = null;
                    log.info("[MCP] Notification stream closed for session {}", sessionId);
                });

                return writer;
            }

            // ── DELETE /mcp — Session termination ───────────────────
            @Override
            protected HttpResponse doDelete(ServiceRequestContext ctx, HttpRequest req) {
                String sessionId = req.headers().get(SESSION_HEADER);
                if (sessionId == null || sessionId.isBlank()) {
                    return jsonError(HttpStatus.BAD_REQUEST,
                            McpSchema.ErrorCodes.INVALID_REQUEST,
                            "Mcp-Session-Id header required for DELETE");
                }

                McpServerSession session = sessions.remove(sessionId);
                StreamableSessionTransport transport = streamableTransports.remove(sessionId);

                if (session != null) {
                    session.closeGracefully().subscribe();
                }
                if (transport != null) {
                    HttpResponseWriter w = transport.notificationWriter;
                    if (w != null) w.close();
                }

                log.info("[MCP] Session terminated: {}", sessionId);
                return HttpResponse.of(HttpStatus.OK);
            }
        };
    }

    // ══════════════════════════════════════════════════════════════════
    //  Legacy SSE Transport (deprecated, backward-compatible)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Returns the legacy SSE endpoint service (mount at {@code /mcp/sse}).
     *
     * @deprecated Use {@link #streamableHttpService()} instead.
     */
    @Deprecated
    public HttpService sseService() {
        return new AbstractHttpService() {
            @Override
            protected HttpResponse doGet(ServiceRequestContext ctx, HttpRequest req) {
                if (isClosing.get()) {
                    return HttpResponse.of(HttpStatus.SERVICE_UNAVAILABLE);
                }
                if (sessionFactory == null) {
                    return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR,
                            MediaType.PLAIN_TEXT, "MCP server not initialized");
                }

                String sessionId = UUID.randomUUID().toString();

                HttpResponseWriter writer = HttpResponse.streaming();
                writer.write(ResponseHeaders.of(HttpStatus.OK,
                        "Content-Type", "text/event-stream",
                        "Cache-Control", "no-cache",
                        "Connection", "keep-alive"));

                var sessionTransport = new LegacySseSessionTransport(sessionId, writer);
                McpServerSession session = sessionFactory.create(sessionTransport);
                sessions.put(sessionId, session);
                legacySseWriters.put(sessionId, writer);

                String endpointUrl = legacyMessageEndpoint + "?sessionId=" + sessionId;
                sendSseEvent(writer, ENDPOINT_EVENT, endpointUrl);

                log.info("[MCP] Legacy SSE client connected: sessionId={}", sessionId);

                writer.whenComplete().thenRun(() -> {
                    sessions.remove(sessionId);
                    legacySseWriters.remove(sessionId);
                    log.info("[MCP] Legacy SSE client disconnected: sessionId={}", sessionId);
                });

                return writer;
            }
        };
    }

    /**
     * Returns the legacy message endpoint service (mount at {@code /mcp/message}).
     *
     * @deprecated Use {@link #streamableHttpService()} instead.
     */
    @Deprecated
    public HttpService messageService() {
        return new AbstractHttpService() {
            @Override
            protected HttpResponse doPost(ServiceRequestContext ctx, HttpRequest req) {
                if (isClosing.get()) {
                    return HttpResponse.of(HttpStatus.SERVICE_UNAVAILABLE);
                }

                String sessionId = ctx.queryParam("sessionId");
                if (sessionId == null) {
                    return jsonError(HttpStatus.BAD_REQUEST,
                            McpSchema.ErrorCodes.INVALID_REQUEST,
                            "Session ID missing in message endpoint");
                }

                McpServerSession session = sessions.get(sessionId);
                if (session == null) {
                    return jsonError(HttpStatus.NOT_FOUND,
                            McpSchema.ErrorCodes.INTERNAL_ERROR,
                            "Session not found: " + sessionId);
                }

                return HttpResponse.of(
                    req.aggregate().thenCompose(agg -> {
                        try {
                            String body = agg.contentUtf8();
                            McpSchema.JSONRPCMessage message =
                                    McpSchema.deserializeJsonRpcMessage(jsonMapper, body);

                            return session.handle(message)
                                    .then(Mono.fromCallable(() -> HttpResponse.of(HttpStatus.OK)))
                                    .onErrorResume(e -> {
                                        log.error("[MCP] Error processing message for session {}: {}",
                                                sessionId, e.getMessage());
                                        return Mono.just(jsonError(HttpStatus.INTERNAL_SERVER_ERROR,
                                                McpSchema.ErrorCodes.INTERNAL_ERROR,
                                                e.getMessage()));
                                    })
                                    .toFuture();
                        } catch (Exception e) {
                            log.error("[MCP] Error deserializing message for session {}: {}",
                                    sessionId, e.getMessage());
                            return CompletableFuture.completedFuture(
                                    jsonError(HttpStatus.INTERNAL_SERVER_ERROR,
                                            McpSchema.ErrorCodes.INTERNAL_ERROR,
                                            e.getMessage()));
                        }
                    })
                );
            }
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void sendSseEvent(HttpResponseWriter writer, String eventType, String data) {
        String sseFrame = "event: " + eventType + "\ndata: " + data + "\n\n";
        writer.write(HttpData.ofUtf8(sseFrame));
    }

    private HttpResponse jsonError(HttpStatus status, int errorCode, String message) {
        try {
            var errorBody = Map.of(
                    "jsonrpc", "2.0",
                    "error", Map.of("code", errorCode, "message", message));
            String json = jsonMapper.writeValueAsString(errorBody);
            return HttpResponse.of(status, MediaType.JSON, json);
        } catch (Exception e) {
            return HttpResponse.of(status, MediaType.PLAIN_TEXT, message);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Session Transports
    // ══════════════════════════════════════════════════════════════════

    /**
     * Per-session transport for Streamable HTTP.
     *
     * <p>Responses to requests are captured via {@link #pendingResponses}
     * and returned in the POST response body. Server-initiated notifications
     * are pushed via the optional SSE {@link #notificationWriter}.</p>
     */
    private class StreamableSessionTransport implements McpServerTransport {

        private final String sessionId;
        final Map<Object, CompletableFuture<String>> pendingResponses = new ConcurrentHashMap<>();
        volatile HttpResponseWriter notificationWriter;

        StreamableSessionTransport(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            return Mono.fromRunnable(() -> {
                try {
                    String json = jsonMapper.writeValueAsString(message);

                    // Route responses to the pending POST request
                    if (message instanceof McpSchema.JSONRPCResponse response) {
                        CompletableFuture<String> future = pendingResponses.remove(response.id());
                        if (future != null) {
                            future.complete(json);
                            return;
                        }
                        log.warn("[MCP] No pending request for response id={} in session {}",
                                response.id(), sessionId);
                    }

                    // Server-initiated notifications → SSE stream
                    HttpResponseWriter writer = notificationWriter;
                    if (writer != null) {
                        sendSseEvent(writer, MESSAGE_EVENT, json);
                        log.debug("[MCP] Notification sent to session {}", sessionId);
                    } else {
                        log.debug("[MCP] No notification stream for session {}, message buffered/dropped",
                                sessionId);
                    }
                } catch (Exception e) {
                    log.error("[MCP] Failed to send message to session {}: {}",
                            sessionId, e.getMessage());
                }
            });
        }

        @Override
        public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
            return jsonMapper.convertValue(data, typeRef);
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.fromRunnable(() -> {
                sessions.remove(sessionId);
                streamableTransports.remove(sessionId);
                // Cancel any pending responses
                pendingResponses.values().forEach(f ->
                        f.completeExceptionally(new RuntimeException("Session closed")));
                pendingResponses.clear();
                HttpResponseWriter writer = notificationWriter;
                if (writer != null) {
                    writer.close();
                    notificationWriter = null;
                }
                log.debug("[MCP] Streamable session closed: {}", sessionId);
            });
        }

        @Override
        public void close() {
            closeGracefully().subscribe();
        }
    }

    /**
     * Per-session transport for legacy SSE.
     *
     * <p>Sends all messages (responses and notifications) via the SSE stream.</p>
     */
    private class LegacySseSessionTransport implements McpServerTransport {

        private final String sessionId;
        private final HttpResponseWriter writer;

        LegacySseSessionTransport(String sessionId, HttpResponseWriter writer) {
            this.sessionId = sessionId;
            this.writer = writer;
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            return Mono.fromRunnable(() -> {
                try {
                    String json = jsonMapper.writeValueAsString(message);
                    sendSseEvent(writer, MESSAGE_EVENT, json);
                    log.debug("[MCP] Legacy SSE message sent to session {}", sessionId);
                } catch (Exception e) {
                    log.error("[MCP] Failed to send legacy SSE message to session {}: {}",
                            sessionId, e.getMessage());
                    sessions.remove(sessionId);
                    legacySseWriters.remove(sessionId);
                    writer.close();
                }
            });
        }

        @Override
        public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
            return jsonMapper.convertValue(data, typeRef);
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.fromRunnable(() -> {
                sessions.remove(sessionId);
                legacySseWriters.remove(sessionId);
                writer.close();
                log.debug("[MCP] Legacy SSE session closed: {}", sessionId);
            });
        }

        @Override
        public void close() {
            sessions.remove(sessionId);
            legacySseWriters.remove(sessionId);
            writer.close();
        }
    }
}
