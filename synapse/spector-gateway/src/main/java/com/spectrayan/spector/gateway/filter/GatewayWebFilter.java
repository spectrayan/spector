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
package com.spectrayan.spector.gateway.filter;

import com.spectrayan.spector.cluster.gateway.ForwardRequest;
import com.spectrayan.spector.cluster.gateway.ForwardResponse;
import com.spectrayan.spector.cluster.gateway.GatewayForwarder;
import com.spectrayan.spector.cluster.gateway.RoutingKeyExtractor;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reactive WebFilter intercepting cell ingress requests and proxying to authoritative owner nodes
 * with backpressure and streaming I/O (ADR-0034 §8, ADR-0081 §8 Phase 2).
 *
 * <p><strong>Dual-mode body handling:</strong>
 * <ul>
 *   <li>GET/HEAD/DELETE/OPTIONS (no body): forwards immediately without buffering.</li>
 *   <li>POST/PUT/PATCH with Content-Length &le; cap: buffers for 421 retry replay.</li>
 *   <li>POST/PUT/PATCH with Content-Length &gt; cap: fast-fails with 413.</li>
 *   <li>POST/PUT/PATCH chunked (no Content-Length): buffers up to cap; excess triggers 413.</li>
 * </ul>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class GatewayWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayWebFilter.class);

    /** HTTP methods that carry a request body and support 421 retry replay. */
    private static final Set<HttpMethod> BODY_METHODS = Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH);

    private final GatewayProperties properties;
    private final GatewayForwarder forwarder;
    private final RoutingKeyExtractor routingKeyExtractor;

    public GatewayWebFilter(
            GatewayProperties properties,
            GatewayForwarder forwarder,
            RoutingKeyExtractor routingKeyExtractor
    ) {
        this.properties = properties;
        this.forwarder = forwarder;
        this.routingKeyExtractor = routingKeyExtractor;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getRawPath();

        if (shouldNotFilter(path)) {
            return chain.filter(exchange);
        }

        int maxBufferedBytes = properties.getRouting().getGateway().getMaxBufferedBodyBytes();
        long contentLength = request.getHeaders().getContentLength();
        HttpMethod httpMethod = request.getMethod();

        // Fast-fail: known Content-Length exceeds cap
        if (contentLength > maxBufferedBytes) {
            log.warn("Gateway rejected request body: Content-Length {} exceeds max limit of {} bytes",
                    contentLength, maxBufferedBytes);
            return writeError(exchange.getResponse(), HttpStatus.PAYLOAD_TOO_LARGE,
                    "PAYLOAD_TOO_LARGE", "Request body exceeds maximum size of " + maxBufferedBytes + " bytes");
        }

        String cellId = properties.getCell().getId();
        RoutingKey routingKey = routingKeyExtractor.extract(
                cellId,
                name -> request.getHeaders().getFirst(name),
                name -> request.getQueryParams().getFirst(name),
                path
        );

        String uriPath = request.getURI().getRawPath();
        String rawQuery = request.getURI().getRawQuery();
        if (rawQuery != null && !rawQuery.isBlank()) {
            uriPath = uriPath + "?" + rawQuery;
        }

        Map<String, List<String>> headers = new HashMap<>();
        request.getHeaders().forEach((key, values) -> {
            headers.put(key, Collections.unmodifiableList(values));
        });

        String idempotencyKey = request.getHeaders().getFirst("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = request.getHeaders().getFirst("X-Idempotency-Key");
        }

        String methodStr = httpMethod != null ? httpMethod.name() : "GET";
        String finalUriPath = uriPath;
        String finalIdempotencyKey = idempotencyKey;

        // Dual-mode body handling:
        // - Body methods (POST/PUT/PATCH): buffer for 421 retry replay
        // - Non-body methods (GET/HEAD/DELETE/OPTIONS): skip body collection entirely
        boolean hasBody = httpMethod != null && BODY_METHODS.contains(httpMethod) && contentLength != 0;

        if (!hasBody) {
            // No body to buffer — forward immediately with empty body supplier
            ForwardRequest forwardRequest = ForwardRequest.ofStream(
                    methodStr, finalUriPath, headers,
                    InputStream::nullInputStream, 0L,
                    finalIdempotencyKey
            );
            return forwardAndWrite(exchange, routingKey, forwardRequest);
        }

        // Buffer body for POST/PUT/PATCH (enables 421 retry replay)
        return DataBufferUtils.join(request.getBody(), maxBufferedBytes)
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .defaultIfEmpty(new byte[0])
                .onErrorResume(DataBufferLimitException.class, ex -> {
                    log.warn("Gateway rejected streaming request body: read bytes exceeded max limit of {} bytes", maxBufferedBytes);
                    return Mono.error(ex);
                })
                .flatMap(bodyBytes -> {
                    ForwardRequest forwardRequest = ForwardRequest.ofBytes(
                            methodStr, finalUriPath, headers,
                            bodyBytes, finalIdempotencyKey
                    );
                    return forwardAndWrite(exchange, routingKey, forwardRequest);
                })
                .onErrorResume(DataBufferLimitException.class, ex ->
                        writeError(exchange.getResponse(), HttpStatus.PAYLOAD_TOO_LARGE,
                                "PAYLOAD_TOO_LARGE", "Request body exceeds maximum size of " + maxBufferedBytes + " bytes"))
                .onErrorResume(Exception.class, ex -> {
                    log.error("Gateway forward failed for namespace '{}': {}", routingKey.namespaceId(), ex.getMessage(), ex);
                    return writeError(exchange.getResponse(), HttpStatus.BAD_GATEWAY,
                            "GATEWAY_ROUTING_FAILURE", "Gateway routing failure for namespace: " + routingKey.namespaceId());
                });
    }

    /**
     * Dispatches the forward request on boundedElastic and writes the streaming response.
     */
    private Mono<Void> forwardAndWrite(ServerWebExchange exchange, RoutingKey routingKey, ForwardRequest forwardRequest) {
        return Mono.fromCallable(() -> forwarder.forward(routingKey, forwardRequest))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(forwardResponse -> writeForwardResponse(exchange.getResponse(), forwardResponse))
                .onErrorResume(Exception.class, ex -> {
                    log.error("Gateway forward failed for namespace '{}': {}", routingKey.namespaceId(), ex.getMessage(), ex);
                    return writeError(exchange.getResponse(), HttpStatus.BAD_GATEWAY,
                            "GATEWAY_ROUTING_FAILURE", "Gateway routing failure for namespace: " + routingKey.namespaceId());
                });
    }

    private boolean shouldNotFilter(String path) {
        if (path == null || !path.startsWith("/api/v1/")) {
            return true;
        }
        // Only skip health and actuator paths (ADR-0081 §9).
        // Auth (/api/v1/auth) and events (/api/v1/events) are forwarded to owner.
        return path.startsWith("/actuator")
                || path.startsWith("/api/v1/health");
    }

    private Mono<Void> writeForwardResponse(ServerHttpResponse response, ForwardResponse forwardResponse) {
        response.setStatusCode(HttpStatusCode.valueOf(forwardResponse.statusCode()));

        HttpHeaders httpHeaders = response.getHeaders();
        for (Map.Entry<String, List<String>> entry : forwardResponse.headers().entrySet()) {
            String name = entry.getKey();
            if (!"transfer-encoding".equalsIgnoreCase(name) && !"content-length".equalsIgnoreCase(name)) {
                for (String val : entry.getValue()) {
                    httpHeaders.add(name, val);
                }
            }
        }

        DataBufferFactory bufferFactory = response.bufferFactory();
        Flux<DataBuffer> responseStream = DataBufferUtils.readInputStream(
                forwardResponse::bodyStream,
                bufferFactory,
                8192
        ).doFinally(signal -> {
            try {
                forwardResponse.close();
            } catch (Exception ignored) {}
        });

        return response.writeWith(responseStream);
    }

    /**
     * Writes a JSON error response with proper escaping to prevent injection.
     */
    private Mono<Void> writeError(ServerHttpResponse response, HttpStatus status, String errorCode, String message) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Escape JSON special characters to prevent broken envelopes
        String safeMessage = escapeJson(message);
        String safeError = escapeJson(errorCode);
        String json = "{\"status\":" + status.value()
                + ",\"error\":\"" + safeError
                + "\",\"message\":\"" + safeMessage + "\"}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
