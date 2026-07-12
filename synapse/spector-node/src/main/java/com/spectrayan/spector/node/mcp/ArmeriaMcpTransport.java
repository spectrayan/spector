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
 * Native Armeria implementation of MCP Streamable HTTP transport.
 *
 * <p>Supports two modes on the same Armeria HTTP stack:</p>
 *
 * <h3>Stateless Mode (default, recommended)</h3>
 * <p>No {@code Mcp-Session-Id} header is emitted. A single shared
 * {@link McpServerSession} handles all requests. Resilient to restarts.</p>
 *
 * <h3>Stateful Mode</h3>
 * <p>Session management via Mcp-Session-Id header. Stale sessions
 * are transparently recovered.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>POST /mcp - JSON-RPC request / JSON response</li>
 *   <li>GET  /mcp - SSE notification stream (stateful only)</li>
 *   <li>DELETE /mcp - Session termination (stateful only)</li>
 * </ul>
 */
public class ArmeriaMcpTransport implements McpServerTransportProvider {

    private static final Logger log = LoggerFactory.getLogger(ArmeriaMcpTransport.class);

    private static final String SESSION_HEADER = "Mcp-Session-Id";
    private static final String MESSAGE_EVENT = "message";

    /** Timeout for waiting for a response from session.handle(). */
    private static final long RESPONSE_TIMEOUT_SECONDS = 60;

    private final McpJsonMapper jsonMapper;
    private final boolean stateless;

    // -- Shared state -------------------------------------------------
    private final Map<String, McpServerSession> sessions = new ConcurrentHashMap<>();
    private final AtomicBoolean isClosing = new AtomicBoolean(false);
    private volatile McpServerSession.Factory sessionFactory;

    // -- Stateless shared session -------------------------------------
    private static final String SHARED_SESSION_ID = "__stateless__";
    private volatile McpServerSession sharedSession;
    private volatile StreamableSessionTransport sharedTransport;

    // -- Streamable HTTP state ----------------------------------------
    private final Map<String, StreamableSessionTransport> streamableTransports = new ConcurrentHashMap<>();

    /**
     * Creates a transport with default settings (stateless mode).
     */
    public ArmeriaMcpTransport() {
        this(true);
    }

    /**
     * Creates a transport with the specified session mode.
     *
     * @param stateless if true, operates in stateless mode (recommended)
     */
    public ArmeriaMcpTransport(boolean stateless) {
        this(McpJsonDefaults.getMapper(), stateless);
    }

    /**
     * Creates a transport with custom JSON mapper.
     *
     * @param jsonMapper JSON mapper for serialization
     * @param stateless  if true, operates in stateless mode
     */
    public ArmeriaMcpTransport(McpJsonMapper jsonMapper, boolean stateless) {
        this.jsonMapper = jsonMapper;
        this.stateless = stateless;
    }

    // -- McpServerTransportProvider -----------------------------------

    @Override
    public void setSessionFactory(McpServerSession.Factory sessionFactory) {
        this.sessionFactory = sessionFactory;
        if (stateless) {
            sharedTransport = new StreamableSessionTransport(SHARED_SESSION_ID);
            sharedSession = sessionFactory.create(sharedTransport);
            sessions.put(SHARED_SESSION_ID, sharedSession);
            streamableTransports.put(SHARED_SESSION_ID, sharedTransport);

            // Pre-initialize the shared session so tools/call works immediately.
            // The SDK's McpServerSession has a Sinks.One exchangeSink that only
            // emits after receiving 'notifications/initialized'. Without this,
            // all tools/call requests block forever.
            try {
                var initRequest = new McpSchema.JSONRPCRequest(
                        McpSchema.METHOD_INITIALIZE, "__init__",
                        Map.of("protocolVersion", "2024-11-05",
                                "capabilities", Map.of(),
                                "clientInfo", Map.of("name", "stateless-bootstrap",
                                        "version", "1.0")));
                // Drain the response future created by sendMessage
                var initFuture = new CompletableFuture<String>();
                sharedTransport.pendingResponses.put("__init__", initFuture);
                sharedSession.handle(initRequest)
                        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                        .block(java.time.Duration.ofSeconds(5));
                sharedTransport.pendingResponses.remove("__init__");

                // Send 'initialized' notification to transition state
                var initializedNotification = new McpSchema.JSONRPCNotification(
                        McpSchema.METHOD_NOTIFICATION_INITIALIZED, null);
                sharedSession.handle(initializedNotification)
                        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                        .block(java.time.Duration.ofSeconds(5));

                log.info("[MCP] Stateless mode: shared session created and pre-initialized");
            } catch (Exception e) {
                log.warn("[MCP] Pre-initialization failed (tools may require init): {}",
                        e.getMessage());
            }
        }
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
                    streamableTransports.values().forEach(t -> {
                        HttpResponseWriter w = t.notificationWriter;
                        if (w != null) w.close();
                    });
                    streamableTransports.clear();
                    sessions.clear();
                    log.info("[MCP] Shutdown complete");
                });
    }

    // =============================================================
    //  Streamable HTTP Transport (MCP 2025-03-26)
    // =============================================================

    /**
     * Returns the unified Streamable HTTP service (mount at {@code /mcp}).
     */
    public HttpService streamableHttpService() {
        return new AbstractHttpService() {

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

                            // -- Resolve session --
                            StreamableSessionTransport transport;
                            McpServerSession session;

                            if (stateless) {
                                transport = sharedTransport;
                                session = sharedSession;
                            } else {
                                String sessionId = req.headers().get(SESSION_HEADER);

                                if (sessionId == null || sessionId.isBlank()) {
                                    sessionId = UUID.randomUUID().toString();
                                    transport = new StreamableSessionTransport(sessionId);
                                    session = sessionFactory.create(transport);
                                    sessions.put(sessionId, session);
                                    streamableTransports.put(sessionId, transport);
                                    log.info("[MCP] Session created: {}", sessionId);
                                } else {
                                    session = sessions.get(sessionId);
                                    transport = streamableTransports.get(sessionId);
                                    if (session == null || transport == null) {
                                        String newSessionId = UUID.randomUUID().toString();
                                        transport = new StreamableSessionTransport(newSessionId);
                                        session = sessionFactory.create(transport);
                                        sessions.put(newSessionId, session);
                                        streamableTransports.put(newSessionId, transport);
                                        sessionId = newSessionId;
                                        log.info("[MCP] Recovered stale session: {}", newSessionId);
                                    }
                                }
                            }

                            // -- Register pending response future --
                            boolean isRequest = message instanceof McpSchema.JSONRPCRequest;
                            CompletableFuture<String> responseFuture = null;

                            if (isRequest) {
                                McpSchema.JSONRPCRequest jsonRpcRequest = (McpSchema.JSONRPCRequest) message;
                                responseFuture = new CompletableFuture<String>()
                                        .orTimeout(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                                transport.pendingResponses.put(jsonRpcRequest.id(), responseFuture);
                            }

                            final CompletableFuture<String> rf = responseFuture;

                            // -- Handle the message --
                            // Run on boundedElastic scheduler to avoid blocking
                            // the Armeria event loop. McpSyncServer tool handlers
                            // execute synchronously and would block the thread
                            // that needs to process the response future callback.
                            session.handle(message)
                                    .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                                    .subscribe(
                                            unused -> {},
                                            err -> {
                                                log.error("[MCP] session.handle error: {}", err.getMessage());
                                                if (rf != null && !rf.isDone()) {
                                                    rf.completeExceptionally(err);
                                                }
                                            }
                                    );

                            if (rf != null) {
                                // Wait for response from sendMessage()
                                return rf.thenApply(responseJson -> {
                                    if (responseJson == null || responseJson.isEmpty()) {
                                        return HttpResponse.of(
                                                ResponseHeaders.builder(HttpStatus.ACCEPTED)
                                                        .build());
                                    }
                                    return HttpResponse.of(
                                            ResponseHeaders.builder(HttpStatus.OK)
                                                    .contentType(MediaType.JSON)
                                                    .build(),
                                            HttpData.ofUtf8(responseJson));
                                }).exceptionally(e -> {
                                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                                    if (cause instanceof java.util.concurrent.TimeoutException) {
                                        log.warn("[MCP] Response timeout after {}s",
                                                RESPONSE_TIMEOUT_SECONDS);
                                        return jsonError(HttpStatus.GATEWAY_TIMEOUT,
                                                McpSchema.ErrorCodes.INTERNAL_ERROR,
                                                "MCP response timeout");
                                    }
                                    log.error("[MCP] Error: {}", cause.getMessage());
                                    return jsonError(HttpStatus.INTERNAL_SERVER_ERROR,
                                            McpSchema.ErrorCodes.INTERNAL_ERROR,
                                            cause.getMessage());
                                });
                            } else {
                                // Notification -- no response expected
                                return CompletableFuture.completedFuture(
                                        HttpResponse.of(ResponseHeaders.builder(HttpStatus.ACCEPTED)
                                                .build()));
                            }

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

            @Override
            protected HttpResponse doGet(ServiceRequestContext ctx, HttpRequest req) {
                if (stateless) {
                    return HttpResponse.of(HttpStatus.METHOD_NOT_ALLOWED,
                            MediaType.PLAIN_TEXT,
                            "SSE streams not available in stateless mode");
                }

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

            @Override
            protected HttpResponse doDelete(ServiceRequestContext ctx, HttpRequest req) {
                if (stateless) {
                    return HttpResponse.of(HttpStatus.METHOD_NOT_ALLOWED,
                            MediaType.PLAIN_TEXT,
                            "Session termination not available in stateless mode");
                }

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

    // -- Helpers ------------------------------------------------------

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

    // =============================================================
    //  Session Transport
    // =============================================================

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
            // Execute eagerly -- the MCP SDK's reactive chain may not subscribe
            // to a lazy Mono, causing the response future to never complete.
            try {
                String json = jsonMapper.writeValueAsString(message);

                if (message instanceof McpSchema.JSONRPCResponse response) {
                    CompletableFuture<String> future = pendingResponses.remove(response.id());
                    if (future == null) {
                        // ID type mismatch fallback (String "1" vs Integer 1)
                        String responseIdStr = String.valueOf(response.id());
                        for (var entry : pendingResponses.entrySet()) {
                            if (responseIdStr.equals(String.valueOf(entry.getKey()))) {
                                future = pendingResponses.remove(entry.getKey());
                                break;
                            }
                        }
                    }
                    if (future != null) {
                        future.complete(json);
                        return Mono.empty();
                    }
                    log.warn("[MCP] No pending request for response id={} in session {}",
                            response.id(), sessionId);
                }

                // Server-initiated notifications -> SSE stream
                HttpResponseWriter writer = notificationWriter;
                if (writer != null) {
                    sendSseEvent(writer, MESSAGE_EVENT, json);
                }
            } catch (Exception e) {
                log.error("[MCP] Failed to send message in session {}: {}",
                        sessionId, e.getMessage());
            }
            return Mono.empty();
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
}
