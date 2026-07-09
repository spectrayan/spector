/*
 * Copyright 2025–2026 Spectrayan. Licensed under the Apache License, Version 2.0.
 */
package com.spectrayan.spector.synapse.platform.events;

import com.spectrayan.sse.server.emitter.SseEmitter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Event publisher for real-time SSE notifications using the Spectrayan SSE Server library.
 */
@Service
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final SseEmitter emitter;

    public EventPublisher(SseEmitter emitter) {
        this.emitter = emitter;
    }

    /**
     * Publishes a memory event (created, recalled, consolidated, decayed).
     */
    public void memoryEvent(String eventType, String memoryId, String details) {
        emitter.emit("memory", eventType,
                java.util.Map.of("memoryId", memoryId, "type", eventType, "details", details));
        log.debug("📡 SSE memory event: type={}, id={}", eventType, memoryId);
    }

    /**
     * Publishes a chat event (message, thinking, tool_call, done).
     */
    public void chatEvent(String eventType, Object data) {
        emitter.emit("chat", eventType, data);
    }

    /**
     * Publishes a system status change event.
     */
    public void systemEvent(String eventType, Object data) {
        emitter.emit("system", eventType, data);
    }

    /**
     * Publishes a connector status event.
     */
    public void connectorEvent(String connectorId, String status) {
        emitter.emit("connectors", "status",
                java.util.Map.of("connectorId", connectorId, "status", status));
    }

    /**
     * Broadcasts to all connected topics.
     */
    public void broadcast(String eventName, Object data) {
        emitter.emit(data);
        log.debug("📡 SSE broadcast: event={}", eventName);
    }
}
