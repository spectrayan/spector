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
     * Publishes an agent event (e.g. approval required, approval resolved, reasoning cycle) to the 'agent' topic.
     */
    public void agentEvent(String eventType, Object data) {
        try {
            emitter.emit(SseEventConstants.TOPIC_AGENT, eventType, data);
            log.debug("📡 SSE agent event: type={}", eventType);
        } catch (Exception e) {
            log.debug("Failed to emit agent event (possibly no subscribers): {}", e.getMessage());
        }
    }

    /**
     * Publishes a memory event (created, recalled, consolidated, decayed).
     */
    public void memoryEvent(String eventType, String memoryId, String details) {
        try {
            emitter.emit(SseEventConstants.TOPIC_MEMORY, eventType,
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
            emitter.emit(SseEventConstants.TOPIC_CHAT, eventType, data);
        } catch (Exception e) {
            log.debug("Failed to emit chat event (possibly no subscribers): {}", e.getMessage());
        }
    }

    /**
     * Publishes a system status change event.
     */
    public void systemEvent(String eventType, Object data) {
        try {
            emitter.emit(SseEventConstants.TOPIC_SYSTEM, eventType, data);
        } catch (Exception e) {
            log.debug("Failed to emit system event (possibly no subscribers): {}", e.getMessage());
        }
    }

    /**
     * Publishes a connector status event.
     */
    public void connectorEvent(String connectorId, String status) {
        try {
            emitter.emit(SseEventConstants.TOPIC_CONNECTORS, SseEventConstants.EVENT_CONNECTOR_STATUS,
                    java.util.Map.of("connectorId", connectorId, "status", status));
        } catch (Exception e) {
            log.debug("Failed to emit connector event (possibly no subscribers): {}", e.getMessage());
        }
    }

    /**
     * Publishes a cortex telemetry event (diagnostic, trace, simd, pulse, etc.) to the 'cortex' topic.
     */
    public void cortexEvent(String eventType, Object data) {
        try {
            emitter.emit(SseEventConstants.TOPIC_CORTEX, eventType, data);
            log.trace("📡 SSE cortex event: type={}", eventType);
        } catch (Exception e) {
            log.trace("Failed to emit cortex event (possibly no subscribers): {}", e.getMessage());
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
