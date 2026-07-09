/*
 * Copyright 2025–2026 Spectrayan. Licensed under the Apache License, Version 2.0.
 */
package com.spectrayan.spector.synapse.platform.events;

import com.spectrayan.sse.server.emitter.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Enterprise event publisher for real-time SSE notifications.
 *
 * <p>Uses the Spectrayan SSE Server library to emit events to connected
 * Cortex UI clients, and publishes Spring ApplicationEvents for internal listeners.</p>
 */
@Service
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final SseEmitter emitter;
    private final ApplicationEventPublisher applicationEventPublisher;

    public EventPublisher(SseEmitter emitter, ApplicationEventPublisher applicationEventPublisher) {
        this.emitter = emitter;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Publishes a memory event (created, recalled, consolidated, decayed).
     */
    public void memoryEvent(String eventType, String memoryId, String details) {
        var data = Map.of("memoryId", memoryId, "type", eventType, "details", details);
        emitter.emit("memory", eventType, data);
        applicationEventPublisher.publishEvent(new SpectorEvent(this, eventType, data));
        log.debug("📡 SSE memory event: type={}, id={}", eventType, memoryId);
    }

    /**
     * Publishes a chat event (message, thinking, tool_call, done).
     */
    public void chatEvent(String eventType, Object data) {
        emitter.emit("chat", eventType, data);
        applicationEventPublisher.publishEvent(new SpectorEvent(this, eventType, data));
    }

    /**
     * Publishes a system status change event.
     */
    public void systemEvent(String eventType, Object data) {
        emitter.emit("system", eventType, data);
        applicationEventPublisher.publishEvent(new SpectorEvent(this, eventType, data));
    }

    /**
     * Publishes a connector status event.
     */
    public void connectorEvent(String connectorId, String status) {
        var data = Map.of("connectorId", connectorId, "status", status);
        emitter.emit("connectors", "status", data);
        applicationEventPublisher.publishEvent(new SpectorEvent(this, "connector.status", data));
    }

    /**
     * Broadcasts to all connected topics.
     */
    public void broadcast(String eventName, Object data) {
        emitter.emit(data);
        applicationEventPublisher.publishEvent(new SpectorEvent(this, eventName, data));
        log.debug("📡 SSE broadcast: event={}", eventName);
    }
}
