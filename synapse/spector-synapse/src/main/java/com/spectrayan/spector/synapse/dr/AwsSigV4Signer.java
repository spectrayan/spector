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
package com.spectrayan.spector.synapse.dr;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Standard AWS Signature Version 4 HMAC-SHA256 request signer for S3-compatible endpoints
 * (ADR-0034 §11.2, §14, Req R1.2, R1.3).
 */
public final class AwsSigV4Signer {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private AwsSigV4Signer() {}

    /**
     * Signs an HTTP request and returns the map of headers to add (including {@code Authorization},
     * {@code x-amz-date}, and {@code x-amz-content-sha256}).
     */
    public static Map<String, String> sign(
            String httpMethod,
            URI uri,
            Map<String, String> headers,
            byte[] payload,
            String accessKey,
            String secretKey,
            String region,
            String service
    ) {
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            return Map.of(); // Unauthenticated / anonymous mode
        }

        Instant now = Instant.now();
        String amzDate = DATE_TIME_FORMATTER.format(now);
        String dateStamp = DATE_FORMATTER.format(now);

        String payloadHash = sha256Hex(payload != null ? payload : new byte[0]);

        Map<String, String> normalizedHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            normalizedHeaders.putAll(headers);
        }
        normalizedHeaders.put("x-amz-date", amzDate);
        normalizedHeaders.put("x-amz-content-sha256", payloadHash);
        if (!normalizedHeaders.containsKey("host")) {
            String host = uri.getHost();
            if (uri.getPort() > 0 && uri.getPort() != 80 && uri.getPort() != 443) {
                host += ":" + uri.getPort();
            }
            normalizedHeaders.put("host", host);
        }

        // Canonical headers and signed headers list
        StringBuilder canonicalHeaders = new StringBuilder();
        StringBuilder signedHeaders = new StringBuilder();
        for (Map.Entry<String, String> entry : normalizedHeaders.entrySet()) {
            String lowerName = entry.getKey().toLowerCase(Locale.ROOT);
            if (signedHeaders.length() > 0) {
                signedHeaders.append(";");
            }
            signedHeaders.append(lowerName);
            canonicalHeaders.append(lowerName).append(":").append(entry.getValue().trim()).append("\n");
        }

        // Canonical query string
        String canonicalQuery = buildCanonicalQuery(uri.getRawQuery());

        // Canonical URI
        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }

        // Canonical Request
        String canonicalRequest = httpMethod.toUpperCase(Locale.ROOT) + "\n"
                + path + "\n"
                + canonicalQuery + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + payloadHash;

        // String to Sign
        String credentialScope = dateStamp + "/" + region + "/" + service + "/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n"
                + amzDate + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

        // Calculate signature
        byte[] signingKey = getSignatureKey(secretKey, dateStamp, region, service);
        String signature = HexFormat.of().formatHex(hmacSha256(stringToSign.getBytes(StandardCharsets.UTF_8), signingKey));

        String authHeader = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;

        Map<String, String> resultHeaders = new HashMap<>();
        resultHeaders.put("Authorization", authHeader);
        resultHeaders.put("x-amz-date", amzDate);
        resultHeaders.put("x-amz-content-sha256", payloadHash);
        return resultHeaders;
    }

    private static String buildCanonicalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        String[] pairs = rawQuery.split("&");
        Arrays.sort(pairs);
        StringBuilder sb = new StringBuilder();
        for (String pair : pairs) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String k = pair.substring(0, idx);
                String v = pair.substring(idx + 1);
                sb.append(k).append("=").append(v);
            } else {
                sb.append(pair).append("=");
            }
        }
        return sb.toString();
    }

    private static byte[] getSignatureKey(String key, String dateStamp, String regionName, String serviceName) {
        byte[] kSecret = ("AWS4" + key).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(dateStamp.getBytes(StandardCharsets.UTF_8), kSecret);
        byte[] kRegion = hmacSha256(regionName.getBytes(StandardCharsets.UTF_8), kDate);
        byte[] kService = hmacSha256(serviceName.getBytes(StandardCharsets.UTF_8), kRegion);
        return hmacSha256("aws4_request".getBytes(StandardCharsets.UTF_8), kService);
    }

    private static byte[] hmacSha256(byte[] data, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
    }

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
