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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.commons.crypto.DestroyableKeyMaterial;
import com.spectrayan.spector.commons.crypto.KeyHealthStatus;
import com.spectrayan.spector.commons.crypto.KeyProvider;
import com.spectrayan.spector.commons.crypto.WrappedKeyResult;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorMemoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.invoke.VarHandle;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Google Cloud KMS {@link KeyProvider} ({@code gcp-kms}) implementing the Cloud KMS REST API v1
 * ({@code projects/.../locations/.../keyRings/.../cryptoKeys/...:encrypt}, {@code :decrypt},
 * and key resource inspection) with {@code additionalAuthenticatedData} (AAD) binding
 * (ADR-0088 §5.2, Issue #1070).
 */
@Component
public class GcpKmsKeyProvider implements KeyProvider {

    private static final Logger log = LoggerFactory.getLogger(GcpKmsKeyProvider.class);
    public static final String PROVIDER_ID = "gcp-kms";
    private static final String DEFAULT_GCP_KMS_ENDPOINT = "https://cloudkms.googleapis.com";

    private final KmsContextResolver contextResolver;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public GcpKmsKeyProvider(
            KmsContextResolver contextResolver,
            @Autowired(required = false) ObjectMapper objectMapper) {
        this(
                contextResolver,
                objectMapper != null ? objectMapper : new ObjectMapper(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    public GcpKmsKeyProvider(
            KmsContextResolver contextResolver,
            ObjectMapper objectMapper,
            HttpClient httpClient) {
        this.contextResolver = Objects.requireNonNull(contextResolver, "contextResolver must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public WrappedKeyResult wrapDek(String tenantId, String kekKeyId, byte[] plaintextDek, byte[] aad) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(kekKeyId, "kekKeyId must not be null");
        Objects.requireNonNull(plaintextDek, "plaintextDek must not be null");

        KmsConnectionContext ctx = contextResolver.resolve(tenantId, PROVIDER_ID, kekKeyId);
        String resourcePath = normalizeResourcePath(kekKeyId);
        String url = ctx.endpointOrDefault(DEFAULT_GCP_KMS_ENDPOINT) + "/v1/" + resourcePath + ":encrypt";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("plaintext", Base64.getEncoder().encodeToString(plaintextDek));
        if (aad != null && aad.length > 0) {
            payload.put("additionalAuthenticatedData", Base64.getEncoder().encodeToString(aad));
        }

        JsonNode root = sendPost(ctx, tenantId, kekKeyId, url, payload);
        String ciphertextB64 = root.path("ciphertext").asText(null);
        if (ciphertextB64 == null || ciphertextB64.isBlank()) {
            throw new SpectorMemoryException(
                    ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                    tenantId,
                    kekKeyId,
                    "GCP Cloud KMS encrypt response missing ciphertext");
        }

        String versionName = root.path("name").asText(resourcePath);
        String version = extractCryptoKeyVersion(versionName);
        byte[] wrappedBytes = Base64.getDecoder().decode(ciphertextB64);
        return new WrappedKeyResult(wrappedBytes, new byte[0], kekKeyId.trim(), version);
    }

    @Override
    public DestroyableKeyMaterial unwrapDek(String tenantId, String kekKeyId, byte[] wrappedDek, byte[] aad) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(kekKeyId, "kekKeyId must not be null");
        Objects.requireNonNull(wrappedDek, "wrappedDek must not be null");

        KmsConnectionContext ctx = contextResolver.resolve(tenantId, PROVIDER_ID, kekKeyId);
        String resourcePath = normalizeResourcePath(kekKeyId);
        String url = ctx.endpointOrDefault(DEFAULT_GCP_KMS_ENDPOINT) + "/v1/" + resourcePath + ":decrypt";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ciphertext", Base64.getEncoder().encodeToString(wrappedDek));
        if (aad != null && aad.length > 0) {
            payload.put("additionalAuthenticatedData", Base64.getEncoder().encodeToString(aad));
        }

        JsonNode root = sendPost(ctx, tenantId, kekKeyId, url, payload);
        String plaintextB64 = root.path("plaintext").asText(null);
        if (plaintextB64 == null || plaintextB64.isBlank()) {
            throw new SpectorMemoryException(
                    ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                    tenantId,
                    kekKeyId,
                    "GCP Cloud KMS decrypt response missing plaintext");
        }

        byte[] raw = Base64.getDecoder().decode(plaintextB64);
        try {
            return new DestroyableKeyMaterial(raw, true);
        } finally {
            Arrays.fill(raw, (byte) 0);
            VarHandle.fullFence();
        }
    }

    @Override
    public KeyHealthStatus checkKeyHealth(String tenantId, String kekKeyId) {
        try {
            KmsConnectionContext ctx = contextResolver.resolve(tenantId, PROVIDER_ID, kekKeyId);
            String resourcePath = normalizeResourcePath(kekKeyId);
            String url = ctx.endpointOrDefault(DEFAULT_GCP_KMS_ENDPOINT) + "/v1/" + resourcePath;

            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET();
            applyBearerHeader(ctx, req);

            HttpResponse<String> response = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 403 || status == 404) {
                return KeyHealthStatus.REVOKED;
            }
            if (status < 200 || status >= 300) {
                return KeyHealthStatus.UNREACHABLE;
            }
            JsonNode root = objectMapper.readTree(response.body());
            String state = root.path("primary").path("state").asText(root.path("state").asText("ENABLED"));
            if ("DESTROYED".equalsIgnoreCase(state) || "DESTROY_SCHEDULED".equalsIgnoreCase(state)) {
                return KeyHealthStatus.REVOKED;
            }
            if ("DISABLED".equalsIgnoreCase(state)) {
                return KeyHealthStatus.DISABLED;
            }
            return KeyHealthStatus.HEALTHY;
        } catch (Exception e) {
            log.warn("[GcpKmsKeyProvider] checkKeyHealth failed for tenant '{}' key '{}': {}",
                    tenantId, kekKeyId, e.getMessage());
            return KeyHealthStatus.UNREACHABLE;
        }
    }

    private JsonNode sendPost(
            KmsConnectionContext ctx,
            String tenantId,
            String kekKeyId,
            String url,
            Map<String, Object> payload) {
        final byte[] bodyBytes;
        try {
            bodyBytes = objectMapper.writeValueAsBytes(payload);
        } catch (Exception e) {
            throw new SpectorMemoryException(
                    ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                    e,
                    tenantId,
                    kekKeyId,
                    "Failed to serialize GCP KMS request");
        }

        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
        applyBearerHeader(ctx, req);

        final HttpResponse<String> response;
        try {
            response = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new SpectorMemoryException(
                    ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                    e,
                    tenantId,
                    kekKeyId,
                    "GCP Cloud KMS request failed: " + e.getMessage());
        }

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            try {
                return objectMapper.readTree(response.body());
            } catch (Exception e) {
                throw new SpectorMemoryException(
                        ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                        e,
                        tenantId,
                        kekKeyId,
                        "Malformed JSON from GCP Cloud KMS");
            }
        }

        String body = response.body() != null ? response.body() : "";
        String lower = body.toLowerCase();
        if (lower.contains("decryption failed") || lower.contains("additional authenticated data") || lower.contains("ciphertext is malformed")) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_AUTH_TAG_MISMATCH, "GCP Cloud KMS decryption/AAD verification failed");
        }
        if (status == 400 || status == 403 || status == 404) {
            if (lower.contains("disabled")
                    || lower.contains("destroyed")
                    || lower.contains("failed_precondition")
                    || lower.contains("permission_denied")
                    || status == 403) {
                throw new SpectorMemoryException(ErrorCode.BYOK_KEY_REVOKED, kekKeyId, tenantId);
            }
        }

        throw new SpectorMemoryException(
                ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                tenantId,
                kekKeyId,
                "GCP Cloud KMS HTTP " + status + ": " + body);
    }

    private static void applyBearerHeader(KmsConnectionContext ctx, HttpRequest.Builder req) {
        if (ctx.secret() != null && !ctx.secret().isBlank()) {
            String token = ctx.secret().trim();
            if (!token.toLowerCase().startsWith("bearer ")) {
                token = "Bearer " + token;
            }
            req.header("Authorization", token);
        }
    }

    private static String normalizeResourcePath(String kekKeyId) {
        String trimmed = kekKeyId.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.startsWith("v1/")) {
            trimmed = trimmed.substring(3);
        }
        return trimmed;
    }

    private static String extractCryptoKeyVersion(String resourceName) {
        int idx = resourceName.indexOf("/cryptoKeyVersions/");
        if (idx >= 0) {
            return "v" + resourceName.substring(idx + "/cryptoKeyVersions/".length());
        }
        return "v1";
    }
}
