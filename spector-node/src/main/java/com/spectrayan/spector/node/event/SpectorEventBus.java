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
package com.spectrayan.spector.node.event;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.events.EventBus;
import com.spectrayan.spector.events.LocalNotificationTransport;
import com.spectrayan.spector.events.NotificationScope;
import com.spectrayan.spector.events.NotificationTransport;
import com.spectrayan.spector.events.SubscriberIdentity;

/**
 * Node-specific event bus — extends the generic {@link EventBus} for
 * {@link SpectorNodeEvent} with scope-aware delivery.
 *
 * <p>This is a thin specialization over {@link EventBus}. All subscriber
 * management, publishing (sync/async), and multi-transport fan-out are
 * inherited from the generic base class. This class only provides:</p>
 * <ul>
 *   <li>Default constructor that wires {@link LocalNotificationTransport}
 *       with {@link SpectorNodeEvent#scope()} extraction</li>
 *   <li>Custom transport injection for distributed deployments</li>
 * </ul>
 *
 * <h3>Architecture</h3>
 * <pre>
 *   eventBus.publish(event)
 *       │
 *       ├── In-process subscribers  (subscribe(Consumer))  — all events, no scope filtering
 *       │                                                    (analytics CDC, metrics, audit)
 *       │
 *       └── Transport subscribers   (addTransport(T))       — scope-filtered delivery
 *           ├── LocalNotificationTransport   (SSE clients)
 *           ├── KafkaTransport               (analytics pipeline)
 *           └── MqttTransport                (remote UIs)
 * </pre>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   SpectorEventBus eventBus = new SpectorEventBus();
 *
 *   // In-process subscribe (all events, no filtering)
 *   eventBus.subscribe(event -> { ... });
 *
 *   // Type-filtered subscribe
 *   eventBus.subscribe(SpectorSearchCompletedEvent.class, e -> { ... });
 *
 *   // Scoped subscribe (only matching events)
 *   var identity = SubscriberIdentity.ofUser("user-1", "acme");
 *   eventBus.subscribe(identity, event -> { ... });
 *
 *   // Publish
 *   eventBus.publish(new SpectorSearchCompletedEvent(...));
 * }</pre>
 *
 * @see EventBus
 * @see SpectorNodeEvent
 * @see NotificationTransport
 * @see NotificationScope
 */
public class SpectorEventBus extends EventBus<SpectorNodeEvent> {

    private static final Logger log = LoggerFactory.getLogger(SpectorEventBus.class);

    /**
     * Creates an event bus with the default {@link LocalNotificationTransport}
     * using {@link SpectorNodeEvent#scope()} for scope-aware delivery.
     */
    public SpectorEventBus() {
        super(new LocalNotificationTransport<>(SpectorNodeEvent::scope));
    }

    /**
     * Creates an event bus with a custom notification transport.
     *
     * <p>Use this constructor to inject a distributed transport (Redis Streams,
     * Kafka, MQTT, NATS) for multi-pod deployments. Additional transports can
     * be added later via {@link #addTransport(NotificationTransport)}.</p>
     *
     * @param transport the primary notification transport for scope-aware delivery
     */
    public SpectorEventBus(NotificationTransport<SpectorNodeEvent> transport) {
        super(transport);
    }
}
