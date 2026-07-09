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
package com.spectrayan.spector.synapse.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.synapse.platform.events.SpectorEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSE controller serving /api/v1/events to the Cortex UI.
 *
 * <p>Handles event streaming subscriptions and broadcasts real-time
 * cognitive trace, reflect, and progress events. Keeps connections alive
 * via a background heartbeat scheduler.</p>
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/events")
public class EventsController implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(EventsController.class);
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper();

    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                Thread t = new Thread(runnable, "sse-heartbeat");
                t.setDaemon(true);
                return t;
            }
    );

    public EventsController() {
        // Send heartbeat every 30 seconds to keep connection alive
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (emitters.isEmpty()) return;
            log.trace("[SSE] Sending keep-alive heartbeat to {} clients", emitters.size());
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("heartbeat")
                            .data("{}"));
                } catch (Exception e) {
                    emitters.remove(emitter);
                }
            }
        }, 15, 30, TimeUnit.SECONDS);
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@RequestParam(required = false) String filter) {
        log.info("[SSE] New client subscribed with filter='{}'", filter);
        SseEmitter emitter = new SseEmitter(180_000L); // 3 minutes timeout
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        // Initial connect event to confirm subscription
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"status\":\"connected\"}"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    /**
     * Broadcasts a JSON event payload to all active SSE subscribers.
     *
     * @param eventType the category/type of the event
     * @param eventData the payload object to serialize to JSON
     */
    public void broadcast(String eventType, Object eventData) {
        if (emitters.isEmpty()) return;

        try {
            String dataJson = mapper.writeValueAsString(eventData);
            log.debug("[SSE] Broadcasting event '{}' to {} subscribers", eventType, emitters.size());
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name(eventType)
                            .data(dataJson));
                } catch (Exception e) {
                    emitters.remove(emitter);
                }
            }
        } catch (Exception e) {
            log.warn("[SSE] Failed to serialize event payload for type '{}': {}", eventType, e.getMessage());
        }
    }

    @EventListener
    public void handleSpectorEvent(SpectorEvent event) {
        broadcast(event.getEventType(), event.getData());
    }

    @Override
    public void destroy() {
        log.info("[SSE] Shutting down heartbeat scheduler and closing connections");
        heartbeatScheduler.shutdownNow();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (Exception ignored) {}
        }
        emitters.clear();
    }
}
