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
     * @param topic optional topic name (defaults to "spector", accepts "cortex" as legacy alias)
     * @param filter optional comma-separated event types to filter by (e.g. "spector,cortex,ingestion")
     * @return a Flux streaming ServerSentEvent notifications
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> events(
            @RequestParam(name = "topic", required = false, defaultValue = "spector") String topic,
            @RequestParam(name = "filter", required = false, defaultValue = "") String filter) {
        log.debug("📡 SSE connection request received: topic='{}', filter='{}'", topic, filter);

        if (filter != null && !filter.isBlank()) {
            String[] topics = filter.split(",");
            Flux<ServerSentEvent<Object>> mergedFlux = Flux.empty();
            for (String t : topics) {
                String cleanTopic = t.trim();
                if (!cleanTopic.isEmpty()) {
                    mergedFlux = Flux.merge(mergedFlux, sseEmitter.connect(cleanTopic));
                }
            }
            return mergedFlux;
        }

        String effectiveTopic = (topic != null && !topic.isBlank()) ? topic.trim() : "spector";
        if ("spector".equalsIgnoreCase(effectiveTopic) || "cortex".equalsIgnoreCase(effectiveTopic)) {
            return Flux.merge(sseEmitter.connect("spector"), sseEmitter.connect("cortex"));
        }
        return sseEmitter.connect(effectiveTopic);
    }
}
