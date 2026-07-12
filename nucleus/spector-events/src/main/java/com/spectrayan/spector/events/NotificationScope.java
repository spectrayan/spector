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

/**
 * Defines the audience scope for event/notification delivery.
 *
 * <p>Each {@link com.spectrayan.spector.node.event.SpectorEvent} declares a scope
 * that determines which subscribers receive it. The {@link NotificationTransport}
 * uses scope matching to filter delivery — e.g., a {@link User}-scoped event
 * only reaches SSE connections authenticated as that user.</p>
 *
 * <h3>Scope Types</h3>
 * <ul>
 *   <li>{@link Global} — broadcast to all subscribers (cluster health, node lifecycle)</li>
 *   <li>{@link Tenant} — all users within a specific tenant (memory diagnostics, graph pulses)</li>
 *   <li>{@link User} — a specific user (ingestion progress, query traces)</li>
 *   <li>{@link Agent} — a specific agent/service account (MCP tool execution results)</li>
 *   <li>{@link Topic} — named topic channel (future: ops-alerts, admin-events)</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   // User-scoped ingestion event
 *   event.scope() → NotificationScope.user("user-123")
 *
 *   // Global cluster event
 *   event.scope() → NotificationScope.BROADCAST
 *
 *   // Tenant-scoped diagnostic
 *   event.scope() → NotificationScope.tenant("acme-corp")
 * }</pre>
 *
 * <h3>Future: Compound Scoping</h3>
 * <p>The current design supports single-scope targeting. When compound targeting
 * is needed (e.g., "notify User X AND all admins of Tenant Y"), extend this
 * sealed hierarchy with:</p>
 * <pre>{@code
 *   record Composite(List<NotificationScope> scopes, MatchMode mode)
 *       implements NotificationScope {}
 *   enum MatchMode { ANY_OF, ALL_OF }
 * }</pre>
 * <p>This preserves backward compatibility — existing single-scope consumers
 * continue to work via pattern matching, while new consumers can handle
 * {@code Composite} for multi-target delivery.</p>
 *
 * @see NotificationTransport
 */
public sealed interface NotificationScope {

    /** Broadcast to every connected subscriber regardless of identity. */
    record Global() implements NotificationScope {}

    /** Target all subscribers within a specific tenant organization. */
    record Tenant(String tenantId) implements NotificationScope {
        public Tenant {
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId must not be null or blank");
            }
        }
    }

    /** Target a specific authenticated user. */
    record User(String userId) implements NotificationScope {
        public User {
            if (userId == null || userId.isBlank()) {
                throw new IllegalArgumentException("userId must not be null or blank");
            }
        }
    }

    /** Target a specific agent or service account (e.g., MCP client). */
    record Agent(String agentId) implements NotificationScope {
        public Agent {
            if (agentId == null || agentId.isBlank()) {
                throw new IllegalArgumentException("agentId must not be null or blank");
            }
        }
    }

    /** Target subscribers of a named topic channel (e.g., "ops-alerts"). */
    record Topic(String topic) implements NotificationScope {
        public Topic {
            if (topic == null || topic.isBlank()) {
                throw new IllegalArgumentException("topic must not be null or blank");
            }
        }
    }

    // ── Factory Methods ─────────────────────────────────────────────

    /** Singleton broadcast scope — delivered to all subscribers. */
    NotificationScope BROADCAST = new Global();

    /** Creates a user-scoped notification target. */
    static NotificationScope user(String userId) {
        return new User(userId);
    }

    /** Creates a tenant-scoped notification target. */
    static NotificationScope tenant(String tenantId) {
        return new Tenant(tenantId);
    }

    /** Creates an agent-scoped notification target. */
    static NotificationScope agent(String agentId) {
        return new Agent(agentId);
    }

    /** Creates a topic-scoped notification target. */
    static NotificationScope topic(String topic) {
        return new Topic(topic);
    }
}
