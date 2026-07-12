/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
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
        try {
            emitter.emit("memory", eventType,
                    java.util.Map.of("memoryId", memoryId, "type", eventType, "details", details));
            log.debug("📡 SSE memory event: type={}, id={}", eventType, memoryId);
        } catch (Exception e) {
            log.debug("Failed to emit memory event (possibly no subscribers): {}", e.getMessage());
        }
    }

    /**
     * Publishes a chat event (message, thinking, tool_call, done).
     */
    public void chatEvent(String eventType, Object data) {
        try {
            emitter.emit("chat", eventType, data);
        } catch (Exception e) {
            log.debug("Failed to emit chat event (possibly no subscribers): {}", e.getMessage());
        }
    }

    /**
     * Publishes a system status change event.
     */
    public void systemEvent(String eventType, Object data) {
        try {
            emitter.emit("system", eventType, data);
        } catch (Exception e) {
            log.debug("Failed to emit system event (possibly no subscribers): {}", e.getMessage());
        }
    }

    /**
     * Publishes a connector status event.
     */
    public void connectorEvent(String connectorId, String status) {
        try {
            emitter.emit("connectors", "status",
                    java.util.Map.of("connectorId", connectorId, "status", status));
        } catch (Exception e) {
            log.debug("Failed to emit connector event (possibly no subscribers): {}", e.getMessage());
        }
    }

    /**
     * Broadcasts to all connected topics.
     */
    public void broadcast(String eventName, Object data) {
        try {
            emitter.emit(data);
            log.debug("📡 SSE broadcast: event={}", eventName);
        } catch (Exception e) {
            log.debug("Failed to broadcast SSE event: {}", e.getMessage());
        }
    }
}
