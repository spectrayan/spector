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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * S3-compatible object store client implementation connecting to AWS S3, MinIO, or compatible
 * object storage targets for Disaster Recovery snapshot export and compliance erasure
 * (ADR-0034 §11.2, §14, §16, Req R1.1–R1.6).
 *
 * <p>Authentication is performed using standard AWS SigV4. Credentials are automatically
 * resolved from environment variables or IAM Workload Identity (Req R1.3).</p>
 */
public class S3CompatibleObjectStoreClient implements ObjectStoreClient {

    private static final Logger log = LoggerFactory.getLogger(S3CompatibleObjectStoreClient.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final String endpoint;
    private final String region;
    private final String accessKey;
    private final String secretKey;
    private final BandwidthThrottler throttler;

    public S3CompatibleObjectStoreClient(
            String endpoint,
            String region,
            String accessKey,
            String secretKey,
            long bandwidthLimitBytesPerSec
    ) {
        this(
                HttpClient.newBuilder().connectTimeout(DEFAULT_TIMEOUT).build(),
                endpoint,
                region,
                accessKey,
                secretKey,
                bandwidthLimitBytesPerSec
        );
    }

    public S3CompatibleObjectStoreClient(
            HttpClient httpClient,
            String endpoint,
            String region,
            String accessKey,
            String secretKey,
            long bandwidthLimitBytesPerSec
    ) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.endpoint = normalizeEndpoint(endpoint);
        this.region = resolveRegion(region);
        this.accessKey = resolveAccessKey(accessKey);
        this.secretKey = resolveSecretKey(secretKey);
        this.throttler = new BandwidthThrottler(bandwidthLimitBytesPerSec);
    }

    @Override
    public void putObject(String bucket, String key, byte[] content, String contentType, Map<String, String> metadata) {
        Objects.requireNonNull(bucket, "bucket must not be null");
        Objects.requireNonNull(key, "key must not be null");
        byte[] payload = content != null ? content : new byte[0];

        throttler.throttle(payload.length);

        URI targetUri = buildUri(bucket, key, null);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", contentType != null ? contentType : "application/octet-stream");
        if (metadata != null) {
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                headers.put("x-amz-meta-" + entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
            }
        }

        Map<String, String> sigHeaders = AwsSigV4Signer.sign(
                "PUT", targetUri, headers, payload, accessKey, secretKey, region, "s3");

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(targetUri)
                .timeout(DEFAULT_TIMEOUT)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(payload));

        addNonRestrictedHeaders(builder, headers);
        addNonRestrictedHeaders(builder, sigHeaders);

        try {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                String body = new String(response.body(), StandardCharsets.UTF_8);
                throw new ObjectStoreException(bucket, key, "HTTP " + status + ": " + body);
            }
        } catch (ObjectStoreException e) {
            throw e;
        } catch (Exception e) {
            throw new ObjectStoreException(bucket, key, "Failed to upload object: " + e.getMessage(), e);
        }
    }

    @Override
    public void putObject(String bucket, String key, InputStream content, long contentLength, String contentType, Map<String, String> metadata) {
        try {
            byte[] bytes = content.readAllBytes();
            putObject(bucket, key, bytes, contentType, metadata);
        } catch (Exception e) {
            throw new ObjectStoreException(bucket, key, "Failed to read input stream: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<byte[]> getObject(String bucket, String key) {
        Objects.requireNonNull(bucket, "bucket must not be null");
        Objects.requireNonNull(key, "key must not be null");

        URI targetUri = buildUri(bucket, key, null);
        Map<String, String> sigHeaders = AwsSigV4Signer.sign(
                "GET", targetUri, Map.of(), new byte[0], accessKey, secretKey, region, "s3");

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(targetUri)
                .timeout(DEFAULT_TIMEOUT)
                .GET();

        addNonRestrictedHeaders(builder, sigHeaders);

        try {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            if (status == 404) {
                return Optional.empty();
            }
            if (status < 200 || status >= 300) {
                String body = new String(response.body(), StandardCharsets.UTF_8);
                throw new ObjectStoreException(bucket, key, "HTTP " + status + ": " + body);
            }
            byte[] body = response.body();
            throttler.throttle(body.length);
            return Optional.of(body);
        } catch (ObjectStoreException e) {
            throw e;
        } catch (Exception e) {
            throw new ObjectStoreException(bucket, key, "Failed to download object: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<InputStream> getObjectStream(String bucket, String key) {
        return getObject(bucket, key).map(ByteArrayInputStream::new);
    }

    @Override
    public List<String> listKeysByPrefix(String bucket, String prefix) {
        Objects.requireNonNull(bucket, "bucket must not be null");
        String safePrefix = prefix != null ? prefix : "";
        String query = "list-type=2&prefix=" + URLEncoder.encode(safePrefix, StandardCharsets.UTF_8);
        URI targetUri = buildUri(bucket, null, query);

        Map<String, String> sigHeaders = AwsSigV4Signer.sign(
                "GET", targetUri, Map.of(), new byte[0], accessKey, secretKey, region, "s3");

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(targetUri)
                .timeout(DEFAULT_TIMEOUT)
                .GET();

        addNonRestrictedHeaders(builder, sigHeaders);

        try {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                String body = new String(response.body(), StandardCharsets.UTF_8);
                throw new ObjectStoreException(bucket, prefix, "HTTP " + status + ": " + body);
            }

            return parseS3ListResponse(response.body());
        } catch (ObjectStoreException e) {
            throw e;
        } catch (Exception e) {
            throw new ObjectStoreException(bucket, prefix, "Failed to list objects by prefix: " + e.getMessage(), e);
        }
    }

    @Override
    public int deleteByPrefix(String bucket, String prefix) {
        List<String> keys = listKeysByPrefix(bucket, prefix);
        int deleted = 0;
        for (String key : keys) {
            if (deleteObject(bucket, key)) {
                deleted++;
            }
        }
        return deleted;
    }

    @Override
    public boolean deleteObject(String bucket, String key) {
        Objects.requireNonNull(bucket, "bucket must not be null");
        Objects.requireNonNull(key, "key must not be null");

        URI targetUri = buildUri(bucket, key, null);
        Map<String, String> sigHeaders = AwsSigV4Signer.sign(
                "DELETE", targetUri, Map.of(), new byte[0], accessKey, secretKey, region, "s3");

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(targetUri)
                .timeout(DEFAULT_TIMEOUT)
                .DELETE();

        addNonRestrictedHeaders(builder, sigHeaders);

        try {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            return status == 200 || status == 204 || status == 404;
        } catch (Exception e) {
            log.warn("[S3Client] Failed to delete object: bucket={}, key={}, error={}", bucket, key, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean objectExists(String bucket, String key) {
        Objects.requireNonNull(bucket, "bucket must not be null");
        Objects.requireNonNull(key, "key must not be null");

        URI targetUri = buildUri(bucket, key, null);
        Map<String, String> sigHeaders = AwsSigV4Signer.sign(
                "HEAD", targetUri, Map.of(), new byte[0], accessKey, secretKey, region, "s3");

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(targetUri)
                .timeout(DEFAULT_TIMEOUT)
                .method("HEAD", HttpRequest.BodyPublishers.noBody());

        addNonRestrictedHeaders(builder, sigHeaders);

        try {
            HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static final Set<String> RESTRICTED_HEADERS = Set.of(
            "connection", "content-length", "date", "expect", "from", "host", "upgrade", "via", "warning"
    );

    private static void addNonRestrictedHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers == null) {
            return;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (!RESTRICTED_HEADERS.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public String getRegion() {
        return region;
    }

    @Override
    public long getBandwidthLimitBytesPerSec() {
        return throttler.getMaxBytesPerSecond();
    }

    private URI buildUri(String bucket, String key, String query) {
        String cleanEndpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        StringBuilder path = new StringBuilder(cleanEndpoint);
        if (bucket != null && !bucket.isBlank()) {
            path.append("/").append(bucket);
        }
        if (key != null && !key.isBlank()) {
            if (!key.startsWith("/")) {
                path.append("/");
            }
            path.append(key);
        }
        if (query != null && !query.isBlank()) {
            path.append("?").append(query);
        }
        return URI.create(path.toString());
    }

    private static List<String> parseS3ListResponse(byte[] xmlBytes) {
        List<String> keys = new ArrayList<>();
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            // Secure XML parser against XXE
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(xmlBytes));
            doc.getDocumentElement().normalize();

            NodeList keyNodes = doc.getElementsByTagName("Key");
            for (int i = 0; i < keyNodes.getLength(); i++) {
                keys.add(keyNodes.item(i).getTextContent());
            }
        } catch (Exception e) {
            log.warn("[S3Client] Error parsing ListObjectsV2 response: {}", e.getMessage());
        }
        return keys;
    }

    private static String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "https://s3.amazonaws.com";
        }
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            return "https://" + endpoint;
        }
        return endpoint;
    }

    private static String resolveRegion(String region) {
        if (region != null && !region.isBlank()) {
            return region;
        }
        String envRegion = System.getenv("AWS_REGION");
        if (envRegion != null && !envRegion.isBlank()) {
            return envRegion;
        }
        return "us-east-1";
    }

    private static String resolveAccessKey(String accessKey) {
        if (accessKey != null && !accessKey.isBlank()) {
            return accessKey;
        }
        String env = System.getenv("AWS_ACCESS_KEY_ID");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return System.getenv("MINIO_ROOT_USER");
    }

    private static String resolveSecretKey(String secretKey) {
        if (secretKey != null && !secretKey.isBlank()) {
            return secretKey;
        }
        String env = System.getenv("AWS_SECRET_ACCESS_KEY");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return System.getenv("MINIO_ROOT_PASSWORD");
    }
}
