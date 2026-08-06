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
package com.spectrayan.spector.config.properties;

import static com.spectrayan.spector.config.SpectorPropertyConstants.*;

import java.io.Serializable;
import java.time.Duration;

/**
 * Configuration properties POJO for Spector REST/RPC Client connections.
 */
public class ClientProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private String host;
    private int port = DEFAULT_SERVER_PORT;
    private String apiKey = DEFAULT_AUTH_API_KEY;
    private int maxConnections = DEFAULT_CLIENT_MAX_CONNECTIONS;
    private Duration requestTimeout = DEFAULT_CLIENT_REQUEST_TIMEOUT;
    private Duration connectTimeout = DEFAULT_CLIENT_CONNECT_TIMEOUT;

    public ClientProperties() {}

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public int getMaxConnections() { return maxConnections; }
    public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }

    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }

    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
}
