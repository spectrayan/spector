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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Azure Key Vault {@link KeyProvider} ({@code azure-kv}) implementing the Azure Key Vault Keys
 * REST API v7.4 ({@code /keys/{name}/{version}/wrapkey}, {@code /unwrapkey} with {@code RSA-OAEP-256})
 * and SHA-256 AAD binding verification (ADR-0088 §5.2, Issue #1070).
 */
@Component
public class AzureKeyVaultKeyProvider implements KeyProvider {

    private static final Logger log = LoggerFactory.getLogger(AzureKeyVaultKeyProvider.class);
    public static final String PROVIDER_ID = "azure-kv";
    private static final String API_VERSION = "7.4";
    private static final String WRAP_ALGORITHM = "RSA-OAEP-256";
    private static final int SHA256_BYTES = 32;

    private final KmsContextResolver contextResolver;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public AzureKeyVaultKeyProvider(
            KmsContextResolver contextResolver,
            @Autowired(required = false) ObjectMapper objectMapper) {
        this(
                contextResolver,
                objectMapper != null ? objectMapper : new ObjectMapper(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    public AzureKeyVaultKeyProvider(
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
        String keyBaseUrl = resolveKeyBaseUrl(ctx, kekKeyId);
        String url = keyBaseUrl + "/wrapkey?api-version=" + API_VERSION;

        // Bind AAD digest (SHA-256 over tenantId + AAD) with plaintext DEK inside RSA-OAEP-256 envelope
        byte[] aadDigest = computeAadDigest(tenantId, aad);
        byte[] boundPlaintext = new byte[SHA256_BYTES + plaintextDek.length];
        ByteBuffer.wrap(boundPlaintext).put(aadDigest).put(plaintextDek);

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("alg", WRAP_ALGORITHM);
            payload.put("value", Base64.getUrlEncoder().withoutPadding().encodeToString(boundPlaintext));

            JsonNode root = sendPost(ctx, tenantId, kekKeyId, url, payload);
            String valueB64Url = root.path("value").asText(null);
            if (valueB64Url == null || valueB64Url.isBlank()) {
                throw new SpectorMemoryException(
                        ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                        tenantId,
                        kekKeyId,
                        "Azure Key Vault wrapkey response missing value");
            }

            byte[] wrappedBytes = Base64.getUrlDecoder().decode(valueB64Url);
            String kid = root.path("kid").asText(keyBaseUrl);
            return new WrappedKeyResult(wrappedBytes, new byte[0], kekKeyId.trim(), extractKeyVersion(kid));
        } finally {
            Arrays.fill(boundPlaintext, (byte) 0);
            VarHandle.fullFence();
        }
    }

    @Override
    public DestroyableKeyMaterial unwrapDek(String tenantId, String kekKeyId, byte[] wrappedDek, byte[] aad) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(kekKeyId, "kekKeyId must not be null");
        Objects.requireNonNull(wrappedDek, "wrappedDek must not be null");

        KmsConnectionContext ctx = contextResolver.resolve(tenantId, PROVIDER_ID, kekKeyId);
        String keyBaseUrl = resolveKeyBaseUrl(ctx, kekKeyId);
        String url = keyBaseUrl + "/unwrapkey?api-version=" + API_VERSION;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alg", WRAP_ALGORITHM);
        payload.put("value", Base64.getUrlEncoder().withoutPadding().encodeToString(wrappedDek));

        JsonNode root = sendPost(ctx, tenantId, kekKeyId, url, payload);
        String valueB64Url = root.path("value").asText(null);
        if (valueB64Url == null || valueB64Url.isBlank()) {
            throw new SpectorMemoryException(
                    ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                    tenantId,
                    kekKeyId,
                    "Azure Key Vault unwrapkey response missing value");
        }

        byte[] boundPlaintext = Base64.getUrlDecoder().decode(valueB64Url);
        try {
            if (boundPlaintext.length <= SHA256_BYTES) {
                throw new SpectorMemoryException(
                        ErrorCode.MEMORY_AUTH_TAG_MISMATCH,
                        "Azure Key Vault unwrapped payload shorter than AAD digest");
            }
            byte[] actualDigest = Arrays.copyOfRange(boundPlaintext, 0, SHA256_BYTES);
            byte[] expectedDigest = computeAadDigest(tenantId, aad);
            if (!MessageDigest.isEqual(actualDigest, expectedDigest)) {
                throw new SpectorMemoryException(
                        ErrorCode.MEMORY_AUTH_TAG_MISMATCH,
                        "Azure Key Vault AAD digest verification failed");
            }
            byte[] rawDek = Arrays.copyOfRange(boundPlaintext, SHA256_BYTES, boundPlaintext.length);
            try {
                return new DestroyableKeyMaterial(rawDek, true);
            } finally {
                Arrays.fill(rawDek, (byte) 0);
            }
        } finally {
            Arrays.fill(boundPlaintext, (byte) 0);
            VarHandle.fullFence();
        }
    }

    @Override
    public KeyHealthStatus checkKeyHealth(String tenantId, String kekKeyId) {
        try {
            KmsConnectionContext ctx = contextResolver.resolve(tenantId, PROVIDER_ID, kekKeyId);
            String keyBaseUrl = resolveKeyBaseUrl(ctx, kekKeyId);
            String url = keyBaseUrl + "?api-version=" + API_VERSION;

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
            boolean enabled = root.path("attributes").path("enabled").asBoolean(true);
            return enabled ? KeyHealthStatus.HEALTHY : KeyHealthStatus.DISABLED;
        } catch (Exception e) {
            log.warn("[AzureKeyVaultKeyProvider] checkKeyHealth failed for tenant '{}' key '{}': {}",
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
                    "Failed to serialize Azure Key Vault request");
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
                    "Azure Key Vault request failed: " + e.getMessage());
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
                        "Malformed JSON from Azure Key Vault");
            }
        }

        String body = response.body() != null ? response.body() : "";
        String lower = body.toLowerCase();
        if (status == 403 || status == 404 || lower.contains("keydisabled") || lower.contains("forbidden") || lower.contains("keynotfound")) {
            throw new SpectorMemoryException(ErrorCode.BYOK_KEY_REVOKED, kekKeyId, tenantId);
        }

        throw new SpectorMemoryException(
                ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                tenantId,
                kekKeyId,
                "Azure Key Vault HTTP " + status + ": " + body);
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

    private static String resolveKeyBaseUrl(KmsConnectionContext ctx, String kekKeyId) {
        String trimmed = kekKeyId.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        String endpoint = ctx.endpointOrDefault(null);
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            if (endpoint != null && !endpoint.isBlank()) {
                URI keyUri = URI.create(trimmed);
                return endpoint + keyUri.getPath();
            }
            return trimmed;
        }
        String base = endpoint != null ? endpoint : "https://spector-vault.vault.azure.net";
        if (trimmed.startsWith("keys/")) {
            return base + "/" + trimmed;
        }
        return base + "/keys/" + trimmed;
    }

    private static byte[] computeAadDigest(String tenantId, byte[] aad) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.update(tenantId.trim().getBytes(StandardCharsets.UTF_8));
            sha256.update((byte) ':');
            if (aad != null && aad.length > 0) {
                sha256.update(aad);
            }
            return sha256.digest();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String extractKeyVersion(String kid) {
        if (kid == null || kid.isBlank()) {
            return "v1";
        }
        int keysIdx = kid.indexOf("/keys/");
        if (keysIdx >= 0) {
            String afterKeys = kid.substring(keysIdx + 6);
            int slash = afterKeys.indexOf('/');
            if (slash > 0 && slash + 1 < afterKeys.length()) {
                return afterKeys.substring(slash + 1);
            }
        }
        return "v1";
    }
}
