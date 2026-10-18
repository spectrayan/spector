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
 * HashiCorp Vault Transit Engine {@link KeyProvider} ({@code hashicorp-vault}) implementing
 * {@code /v1/{mount}/encrypt/{key}}, {@code /v1/{mount}/decrypt/{key}}, and
 * {@code /v1/{mount}/keys/{key}} with context-derived AAD binding (ADR-0088 §5.2, Issue #1070).
 */
@Component
public class VaultTransitKeyProvider implements KeyProvider {

    private static final Logger log = LoggerFactory.getLogger(VaultTransitKeyProvider.class);
    public static final String PROVIDER_ID = "hashicorp-vault";
    private static final String DEFAULT_VAULT_ENDPOINT = "http://127.0.0.1:8200";
    private static final String DEFAULT_TRANSIT_MOUNT = "transit";

    private final KmsContextResolver contextResolver;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public VaultTransitKeyProvider(
            KmsContextResolver contextResolver,
            @Autowired(required = false) ObjectMapper objectMapper) {
        this(
                contextResolver,
                objectMapper != null ? objectMapper : new ObjectMapper(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    public VaultTransitKeyProvider(
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
        String[] mountAndKey = resolveMountAndKey(ctx, kekKeyId);
        String url = ctx.endpointOrDefault(DEFAULT_VAULT_ENDPOINT)
                + "/v1/" + mountAndKey[0] + "/encrypt/" + mountAndKey[1];

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("plaintext", Base64.getEncoder().encodeToString(plaintextDek));
        if (aad != null && aad.length > 0) {
            payload.put("context", Base64.getEncoder().encodeToString(aad));
        }

        JsonNode root = sendPost(ctx, tenantId, kekKeyId, url, payload);
        String ciphertext = root.path("data").path("ciphertext").asText(null);
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new SpectorMemoryException(
                    ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                    tenantId,
                    kekKeyId,
                    "Vault Transit encrypt response missing data.ciphertext");
        }

        String version = extractVaultVersion(ciphertext, root.path("data").path("key_version").asInt(1));
        return new WrappedKeyResult(
                ciphertext.getBytes(StandardCharsets.UTF_8),
                new byte[0],
                kekKeyId.trim(),
                version);
    }

    @Override
    public DestroyableKeyMaterial unwrapDek(String tenantId, String kekKeyId, byte[] wrappedDek, byte[] aad) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(kekKeyId, "kekKeyId must not be null");
        Objects.requireNonNull(wrappedDek, "wrappedDek must not be null");

        KmsConnectionContext ctx = contextResolver.resolve(tenantId, PROVIDER_ID, kekKeyId);
        String[] mountAndKey = resolveMountAndKey(ctx, kekKeyId);
        String url = ctx.endpointOrDefault(DEFAULT_VAULT_ENDPOINT)
                + "/v1/" + mountAndKey[0] + "/decrypt/" + mountAndKey[1];

        String ciphertext = new String(wrappedDek, StandardCharsets.UTF_8);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ciphertext", ciphertext);
        if (aad != null && aad.length > 0) {
            payload.put("context", Base64.getEncoder().encodeToString(aad));
        }

        JsonNode root = sendPost(ctx, tenantId, kekKeyId, url, payload);
        String plaintextB64 = root.path("data").path("plaintext").asText(null);
        if (plaintextB64 == null || plaintextB64.isBlank()) {
            throw new SpectorMemoryException(
                    ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                    tenantId,
                    kekKeyId,
                    "Vault Transit decrypt response missing data.plaintext");
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
            String[] mountAndKey = resolveMountAndKey(ctx, kekKeyId);
            String url = ctx.endpointOrDefault(DEFAULT_VAULT_ENDPOINT)
                    + "/v1/" + mountAndKey[0] + "/keys/" + mountAndKey[1];

            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET();
            applyVaultHeaders(ctx, req);

            HttpResponse<String> response = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 403 || status == 404) {
                return KeyHealthStatus.REVOKED;
            }
            if (status < 200 || status >= 300) {
                return KeyHealthStatus.UNREACHABLE;
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            if (data.path("supports_encryption").asBoolean(true) && data.path("supports_decryption").asBoolean(true)) {
                return KeyHealthStatus.HEALTHY;
            }
            return KeyHealthStatus.DISABLED;
        } catch (Exception e) {
            log.warn("[VaultTransitKeyProvider] checkKeyHealth failed for tenant '{}' key '{}': {}",
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
                    "Failed to serialize Vault Transit request");
        }

        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
        applyVaultHeaders(ctx, req);

        final HttpResponse<String> response;
        try {
            response = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new SpectorMemoryException(
                    ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                    e,
                    tenantId,
                    kekKeyId,
                    "Vault Transit request failed: " + e.getMessage());
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
                        "Malformed JSON from Vault Transit");
            }
        }

        String body = response.body() != null ? response.body() : "";
        String lower = body.toLowerCase();
        if (lower.contains("message authentication failed")
                || lower.contains("unable to decrypt")
                || lower.contains("invalid ciphertext")) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_AUTH_TAG_MISMATCH, "Vault Transit authentication failed");
        }
        if (status == 403 || status == 404 || lower.contains("permission denied") || lower.contains("key not found") || lower.contains("disabled")) {
            throw new SpectorMemoryException(ErrorCode.BYOK_KEY_REVOKED, kekKeyId, tenantId);
        }

        throw new SpectorMemoryException(
                ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                tenantId,
                kekKeyId,
                "Vault Transit HTTP " + status + ": " + body);
    }

    private static void applyVaultHeaders(KmsConnectionContext ctx, HttpRequest.Builder req) {
        if (ctx.secret() != null && !ctx.secret().isBlank()) {
            req.header("X-Vault-Token", ctx.secret().trim());
        }
        String vaultNs = ctx.vaultNamespace();
        if (vaultNs != null) {
            req.header("X-Vault-Namespace", vaultNs);
        }
    }

    private static String[] resolveMountAndKey(KmsConnectionContext ctx, String kekKeyId) {
        String trimmed = kekKeyId.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.startsWith("v1/")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.contains("/keys/")) {
            int idx = trimmed.indexOf("/keys/");
            return new String[]{trimmed.substring(0, idx), trimmed.substring(idx + 6)};
        }
        if (trimmed.contains("/")) {
            int slash = trimmed.indexOf('/');
            return new String[]{trimmed.substring(0, slash), trimmed.substring(slash + 1)};
        }
        return new String[]{ctx.mountPathOrDefault(DEFAULT_TRANSIT_MOUNT), trimmed};
    }

    private static String extractVaultVersion(String ciphertext, int fallbackVersion) {
        // Format: vault:v1:base64...
        if (ciphertext.startsWith("vault:")) {
            int secondColon = ciphertext.indexOf(':', 6);
            if (secondColon > 6) {
                return ciphertext.substring(6, secondColon);
            }
        }
        return "v" + fallbackVersion;
    }
}
