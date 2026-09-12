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
package com.spectrayan.spector.synapse.cluster.gateway;

import com.spectrayan.spector.cluster.routing.ResolvedRoute;
import com.spectrayan.spector.cluster.routing.RouteBinding;
import com.spectrayan.spector.cluster.routing.RouteSource;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.cluster.routing.cache.WaterfallRoutingResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Service that resolves target cell owner nodes and forwards ingress requests
 * with bounded retries, idempotency key reuse, and degraded fallback protection
 * (ADR-0034 §8, Req R7.1–R7.6, Invariant K7).
 */
public class GatewayForwarder {

    private static final Logger log = LoggerFactory.getLogger(GatewayForwarder.class);

    public static final String HEADER_NAMESPACE = "X-Spector-Namespace";
    public static final String HEADER_TENANT = "X-Spector-Tenant";
    public static final String HEADER_EPOCH = "X-Spector-Epoch";
    public static final String HEADER_FENCE = "X-Spector-Fence";

    private final WaterfallRoutingResolver resolver;
    private final int retryMax;
    private final GatewayHttpTransport transport;
    private final Function<String, String> nodeBaseUrlResolver;

    public GatewayForwarder(
            WaterfallRoutingResolver resolver,
            int retryMax,
            GatewayHttpTransport transport,
            Function<String, String> nodeBaseUrlResolver
    ) {
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
        this.retryMax = Math.max(0, retryMax);
        this.transport = transport != null ? transport : GatewayHttpTransport.defaultJdkTransport(Duration.ofSeconds(10));
        this.nodeBaseUrlResolver = nodeBaseUrlResolver != null ? nodeBaseUrlResolver : defaultNodeUrlResolver(7070);
    }

    /**
     * Forwards an incoming request to the authoritative owner node, retrying on STALE_ROUTE (421)
     * up to {@code retryMax} times (Req R7.4, R7.5).
     *
     * @param routingKey the routing key identifying the namespace
     * @param request    the forward request representation
     * @return forward response
     * @throws Exception if transport-level dispatch fails
     */
    public ForwardResponse forward(RoutingKey routingKey, ForwardRequest request) throws Exception {
        Objects.requireNonNull(routingKey, "routingKey must not be null");
        Objects.requireNonNull(request, "request must not be null");

        int attempts = 0;
        String explicitTargetOwner = null;

        while (true) {
            attempts++;
            String ownerId;
            long epoch;
            RouteSource source;
            String fence;

            if (explicitTargetOwner != null) {
                ownerId = explicitTargetOwner;
                epoch = 0L;
                fence = "";
                source = RouteSource.REDIS;
                explicitTargetOwner = null;
            } else {
                ResolvedRoute resolved = resolver.resolve(routingKey);
                RouteBinding route = resolved.binding();
                ownerId = resolved.ownerId();
                epoch = resolved.epoch();
                source = resolved.source();
                fence = route.fence() != null ? route.fence() : "";
            }

            String targetBaseUrl = nodeBaseUrlResolver.apply(ownerId);

            Map<String, String> routingHeaders = new HashMap<>();
            routingHeaders.put(HEADER_NAMESPACE, routingKey.namespaceId());
            if (routingKey.tenantId() != null && !routingKey.tenantId().isBlank()) {
                routingHeaders.put(HEADER_TENANT, routingKey.tenantId());
            }
            routingHeaders.put(HEADER_EPOCH, String.valueOf(epoch));
            routingHeaders.put(HEADER_FENCE, fence);

            log.debug("Forwarding request {} to owner '{}' at {} with epoch {} (source={}, attempt={}/{})",
                    request.uriPath(), ownerId, targetBaseUrl, epoch, source, attempts, retryMax + 1);

            ForwardResponse response = transport.send(ownerId, targetBaseUrl, request, routingHeaders);

            // Check for STALE_ROUTE / NOT_OWNER refusal (HTTP 421 Misdirected Request)
            if (response.statusCode() == 421) {
                // Check retry bounds (Req R7.4)
                if (attempts > retryMax) {
                    log.warn("Exhausted bounded retries ({}/{}) for namespace '{}' after node '{}' returned 421; surfacing refusal",
                            attempts - 1, retryMax, routingKey.namespaceId(), ownerId);
                    return new ForwardResponse(response.statusCode(), response.headers(), response.body(), ownerId, attempts);
                }

                // G45: Check if refusing node reported the true authoritative owner
                String reportedOwner = extractReportedOwner(response);
                if (reportedOwner != null && !reportedOwner.isBlank() && !reportedOwner.equals(ownerId)) {
                    log.info("Retargeting gateway forward for namespace '{}' directly to reported owner '{}' (was '{}', attempt {}/{})",
                            routingKey.namespaceId(), reportedOwner, ownerId, attempts, retryMax);
                    explicitTargetOwner = reportedOwner;
                } else if (source == RouteSource.HASH_FALLBACK) {
                    // Req R7.6: If route came from degraded path (HASH_FALLBACK) and node reported no alternative owner, do NOT forward again!
                    log.warn("Target node '{}' refused request for namespace '{}' resolved via degraded HASH_FALLBACK; surfacing refusal immediately (Req R7.6)",
                            ownerId, routingKey.namespaceId());
                    return new ForwardResponse(response.statusCode(), response.headers(), response.body(), ownerId, attempts);
                } else {
                    log.info("Retrying gateway forward for namespace '{}' after 421 refusal from node '{}' (attempt {}/{})",
                            routingKey.namespaceId(), ownerId, attempts, retryMax);
                }

                // Bounded retry (Req R7.4, G40):
                // Invalidate local Caffeine L1 cache, and evict Redis ONLY if mode == HASH (never delete shared Redis overrides!)
                resolver.invalidateLocal(routingKey);
                resolver.invalidateIfHash(routingKey);
                continue;
            }

            return new ForwardResponse(response.statusCode(), response.headers(), response.body(), ownerId, attempts);
        }
    }

    private static String extractReportedOwner(ForwardResponse response) {
        if (response.headers() != null) {
            java.util.List<String> ownerHeaders = response.headers().get("X-Spector-Owner");
            if (ownerHeaders != null && !ownerHeaders.isEmpty()) {
                String val = ownerHeaders.get(0);
                if (val != null && !val.isBlank()) {
                    return val.trim();
                }
            }
        }
        if (response.body() != null && response.body().length > 0) {
            String bodyStr = new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"owner\"\\s*:\\s*\"([^\"]+)\"").matcher(bodyStr);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }

    public static Function<String, String> defaultNodeUrlResolver(int defaultPort) {
        return nodeId -> {
            if (nodeId.startsWith("http://") || nodeId.startsWith("https://")) {
                return nodeId;
            }
            if (nodeId.contains(":")) {
                return "http://" + nodeId;
            }
            return "http://" + nodeId + ":" + defaultPort;
        };
    }
}
