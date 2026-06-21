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
package com.spectrayan.spector.events;

import java.time.Instant;
import java.util.Map;

/**
 * Root interface for all Spector events.
 *
 * <p>Product-level event contract — equivalent to Spring's {@code ApplicationEvent}
 * or Redis's {@code KeyspaceNotification}. Distinguishes Spector events from other
 * libraries' events when embedded as a JAR, Spring Boot starter, or Micronaut bean.</p>
 *
 * <p>This interface is intentionally <b>NOT sealed</b>. Each module defines its own
 * sealed sub-hierarchy for exhaustive pattern matching within that domain:</p>
 * <ul>
 *   <li>{@link SpectorTelemetryEvent} — raw performance/diagnostic telemetry</li>
 *   <li>{@code SpectorLifecycleEvent} — memory system lifecycle (checkpoint, compaction)</li>
 *   <li>{@code SpectorNodeEvent} — node domain events (search, ingest, MCP, cluster)</li>
 * </ul>
 *
 * <h3>Design Rationale</h3>
 * <ul>
 *   <li><b>Interface, not abstract class</b> — Java records cannot extend classes.
 *       All Spector events are records for immutability, compact allocation, and
 *       auto-generated equals/hashCode/toString.</li>
 *   <li><b>Optional defaults</b> — {@link #source()}, {@link #scope()}, and
 *       {@link #context()} have sensible defaults so lightweight telemetry events
 *       don't pay for features they don't use.</li>
 *   <li><b>Framework-agnostic</b> — no dependency on Spring, Micronaut, or CDI.
 *       Framework bridges subscribe to {@link EventBus} and re-publish to
 *       framework-native event systems.</li>
 *   <li><b>Transport-ready</b> — {@link #eventType()} maps to Kafka topics, MQTT
 *       topic segments, AMQP routing keys, NATS subjects, and Apache Camel
 *       endpoint URIs. {@link #context()} maps to Kafka headers, MQTT v5 user
 *       properties, and Camel message headers.</li>
 * </ul>
 *
 * <h3>Framework Integration</h3>
 * <pre>{@code
 *   // Spring Boot — @EventListener works with any object type
 *   @EventListener
 *   public void onCheckpoint(CheckpointCompletedEvent event) { ... }
 *
 *   // Micronaut
 *   @EventListener
 *   public void onCheckpoint(CheckpointCompletedEvent event) { ... }
 *
 *   // CDI (Quarkus)
 *   public void onCheckpoint(@Observes CheckpointCompletedEvent event) { ... }
 * }</pre>
 *
 * @see EventBus
 * @see NotificationScope
 * @see NotificationTransport
 */
public interface SpectorEvent {

    /** When the event occurred. */
    Instant timestamp();

    /**
     * Dotted event type name — used for routing across all transports.
     *
     * <p>Convention: {@code category.subcategory.action}</p>
     * <ul>
     *   <li>{@code "lifecycle.checkpoint.completed"}</li>
     *   <li>{@code "search.completed"}</li>
     *   <li>{@code "telemetry.simd.kernel"}</li>
     *   <li>{@code "ingestion.completed"}</li>
     * </ul>
     *
     * <h4>Transport Mapping</h4>
     * <table>
     *   <tr><th>Transport</th><th>Mapping</th></tr>
     *   <tr><td>SSE</td><td>{@code event:} field</td></tr>
     *   <tr><td>Kafka</td><td>Topic name: {@code spector.{eventType}}</td></tr>
     *   <tr><td>MQTT</td><td>Topic segment</td></tr>
     *   <tr><td>AMQP</td><td>Routing key</td></tr>
     *   <tr><td>NATS</td><td>Subject</td></tr>
     *   <tr><td>Apache Camel</td><td>Endpoint URI: {@code spector:{eventType}}</td></tr>
     * </table>
     */
    String eventType();

    /**
     * The object that originated this event.
     *
     * <p>Mirrors {@link java.util.EventObject#getSource()} for familiarity.
     * Returns {@code null} if no source is relevant (the common case for
     * lightweight telemetry events).</p>
     *
     * @return the source object, or null
     */
    default Object source() { return null; }

    /**
     * Notification scope — determines which subscribers receive this event.
     *
     * <p>Used by {@link NotificationTransport} for scope-based filtering,
     * and by MQTT/AMQP transports for topic/routing construction.</p>
     *
     * <p>Defaults to {@link NotificationScope#BROADCAST} (all subscribers).
     * Override in event records to restrict delivery:</p>
     * <ul>
     *   <li>{@link NotificationScope.User} — ingestion progress, query traces</li>
     *   <li>{@link NotificationScope.Tenant} — memory diagnostics, graph pulses</li>
     *   <li>{@link NotificationScope.Global} — node health, cluster topology</li>
     *   <li>{@link NotificationScope.Agent} — MCP tool execution results</li>
     *   <li>{@link NotificationScope.Topic} — named channels (ops-alerts, etc.)</li>
     * </ul>
     *
     * @return the notification scope for this event
     * @see NotificationScope
     */
    default NotificationScope scope() { return NotificationScope.BROADCAST; }

    /**
     * Generic context attributes — identity, routing, and correlation metadata.
     *
     * <p>Carried as Kafka headers, MQTT v5 user properties, AMQP headers,
     * or Apache Camel message headers. Core (OSS) uses generic keys;
     * enterprise enriches with tenant/namespace/user context.</p>
     *
     * <p>All keys are namespaced with {@code "spector."} prefix to avoid
     * collisions with headers from other systems in shared transports.</p>
     *
     * @return unmodifiable context map (empty if no context)
     * @see ContextKeys
     */
    default Map<String, String> context() { return Map.of(); }

    /**
     * Well-known context keys for {@link #context()}.
     *
     * <p>All keys use the {@code "spector."} prefix to avoid collisions
     * with Kafka headers, MQTT user properties, and Apache Camel message
     * headers from other systems.</p>
     *
     * <h4>Core Keys (Set by OSS)</h4>
     * <ul>
     *   <li>{@link #INSTANCE} — memory instance identifier (e.g., basePath)</li>
     *   <li>{@link #PARTITION} — partition identifier</li>
     *   <li>{@link #NODE} — originating node ID</li>
     *   <li>{@link #CORRELATION} — distributed trace correlation ID</li>
     * </ul>
     *
     * <h4>Enterprise Keys (Set by Enterprise Layer)</h4>
     * <ul>
     *   <li>{@link #TENANT} — tenant/organization ID</li>
     *   <li>{@link #NAMESPACE} — user/agent namespace</li>
     * </ul>
     */
    interface ContextKeys {
        /** Memory instance identifier (e.g., base path or instance name). */
        String INSTANCE    = "spector.instance";
        /** Partition identifier within a memory instance. */
        String PARTITION   = "spector.partition";
        /** Originating node ID in a cluster deployment. */
        String NODE        = "spector.node";
        /** Distributed trace correlation ID. */
        String CORRELATION = "spector.correlation";
        /** Tenant/organization ID (enterprise — set by enterprise layer, not core). */
        String TENANT      = "spector.tenant";
        /** User/agent namespace (enterprise — set by enterprise layer, not core). */
        String NAMESPACE   = "spector.namespace";
    }
}
