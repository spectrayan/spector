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
package com.spectrayan.spector.synapse.security.kms;

import java.util.Map;
import java.util.Objects;

/**
 * Resolved connection and authentication context for invoking an external or local KMS
 * on behalf of a tenant (ADR-0088 §5, Issue #1070).
 */
public record KmsConnectionContext(
        String tenantId,
        String provider,
        String kekKeyId,
        String credentialName,
        String secret,
        String region,
        String endpoint,
        Map<String, Object> properties) {

    public KmsConnectionContext {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        properties = properties != null ? Map.copyOf(properties) : Map.of();
    }

    public String regionOrDefault(String fallback) {
        if (region != null && !region.isBlank()) {
            return region.trim();
        }
        Object propRegion = properties.get("region");
        if (propRegion instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return fallback;
    }

    public String endpointOrDefault(String fallback) {
        if (endpoint != null && !endpoint.isBlank()) {
            return trimTrailingSlash(endpoint.trim());
        }
        Object propEndpoint = properties.get("endpoint");
        if (propEndpoint instanceof String s && !s.isBlank()) {
            return trimTrailingSlash(s.trim());
        }
        return fallback != null ? trimTrailingSlash(fallback) : null;
    }

    public String mountPathOrDefault(String fallback) {
        Object propMount = properties.get("mountPath");
        if (propMount instanceof String s && !s.isBlank()) {
            return trimSlashes(s.trim());
        }
        Object propMountAlt = properties.get("mount");
        if (propMountAlt instanceof String s && !s.isBlank()) {
            return trimSlashes(s.trim());
        }
        return fallback;
    }

    public String vaultNamespace() {
        Object ns = properties.get("vaultNamespace");
        if (ns instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return null;
    }

    public String awsAccessKeyId() {
        Object propKey = properties.get("accessKeyId");
        if (propKey instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        if (secret != null && secret.contains(":")) {
            return secret.substring(0, secret.indexOf(':')).trim();
        }
        return "AKIAIOSFODNN7EXAMPLE";
    }

    public String awsSecretAccessKey() {
        if (secret != null && !secret.isBlank()) {
            if (secret.contains(":") && !secret.trim().startsWith("{")) {
                return secret.substring(secret.indexOf(':') + 1).trim();
            }
            return secret.trim();
        }
        Object propSecret = properties.get("secretAccessKey");
        if (propSecret instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return "";
    }

    public String awsSessionToken() {
        Object token = properties.get("sessionToken");
        if (token instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return null;
    }

    private static String trimTrailingSlash(String url) {
        String result = url;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String trimSlashes(String path) {
        String result = path;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
