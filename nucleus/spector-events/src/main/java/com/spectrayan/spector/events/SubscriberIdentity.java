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

import java.util.Set;

/**
 * Identity context for a notification subscriber — used for scope matching.
 *
 * <p>When a client subscribes to the event stream (SSE, WebSocket, etc.),
 * the transport builds a {@code SubscriberIdentity} from the authentication
 * context. The transport then uses this identity to match against each event's
 * {@link NotificationScope} to decide whether to deliver the event.</p>
 *
 * <h3>Matching Rules</h3>
 * <ul>
 *   <li>{@link NotificationScope.Global} → always matches</li>
 *   <li>{@link NotificationScope.Tenant} → matches if {@code tenantId} equals</li>
 *   <li>{@link NotificationScope.User} → matches if {@code userId} equals</li>
 *   <li>{@link NotificationScope.Agent} → matches if {@code agentId} equals</li>
 *   <li>{@link NotificationScope.Topic} → matches if {@code subscribedTopics} contains</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   var identity = new SubscriberIdentity("user-1", "acme", null, Set.of("USER"), Set.of());
 *   var scope = NotificationScope.user("user-1");
 *   assert identity.matches(scope); // true
 * }</pre>
 *
 * @param userId           the authenticated user's ID (null for anonymous/system)
 * @param tenantId         the user's tenant/organization ID (null for single-tenant)
 * @param agentId          the agent/service account ID (null for human users)
 * @param roles            the user's roles (e.g., ADMIN, USER) — for future role-based scoping
 * @param subscribedTopics explicit topic subscriptions (e.g., "ops-alerts")
 */
public record SubscriberIdentity(
        String userId,
        String tenantId,
        String agentId,
        Set<String> roles,
        Set<String> subscribedTopics
) {
    /** Compact constructor — normalizes nulls to empty sets. */
    public SubscriberIdentity {
        roles = roles != null ? Set.copyOf(roles) : Set.of();
        subscribedTopics = subscribedTopics != null ? Set.copyOf(subscribedTopics) : Set.of();
    }

    /**
     * Tests whether this subscriber should receive an event with the given scope.
     *
     * @param scope the event's notification scope
     * @return true if this subscriber matches the scope
     */
    public boolean matches(NotificationScope scope) {
        return switch (scope) {
            case NotificationScope.Global g -> true;
            case NotificationScope.Tenant t ->
                    tenantId != null && tenantId.equals(t.tenantId());
            case NotificationScope.User u ->
                    userId != null && userId.equals(u.userId());
            case NotificationScope.Agent a ->
                    agentId != null && agentId.equals(a.agentId());
            case NotificationScope.Topic t ->
                    subscribedTopics.contains(t.topic());
        };
    }

    /** Creates an anonymous identity that only receives broadcast events. */
    public static SubscriberIdentity anonymous() {
        return new SubscriberIdentity(null, null, null, Set.of(), Set.of());
    }

    /** Creates a user identity with minimal context. */
    public static SubscriberIdentity ofUser(String userId, String tenantId) {
        return new SubscriberIdentity(userId, tenantId, null, Set.of(), Set.of());
    }
}
