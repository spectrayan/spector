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
package com.spectrayan.spector.cluster.gateway;

import com.spectrayan.spector.cluster.routing.RoutingKey;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standard routing key extractor for Spector gateway layers (ADR-0081 §8).
 * Decoupled from Servlet and WebFlux APIs to allow shared extraction logic.
 */
public class RoutingKeyExtractor {

    public static final String DEFAULT_NAMESPACE = "default";
    public static final String DEFAULT_CELL_ID = "default";

    private static final Pattern NAMESPACE_PATH_PATTERN =
            Pattern.compile("^/api/v1/namespaces/([^/?]+)(?:/.*)?$");

    private final ObjectMapper objectMapper;

    public RoutingKeyExtractor() {
        this(new ObjectMapper());
    }

    public RoutingKeyExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Extracts a {@link RoutingKey} from generic request metadata functions.
     *
     * @param cellId       current cell ID
     * @param headerLookup header retrieval function
     * @param paramLookup  query parameter retrieval function
     * @param uriPath      request URI path
     * @return resolved {@link RoutingKey}
     */
    public RoutingKey extract(
            String cellId,
            Function<String, String> headerLookup,
            Function<String, String> paramLookup,
            String uriPath
    ) {
        String effectiveCellId = (cellId != null && !cellId.isBlank()) ? cellId.trim() : DEFAULT_CELL_ID;

        String headerNs = headerLookup != null ? headerLookup.apply(GatewayForwarder.HEADER_NAMESPACE) : null;
        String paramNs = paramLookup != null ? paramLookup.apply("namespace") : null;
        String pathNs = null;
        if (uriPath != null) {
            Matcher m = NAMESPACE_PATH_PATTERN.matcher(uriPath);
            if (m.matches()) {
                pathNs = m.group(1);
            }
        }

        String namespaceId = (headerNs != null && !headerNs.isBlank())
                ? headerNs.trim()
                : (paramNs != null && !paramNs.isBlank()
                        ? paramNs.trim()
                        : (pathNs != null && !pathNs.isBlank() ? pathNs.trim() : null));

        String headerTenant = headerLookup != null ? headerLookup.apply(GatewayForwarder.HEADER_TENANT) : null;
        String paramTenant = paramLookup != null ? paramLookup.apply("tenant") : null;
        String tenantId = (headerTenant != null && !headerTenant.isBlank())
                ? headerTenant.trim()
                : (paramTenant != null && !paramTenant.isBlank() ? paramTenant.trim() : null);

        // Fallback: extract namespace (sub) and tenant from JWT Bearer token if not explicitly provided
        if (namespaceId == null || tenantId == null) {
            String authHeader = headerLookup != null ? headerLookup.apply("Authorization") : null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                if (namespaceId == null) {
                    namespaceId = extractSubFromJwt(token);
                }
                if (tenantId == null) {
                    tenantId = extractTenantFromJwt(token);
                }
            }
        }

        if (namespaceId == null || namespaceId.isBlank()) {
            namespaceId = DEFAULT_NAMESPACE;
        }

        return (tenantId != null && !tenantId.isBlank())
                ? RoutingKey.ofTenanted(effectiveCellId, tenantId, namespaceId)
                : RoutingKey.ofUntenanted(effectiveCellId, namespaceId);
    }

    private String extractSubFromJwt(String token) {
        if (token == null || token.isBlank()) return null;
        String[] parts = token.split("\\.");
        if (parts.length < 2) return null;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode node = objectMapper.readTree(decoded);
            if (node.has("sub")) {
                String sub = node.get("sub").asText();
                return (sub != null && !sub.isBlank()) ? sub.trim() : null;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractTenantFromJwt(String token) {
        if (token == null || token.isBlank()) return null;
        String[] parts = token.split("\\.");
        if (parts.length < 2) return null;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode node = objectMapper.readTree(decoded);
            if (node.has("tenant_id")) {
                String tid = node.get("tenant_id").asText();
                return (tid != null && !tid.isBlank()) ? tid.trim() : null;
            }
            if (node.has("tenantId")) {
                String tid = node.get("tenantId").asText();
                return (tid != null && !tid.isBlank()) ? tid.trim() : null;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
