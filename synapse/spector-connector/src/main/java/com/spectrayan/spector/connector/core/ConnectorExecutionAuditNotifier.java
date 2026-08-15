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
package com.spectrayan.spector.connector.core;

import com.spectrayan.spector.connector.model.ExecutionRecord;
import com.spectrayan.spector.connector.spi.ExecutionLogger;

import org.apache.camel.Exchange;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeFailedEvent;
import org.apache.camel.support.EventNotifierSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;

/**
 * Generic Camel EventNotifier that records execution metrics and traces
 * for all connector routes (inbound ingestion, outbound notifications,
 * chat communications, import/export, etc.).
 *
 * <p>If a specialized sink (such as {@code SpectorIngestionSink}) already
 * logged the execution (indicated by the {@code spector-execution-logged}
 * exchange property), this notifier avoids duplicate logging.</p>
 */
public class ConnectorExecutionAuditNotifier extends EventNotifierSupport {

    public static final String EXECUTION_LOGGED_PROPERTY = "spector-execution-logged";
    private static final Logger log = LoggerFactory.getLogger(ConnectorExecutionAuditNotifier.class);

    private final ExecutionLogger executionLogger;

    public ConnectorExecutionAuditNotifier(ExecutionLogger executionLogger) {
        this.executionLogger = Objects.requireNonNull(executionLogger, "ExecutionLogger must not be null");
    }

    @Override
    public void notify(CamelEvent event) throws Exception {
        if (event instanceof ExchangeCompletedEvent completedEvent) {
            handleExchangeCompleted(completedEvent.getExchange());
        } else if (event instanceof ExchangeFailedEvent failedEvent) {
            handleExchangeFailed(failedEvent.getExchange());
        }
    }

    @Override
    public boolean isEnabled(CamelEvent event) {
        return event instanceof ExchangeCompletedEvent || event instanceof ExchangeFailedEvent;
    }

    private void handleExchangeCompleted(Exchange exchange) {
        if (isAlreadyLogged(exchange)) {
            return;
        }

        String routeId = extractRouteId(exchange);
        if (routeId == null) {
            return;
        }

        String tenantId = extractTenantId(exchange);
        String traceId = extractTraceId(exchange);
        Duration duration = calculateDuration(exchange);
        int docCount = extractDocCount(exchange, 1);

        ExecutionRecord record = ExecutionRecord.success(traceId, routeId, tenantId, docCount, duration);
        executionLogger.log(record);
        exchange.setProperty(EXECUTION_LOGGED_PROPERTY, Boolean.TRUE);

        log.debug("[AuditNotifier] Recorded successful execution for route '{}' (traceId={}, tenantId={}, duration={}ms)",
                routeId, traceId, tenantId, duration.toMillis());
    }

    private void handleExchangeFailed(Exchange exchange) {
        if (isAlreadyLogged(exchange)) {
            return;
        }

        String routeId = extractRouteId(exchange);
        if (routeId == null) {
            return;
        }

        String tenantId = extractTenantId(exchange);
        String traceId = extractTraceId(exchange);
        Duration duration = calculateDuration(exchange);

        Throwable exception = exchange.getException();
        String errorMessage = exception != null ? exception.getMessage() : "Route execution failed";

        ExecutionRecord record = ExecutionRecord.failure(traceId, routeId, tenantId, 0, 1, duration, errorMessage);
        executionLogger.log(record);
        exchange.setProperty(EXECUTION_LOGGED_PROPERTY, Boolean.TRUE);

        log.warn("[AuditNotifier] Recorded failed execution for route '{}' (traceId={}, tenantId={}, error={})",
                routeId, traceId, tenantId, errorMessage);
    }

    private boolean isAlreadyLogged(Exchange exchange) {
        return Boolean.TRUE.equals(exchange.getProperty(EXECUTION_LOGGED_PROPERTY, Boolean.class));
    }

    private String extractRouteId(Exchange exchange) {
        String routeId = exchange.getFromRouteId();
        if (routeId != null && !routeId.isBlank()) {
            return routeId;
        }
        return exchange.getIn().getHeader("spector-route-id", String.class);
    }

    private String extractTenantId(Exchange exchange) {
        String tenantId = exchange.getIn().getHeader("spector-tenant-id", String.class);
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId;
        }
        return "default";
    }

    private String extractTraceId(Exchange exchange) {
        String traceId = exchange.getIn().getHeader("spector-trace-id", String.class);
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return exchange.getExchangeId();
    }

    private int extractDocCount(Exchange exchange, int defaultValue) {
        Integer count = exchange.getIn().getHeader("spector-doc-count", Integer.class);
        return count != null ? count : defaultValue;
    }

    private Duration calculateDuration(Exchange exchange) {
        long created = exchange.getCreated();
        if (created > 0) {
            long elapsed = System.currentTimeMillis() - created;
            return Duration.ofMillis(Math.max(0, elapsed));
        }
        return Duration.ZERO;
    }
}
