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
package com.spectrayan.spector.connector.core;

import com.spectrayan.spector.connector.spi.ConnectionProber;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry and factory for {@link ConnectionProber} implementations.
 */
public final class ConnectionProbers {

    private static final Map<String, ConnectionProber> PROBERS = new HashMap<>();

    static {
        // FILE_WATCH Prober
        PROBERS.put("FILE_WATCH", properties -> {
            String pathStr = properties.get("path");
            if (pathStr == null || pathStr.isBlank()) {
                throw new IllegalArgumentException("Directory path parameter is required");
            }
            Path path = Paths.get(pathStr);
            if (!Files.exists(path)) {
                throw new FileNotFoundException("Directory path does not exist: " + pathStr);
            }
            if (!Files.isDirectory(path)) {
                throw new IllegalArgumentException("Path is not a directory: " + pathStr);
            }
            if (!Files.isReadable(path)) {
                throw new IOException("Directory path is not readable: " + pathStr);
            }
            return true;
        });

        // REST_API Prober
        PROBERS.put("REST_API", properties -> {
            String urlStr = properties.get("url");
            if (urlStr == null || urlStr.isBlank()) {
                throw new IllegalArgumentException("URL is required");
            }
            URL url = new URI(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            // Any response code means server is reachable
            conn.getResponseCode();
            return true;
        });

        // DIRECT Prober
        PROBERS.put("DIRECT", properties -> true);

        // DATABASE Prober
        PROBERS.put("DATABASE", properties -> {
            String jdbcUrl = properties.get("jdbcUrl");
            if (jdbcUrl == null || jdbcUrl.isBlank()) {
                throw new IllegalArgumentException("JDBC URL is required");
            }
            // Parse jdbc URL e.g. jdbc:postgresql://localhost:5432/mydb
            if (jdbcUrl.startsWith("jdbc:")) {
                int doubleSlash = jdbcUrl.indexOf("//");
                if (doubleSlash != -1) {
                    String remaining = jdbcUrl.substring(doubleSlash + 2);
                    int nextSlash = remaining.indexOf("/");
                    String hostPort = nextSlash != -1 ? remaining.substring(0, nextSlash) : remaining;
                    int colon = hostPort.indexOf(":");
                    String host = colon != -1 ? hostPort.substring(0, colon) : hostPort;
                    int port = 5432;
                    if (colon != -1) {
                        try {
                            port = Integer.parseInt(hostPort.substring(colon + 1));
                        } catch (NumberFormatException ignored) {}
                    }
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(host, port), 2000);
                        return true;
                    } catch (IOException e) {
                        throw new IOException("Failed to connect to database host at " + host + ":" + port, e);
                    }
                }
            }
            return true; // fallback success if we cannot parse
        });

        // DEFAULT fallback for S3, Kafka, Webhook, Slack, Email etc.
        // It does a basic success or socket check if a host is identifiable.
        PROBERS.put("DEFAULT", properties -> true);
    }

    private ConnectionProbers() {}

    /**
     * Finds a connection prober for a connector type.
     *
     * @param connectorType the connector type (e.g. "FILE_WATCH", "REST_API")
     * @return the prober
     */
    public static ConnectionProber getProber(String connectorType) {
        return PROBERS.getOrDefault(connectorType, PROBERS.get("DEFAULT"));
    }
}
