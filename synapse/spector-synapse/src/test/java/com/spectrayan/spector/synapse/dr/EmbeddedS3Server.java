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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lightweight in-memory S3-compatible mock HTTP server for unit and integration testing
 * of DR exports, restores, and compliance erasures without requiring external MinIO or AWS accounts
 * (ADR-0034 §11.2, Req R1.2, Task 0.7).
 */
public class EmbeddedS3Server implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, Map<String, byte[]>> buckets = new ConcurrentHashMap<>();
    private final AtomicBoolean unavailable = new AtomicBoolean(false);

    public EmbeddedS3Server() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/", new S3Handler());
        this.server.setExecutor(null);
        this.server.start();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public String getEndpoint() {
        return "http://127.0.0.1:" + getPort();
    }

    public void setUnavailable(boolean state) {
        this.unavailable.set(state);
    }

    public void createBucket(String bucket) {
        buckets.computeIfAbsent(bucket, b -> new ConcurrentHashMap<>());
    }

    public Map<String, byte[]> getBucketObjects(String bucket) {
        return buckets.getOrDefault(bucket, Map.of());
    }

    public void clear() {
        buckets.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private class S3Handler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (unavailable.get()) {
                byte[] resp = "Service Unavailable".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(503, resp.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp);
                }
                return;
            }

            String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
            String rawPath = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getRawQuery();

            if (rawPath.startsWith("/")) {
                rawPath = rawPath.substring(1);
            }

            int firstSlash = rawPath.indexOf('/');
            String bucket = firstSlash > 0 ? rawPath.substring(0, firstSlash) : rawPath;
            String key = firstSlash > 0 ? rawPath.substring(firstSlash + 1) : "";

            Map<String, byte[]> bucketMap = buckets.computeIfAbsent(bucket, b -> new ConcurrentHashMap<>());

            if ("PUT".equals(method)) {
                if (key.isEmpty()) {
                    // Create bucket
                    exchange.sendResponseHeaders(200, -1);
                    return;
                }
                try (InputStream is = exchange.getRequestBody()) {
                    byte[] data = is.readAllBytes();
                    bucketMap.put(key, data);
                }
                exchange.sendResponseHeaders(200, -1);
            } else if ("GET".equals(method)) {
                if (key.isEmpty()) {
                    // List objects v2
                    String prefix = "";
                    if (query != null && query.contains("prefix=")) {
                        int pIdx = query.indexOf("prefix=");
                        String sub = query.substring(pIdx + 7);
                        int amp = sub.indexOf('&');
                        prefix = URLDecoder.decode(amp > 0 ? sub.substring(0, amp) : sub, StandardCharsets.UTF_8);
                    }

                    StringBuilder xml = new StringBuilder();
                    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                            .append("<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">\n")
                            .append("  <Name>").append(bucket).append("</Name>\n")
                            .append("  <Prefix>").append(prefix).append("</Prefix>\n");

                    for (Map.Entry<String, byte[]> entry : bucketMap.entrySet()) {
                        if (entry.getKey().startsWith(prefix)) {
                            xml.append("  <Contents>\n")
                                    .append("    <Key>").append(entry.getKey()).append("</Key>\n")
                                    .append("    <Size>").append(entry.getValue().length).append("</Size>\n")
                                    .append("  </Contents>\n");
                        }
                    }
                    xml.append("</ListBucketResult>");

                    byte[] resp = xml.toString().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/xml");
                    exchange.sendResponseHeaders(200, resp.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(resp);
                    }
                } else {
                    byte[] data = bucketMap.get(key);
                    if (data == null) {
                        exchange.sendResponseHeaders(404, -1);
                    } else {
                        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                        exchange.sendResponseHeaders(200, data.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(data);
                        }
                    }
                }
            } else if ("HEAD".equals(method)) {
                if (bucketMap.containsKey(key)) {
                    exchange.getResponseHeaders().set("Content-Length", String.valueOf(bucketMap.get(key).length));
                    exchange.sendResponseHeaders(200, -1);
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            } else if ("DELETE".equals(method)) {
                bucketMap.remove(key);
                exchange.sendResponseHeaders(204, -1);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
}
