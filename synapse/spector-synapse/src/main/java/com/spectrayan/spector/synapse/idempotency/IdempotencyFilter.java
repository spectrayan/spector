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
package com.spectrayan.spector.synapse.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spectrayan.spector.cluster.gateway.RoutingKeyExtractor;
import com.spectrayan.spector.cluster.idempotency.IdempotencyReservation;
import com.spectrayan.spector.cluster.idempotency.IdempotencyStore;
import com.spectrayan.spector.cluster.idempotency.IdempotencyValidator;
import com.spectrayan.spector.cluster.idempotency.RequestFingerprint;
import com.spectrayan.spector.cluster.idempotency.StoredIdempotentResponse;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.synapse.cluster.gateway.GatewayForwardingFilter;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.memory.MemoryBinding;
import com.spectrayan.spector.synapse.memory.MemoryDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Owner and standalone servlet filter that enforces {@code Idempotency-Key} validation,
 * in-flight conflict detection, request fingerprint verification, and response replay
 * across mutating HTTP endpoints.
 */
@Order(Ordered.LOWEST_PRECEDENCE - 5)
public class IdempotencyFilter extends OncePerRequestFilter {

    private final SynapseProperties properties;
    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;
    private final RoutingKeyExtractor routingKeyExtractor = new RoutingKeyExtractor();

    public IdempotencyFilter(SynapseProperties properties, IdempotencyStore idempotencyStore) {
        this(properties, idempotencyStore, defaultObjectMapper());
    }

    public IdempotencyFilter(
            SynapseProperties properties,
            IdempotencyStore idempotencyStore,
            ObjectMapper objectMapper
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "idempotencyStore must not be null");
        this.objectMapper = (objectMapper != null ? objectMapper.copy() : new ObjectMapper())
                .registerModule(new JavaTimeModule());
    }

    private static ObjectMapper defaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (properties.getIdempotency() != null && !properties.getIdempotency().isEnabled()) {
            return true;
        }
        if (properties.getCell() != null && properties.getCell().resolvedRole() == NodeRole.GATEWAY) {
            return true;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }
        return path.startsWith("/actuator") || path.startsWith("/api/v1/health");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String primaryHeader = request.getHeader(IdempotencyValidator.HEADER_IDEMPOTENCY_KEY);
        String altHeader = request.getHeader(IdempotencyValidator.HEADER_ALT_IDEMPOTENCY_KEY);

        if (primaryHeader == null && altHeader == null) {
            filterChain.doFilter(request, response);
            return;
        }

        final String idempotencyKey;
        try {
            idempotencyKey = IdempotencyValidator.extractAndValidate(primaryHeader, altHeader);
        } catch (SpectorValidationException ex) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, ex.errorCode().name(), ex.getMessage());
            return;
        }

        String method = request.getMethod();
        String rawPath = request.getRequestURI();
        if (!IdempotencyValidator.isMutatingRequest(method, rawPath) || isMultipart(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        long contentLength = request.getContentLengthLong();
        if (contentLength > GatewayForwardingFilter.MAX_BUFFERED_BODY_BYTES) {
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "PAYLOAD_TOO_LARGE",
                    "Request body exceeds maximum size of " + GatewayForwardingFilter.MAX_BUFFERED_BODY_BYTES + " bytes");
            return;
        }

        byte[] bodyBytes = request.getInputStream().readNBytes(GatewayForwardingFilter.MAX_BUFFERED_BODY_BYTES + 1);
        if (bodyBytes.length > GatewayForwardingFilter.MAX_BUFFERED_BODY_BYTES) {
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "PAYLOAD_TOO_LARGE",
                    "Request body exceeds maximum size of " + GatewayForwardingFilter.MAX_BUFFERED_BODY_BYTES + " bytes");
            return;
        }

        String pathWithQuery = rawPath;
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            pathWithQuery = rawPath + "?" + request.getQueryString();
        }

        String namespaceScope = resolveNamespaceScope(request);
        RequestFingerprint fingerprint = RequestFingerprint.of(method, pathWithQuery, bodyBytes);
        IdempotencyReservation reservation = idempotencyStore.reserve(namespaceScope, idempotencyKey, fingerprint);

        switch (reservation.status()) {
            case REPLAYED -> {
                StoredIdempotentResponse stored = reservation.response();
                response.setStatus(stored.statusCode());
                for (Map.Entry<String, List<String>> entry : stored.headersWithReplayMarker().entrySet()) {
                    String headerName = entry.getKey();
                    if (!"transfer-encoding".equalsIgnoreCase(headerName)
                            && !"content-length".equalsIgnoreCase(headerName)) {
                        response.setHeader(headerName, null);
                        for (String val : entry.getValue()) {
                            response.addHeader(headerName, val);
                        }
                    }
                }
                byte[] replayBody = stored.body();
                response.setContentLength(replayBody.length);
                response.getOutputStream().write(replayBody);
                response.getOutputStream().flush();
            }
            case MISMATCH -> writeError(
                    response,
                    422,
                    ErrorCode.IDEMPOTENCY_KEY_REUSED.name(),
                    ErrorCode.IDEMPOTENCY_KEY_REUSED.format(idempotencyKey, namespaceScope)
            );
            case IN_FLIGHT -> {
                response.setHeader("Retry-After", "1");
                writeError(
                        response,
                        HttpServletResponse.SC_CONFLICT,
                        ErrorCode.IDEMPOTENCY_IN_FLIGHT.name(),
                        ErrorCode.IDEMPOTENCY_IN_FLIGHT.format(idempotencyKey, namespaceScope)
                );
            }
            case ACQUIRED -> {
                CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request, bodyBytes);
                ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
                boolean committed = false;
                try {
                    filterChain.doFilter(wrappedRequest, wrappedResponse);
                    int status = wrappedResponse.getStatus();
                    if (status >= 200 && status < 300) {
                        byte[] respBytes = wrappedResponse.getContentAsByteArray();
                        Map<String, List<String>> respHeaders = extractResponseHeaders(wrappedResponse);
                        StoredIdempotentResponse toStore = new StoredIdempotentResponse(
                                status,
                                respHeaders,
                                respBytes,
                                System.currentTimeMillis()
                        );
                        idempotencyStore.commit(namespaceScope, idempotencyKey, fingerprint, toStore);
                        committed = true;
                    }
                } finally {
                    if (!committed) {
                        idempotencyStore.release(namespaceScope, idempotencyKey);
                    }
                    wrappedResponse.copyBodyToResponse();
                }
            }
        }
    }

    private String resolveNamespaceScope(HttpServletRequest request) {
        Object attr = request.getAttribute(MemoryBinding.ATTRIBUTE_KEY);
        if (attr instanceof MemoryBinding binding) {
            String tenantId = binding.context() != null ? binding.context().tenantId() : null;
            return IdempotencyValidator.normalizeNamespaceScope(tenantId, binding.namespaceId());
        }
        if (MemoryScope.isActive()) {
            String tenantId = MemoryScope.isTenantActive() ? MemoryScope.tenantId() : null;
            return IdempotencyValidator.normalizeNamespaceScope(tenantId, MemoryScope.namespaceId());
        }
        String cellId = properties.getCell() != null ? properties.getCell().getId() : "default";
        RoutingKey key = routingKeyExtractor.extract(
                cellId,
                request::getHeader,
                request::getParameter,
                request.getRequestURI()
        );
        return IdempotencyValidator.normalizeNamespaceScope(key);
    }

    private static boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    private static Map<String, List<String>> extractResponseHeaders(HttpServletResponse response) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        Collection<String> names = response.getHeaderNames();
        if (names != null) {
            for (String name : names) {
                Collection<String> vals = response.getHeaders(name);
                if (vals != null && !vals.isEmpty()) {
                    headers.put(name, new ArrayList<>(vals));
                }
            }
        }
        if (response.getContentType() != null && !headers.containsKey("Content-Type")) {
            headers.put("Content-Type", List.of(response.getContentType()));
        }
        return headers;
    }

    private void writeError(HttpServletResponse response, int status, String errorCode, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        MemoryDto.ErrorResponse errorResponse = new MemoryDto.ErrorResponse(status, errorCode, message);
        byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }

    private static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        CachedBodyHttpServletRequest(HttpServletRequest request, byte[] cachedBody) {
            super(request);
            this.cachedBody = cachedBody != null ? cachedBody : new byte[0];
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return bais.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {}

                @Override
                public int read() {
                    return bais.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return bais.read(b, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String enc = getCharacterEncoding();
            Charset charset = (enc != null && !enc.isBlank()) ? Charset.forName(enc) : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return cachedBody.length;
        }

        @Override
        public long getContentLengthLong() {
            return cachedBody.length;
        }
    }
}
