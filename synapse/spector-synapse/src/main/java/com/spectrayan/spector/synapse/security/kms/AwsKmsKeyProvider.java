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
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * AWS Key Management Service (AWS KMS) {@link KeyProvider} ({@code aws-kms}) implementing
 * the AWS KMS JSON 1.1 wire protocol ({@code TrentService.Encrypt}, {@code TrentService.Decrypt},
 * {@code TrentService.DescribeKey}) with AWS Signature Version 4 ({@code AWS4-HMAC-SHA256})
 * request signing and tenant-bound {@code EncryptionContext} (ADR-0088 §5.2, Issue #1070).
 */
@Component
public class AwsKmsKeyProvider implements KeyProvider {

    private static final Logger log = LoggerFactory.getLogger(AwsKmsKeyProvider.class);
    public static final String PROVIDER_ID = "aws-kms";

    private static final String CONTENT_TYPE = "application/x-amz-json-1.1";
    private static final String SERVICE_NAME = "kms";
    private static final DateTimeFormatter AMZ_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final KmsContextResolver contextResolver;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Clock clock;

    @Autowired
    public AwsKmsKeyProvider(
            KmsContextResolver contextResolver,
            @Autowired(required = false) ObjectMapper objectMapper) {
        this(
                contextResolver,
                objectMapper != null ? objectMapper : new ObjectMapper(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                Clock.systemUTC());
    }

    public AwsKmsKeyProvider(
            KmsContextResolver contextResolver,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            Clock clock) {
        this.contextResolver = Objects.requireNonNull(contextResolver, "contextResolver must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("KeyId", kekKeyId.trim());
        body.put("Plaintext", Base64.getEncoder().encodeToString(plaintextDek));
        body.put("EncryptionContext", buildEncryptionContext(tenantId, aad));

        JsonNode response = invokeAwsKms(ctx, tenantId, kekKeyId, "TrentService.Encrypt", body);
        String ciphertextB64 = response.path("CiphertextBlob").asText(null);
        if (ciphertextB64 == null || ciphertextB64.isBlank()) {
            throw new SpectorMemoryException(
                    ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                    tenantId,
                    kekKeyId,
                    "AWS KMS Encrypt response missing CiphertextBlob");
        }
        byte[] wrappedBytes = Base64.getDecoder().decode(ciphertextB64);
        String resolvedKeyId = response.path("KeyId").asText(kekKeyId.trim());
        return new WrappedKeyResult(wrappedBytes, new byte[0], resolvedKeyId, "v1");
    }

    @Override
    public DestroyableKeyMaterial unwrapDek(String tenantId, String kekKeyId, byte[] wrappedDek, byte[] aad) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(kekKeyId, "kekKeyId must not be null");
        Objects.requireNonNull(wrappedDek, "wrappedDek must not be null");

        KmsConnectionContext ctx = contextResolver.resolve(tenantId, PROVIDER_ID, kekKeyId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("KeyId", kekKeyId.trim());
        body.put("CiphertextBlob", Base64.getEncoder().encodeToString(wrappedDek));
        body.put("EncryptionContext", buildEncryptionContext(tenantId, aad));

        JsonNode response = invokeAwsKms(ctx, tenantId, kekKeyId, "TrentService.Decrypt", body);
        String plaintextB64 = response.path("Plaintext").asText(null);
        if (plaintextB64 == null || plaintextB64.isBlank()) {
            throw new SpectorMemoryException(
                    ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                    tenantId,
                    kekKeyId,
                    "AWS KMS Decrypt response missing Plaintext");
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
            Map<String, Object> body = Map.of("KeyId", kekKeyId.trim());
            JsonNode response = invokeAwsKms(ctx, tenantId, kekKeyId, "TrentService.DescribeKey", body);
            JsonNode meta = response.path("KeyMetadata");
            String keyState = meta.path("KeyState").asText("Enabled");
            boolean enabled = meta.path("Enabled").asBoolean(true);
            if ("PendingDeletion".equalsIgnoreCase(keyState) || "Unavailable".equalsIgnoreCase(keyState)) {
                return KeyHealthStatus.REVOKED;
            }
            if ("Disabled".equalsIgnoreCase(keyState) || !enabled) {
                return KeyHealthStatus.DISABLED;
            }
            return KeyHealthStatus.HEALTHY;
        } catch (SpectorMemoryException sme) {
            if (sme.errorCode() == ErrorCode.BYOK_KEY_REVOKED) {
                return KeyHealthStatus.REVOKED;
            }
            return KeyHealthStatus.UNREACHABLE;
        } catch (Exception e) {
            log.warn("[AwsKmsKeyProvider] checkKeyHealth failed for tenant '{}' key '{}': {}",
                    tenantId, kekKeyId, e.getMessage());
            return KeyHealthStatus.UNREACHABLE;
        }
    }

    private Map<String, String> buildEncryptionContext(String tenantId, byte[] aad) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put("tenant_id", tenantId.trim());
        if (aad != null && aad.length > 0) {
            context.put("aad", Base64.getEncoder().encodeToString(aad));
        }
        return context;
    }

    private JsonNode invokeAwsKms(
            KmsConnectionContext ctx,
            String tenantId,
            String kekKeyId,
            String amzTarget,
            Map<String, Object> payload) {
        String region = resolveRegion(ctx, kekKeyId);
        String endpointUrl = ctx.endpointOrDefault("https://kms." + region + ".amazonaws.com");
        URI uri = URI.create(endpointUrl.endsWith("/") ? endpointUrl : endpointUrl + "/");

        final byte[] bodyBytes;
        try {
            bodyBytes = objectMapper.writeValueAsBytes(payload);
        } catch (Exception e) {
            throw new SpectorMemoryException(
                    ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                    e,
                    tenantId,
                    kekKeyId,
                    "Failed to serialize AWS KMS request");
        }

        Instant now = clock.instant();
        String amzDate = AMZ_DATE_FORMAT.format(now);
        String dateStamp = DATE_STAMP_FORMAT.format(now);
        String host = uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
        String payloadHash = sha256Hex(bodyBytes);
        String sessionToken = ctx.awsSessionToken();

        String canonicalHeaders = "content-type:" + CONTENT_TYPE + "\n"
                + "host:" + host + "\n"
                + "x-amz-date:" + amzDate + "\n"
                + (sessionToken != null ? "x-amz-security-token:" + sessionToken + "\n" : "")
                + "x-amz-target:" + amzTarget + "\n";
        String signedHeaders = "content-type;host;x-amz-date;"
                + (sessionToken != null ? "x-amz-security-token;" : "")
                + "x-amz-target";

        String canonicalUri = (uri.getPath() == null || uri.getPath().isEmpty()) ? "/" : uri.getPath();
        String canonicalRequest = "POST\n"
                + canonicalUri + "\n"
                + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + payloadHash;

        String credentialScope = dateStamp + "/" + region + "/" + SERVICE_NAME + "/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n"
                + amzDate + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

        byte[] signingKey = getSignatureKey(ctx.awsSecretAccessKey(), dateStamp, region, SERVICE_NAME);
        String signature = HexFormat.of().formatHex(hmacSha256(signingKey, stringToSign));
        Arrays.fill(signingKey, (byte) 0);

        String authorizationHeader = "AWS4-HMAC-SHA256 Credential=" + ctx.awsAccessKeyId() + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", CONTENT_TYPE)
                .header("X-Amz-Date", amzDate)
                .header("X-Amz-Target", amzTarget)
                .header("Authorization", authorizationHeader)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
        if (sessionToken != null) {
            reqBuilder.header("X-Amz-Security-Token", sessionToken);
        }

        final HttpResponse<String> response;
        try {
            response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new SpectorMemoryException(
                    ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                    e,
                    tenantId,
                    kekKeyId,
                    "AWS KMS request failed: " + e.getMessage());
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
                        "Malformed JSON from AWS KMS");
            }
        }

        String errorType = "";
        String errorMessage = response.body();
        try {
            JsonNode errNode = objectMapper.readTree(response.body());
            errorType = errNode.path("__type").asText("");
            errorMessage = errNode.path("message").asText(errNode.path("Message").asText(response.body()));
        } catch (Exception ignored) {
        }

        if (status == 400 || status == 403) {
            String upperType = errorType.toUpperCase();
            if (upperType.contains("DISABLED")
                    || upperType.contains("INVALIDSTATE")
                    || upperType.contains("ACCESSDENIED")
                    || upperType.contains("NOTFOUND")
                    || upperType.contains("INCORRECTKEY")) {
                throw new SpectorMemoryException(ErrorCode.BYOK_KEY_REVOKED, kekKeyId, tenantId);
            }
            if (upperType.contains("INVALIDCIPHERTEXT")) {
                throw new SpectorMemoryException(ErrorCode.MEMORY_AUTH_TAG_MISMATCH, "AWS KMS InvalidCiphertextException");
            }
        }

        throw new SpectorMemoryException(
                ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                tenantId,
                kekKeyId,
                "HTTP " + status + " (" + errorType + "): " + errorMessage);
    }

    static String resolveRegion(KmsConnectionContext ctx, String kekKeyId) {
        String explicit = ctx.regionOrDefault(null);
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        // Extract region from ARN: arn:aws:kms:<region>:<account>:key/<id>
        if (kekKeyId != null && kekKeyId.startsWith("arn:aws")) {
            String[] parts = kekKeyId.split(":");
            if (parts.length >= 4 && !parts[3].isBlank()) {
                return parts[3].trim();
            }
        }
        return "us-east-1";
    }

    private static String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static byte[] getSignatureKey(String key, String dateStamp, String regionName, String serviceName) {
        byte[] kSecret = ("AWS4" + (key != null ? key : "")).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, dateStamp);
        byte[] kRegion = hmacSha256(kDate, regionName);
        byte[] kService = hmacSha256(kRegion, serviceName);
        byte[] kSigning = hmacSha256(kService, "aws4_request");
        Arrays.fill(kSecret, (byte) 0);
        Arrays.fill(kDate, (byte) 0);
        Arrays.fill(kRegion, (byte) 0);
        Arrays.fill(kService, (byte) 0);
        return kSigning;
    }
}
