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
package com.spectrayan.spector.synapse.platform.events.api;

import com.spectrayan.sse.server.emitter.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Spring MVC REST Controller for Server-Sent Events (SSE).
 *
 * Bridges the reactive Spectrayan SSE Server library (which is WebFlux-based)
 * to run seamlessly on the Tomcat Servlet container under Spring Boot 4.
 */
@RestController
@RequestMapping("/api/v1")
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);

    private final SseEmitter sseEmitter;

    public EventController(SseEmitter sseEmitter) {
        this.sseEmitter = sseEmitter;
    }

    /**
     * Establishes the real-time event stream connection.
     *
     * @param filter optional comma-separated event types to filter by (e.g. "cortex,ingestion")
     * @return a Flux streaming ServerSentEvent notifications
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> events(@RequestParam(required = false, defaultValue = "") String filter) {
        log.debug("📡 SSE connection request received: filter='{}'", filter);
        if (filter == null || filter.isBlank()) {
            return sseEmitter.connect("cortex");
        }

        String[] topics = filter.split(",");
        Flux<ServerSentEvent<Object>> mergedFlux = Flux.empty();
        for (String topic : topics) {
            String cleanTopic = topic.trim();
            if (!cleanTopic.isEmpty()) {
                mergedFlux = Flux.merge(mergedFlux, sseEmitter.connect(cleanTopic));
            }
        }
        return mergedFlux;
    }
}
