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
package com.spectrayan.spector.synapse.admin.audit;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.spectrayan.spector.events.NotificationScope;
import com.spectrayan.spector.events.SpectorEvent;
import com.spectrayan.spector.synapse.admin.audit.dto.AdminAuditDto.AuditEventDto;

/**
 * Domain event emitted onto Spector's {@link com.spectrayan.spector.events.EventBus}
 * whenever an administrative or privileged audit record is appended to {@code audit_event}
 * (ADR-0087, Issue #1059).
 *
 * <p>Enables real-time SIEM export connectors, Kafka/MQTT transports, and audit stream
 * subscribers to observe every privileged action without polling the database.</p>
 */
public record AuditSpectorEvent(
        Instant timestamp,
        String eventType,
        AuditEventDto auditEvent
) implements SpectorEvent {

    public AuditSpectorEvent {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(auditEvent, "auditEvent must not be null");
    }

    public static AuditSpectorEvent from(AuditEventDto dto) {
        String normAction = dto.action() != null
                ? dto.action().trim().toLowerCase().replace('_', '.')
                : "unknown";
        return new AuditSpectorEvent(
                dto.ts() != null ? dto.ts() : Instant.now(),
                "audit.event." + normAction,
                dto
        );
    }

    @Override
    public NotificationScope scope() {
        if (auditEvent.tenantId() != null && !auditEvent.tenantId().isBlank()) {
            return NotificationScope.tenant(auditEvent.tenantId());
        }
        return NotificationScope.BROADCAST;
    }

    @Override
    public Map<String, String> context() {
        Map<String, String> ctx = new LinkedHashMap<>();
        if (auditEvent.tenantId() != null && !auditEvent.tenantId().isBlank()) {
            ctx.put(ContextKeys.TENANT, auditEvent.tenantId());
        }
        if (auditEvent.namespaceId() != null && !auditEvent.namespaceId().isBlank()) {
            ctx.put(ContextKeys.NAMESPACE, auditEvent.namespaceId());
        }
        if (auditEvent.requestId() != null && !auditEvent.requestId().isBlank()) {
            ctx.put(ContextKeys.CORRELATION, auditEvent.requestId());
        }
        return Collections.unmodifiableMap(ctx);
    }
}
