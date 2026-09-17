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
package com.spectrayan.spector.gateway.transport;

import com.spectrayan.spector.cluster.gateway.ForwardRequest;
import com.spectrayan.spector.cluster.gateway.ForwardResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test exercising the real {@link ReactorNettyGatewayHttpTransport} with
 * a local Reactor Netty HTTP server. Verifies that SSE frames stream through the pipe
 * without waiting for the entire response body (regression for the {@code responseSingle} bug).
 */
@DisplayName("ReactorNetty Transport Streaming Integration Test (ADR-0081)")
class ReactorNettyStreamingIntegrationTest {

    private static DisposableServer sseServer;
    private static int serverPort;

    @BeforeAll
    static void startServer() {
        sseServer = HttpServer.create()
                .port(0) // random available port
                .route(routes -> routes
                        .get("/api/v1/events", (req, res) -> {
                            res.header("Content-Type", "text/event-stream");
                            res.header("Cache-Control", "no-cache");
                            // Emit two SSE frames 500ms apart, then complete
                            Flux<byte[]> frames = Flux.just(
                                    "data: {\"event\":\"ping\",\"seq\":1}\n\n",
                                    "data: {\"event\":\"ping\",\"seq\":2}\n\n"
                            ).delayElements(Duration.ofMillis(500))
                             .map(s -> s.getBytes(StandardCharsets.UTF_8));
                            return res.sendByteArray(frames);
                        })
                        .get("/api/v1/health", (req, res) ->
                            res.sendString(Mono.just("{\"status\":\"UP\"}")))
                )
                .bindNow();
        serverPort = sseServer.port();
    }

    @AfterAll
    static void stopServer() {
        if (sseServer != null) sseServer.disposeNow();
    }

    @Test
    @DisplayName("SSE: first frame arrives before second frame is sent (streaming, not buffered)")
    void sseFramesStreamIncrementally() throws Exception {
        ReactorNettyGatewayHttpTransport transport = new ReactorNettyGatewayHttpTransport(
                Duration.ofSeconds(2), Duration.ofSeconds(10));

        ForwardRequest request = ForwardRequest.ofStream(
                "GET", "/api/v1/events",
                Collections.emptyMap(),
                null, 0L, null
        );

        long startMs = System.currentTimeMillis();

        ForwardResponse response = transport.send(
                "local-test", "http://localhost:" + serverPort,
                request, Collections.emptyMap()
        );

        assertThat(response.statusCode()).isEqualTo(200);

        // Read frames from the streamed body
        List<String> frames = new ArrayList<>();
        List<Long> arrivalTimesMs = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.bodyStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data:")) {
                    frames.add(line);
                    arrivalTimesMs.add(System.currentTimeMillis() - startMs);
                }
            }
        }
        response.close();

        // Verify both frames arrived
        assertThat(frames).hasSize(2);
        assertThat(frames.get(0)).contains("\"seq\":1");
        assertThat(frames.get(1)).contains("\"seq\":2");

        // KEY ASSERTION: The first frame must arrive well before the second.
        // If the transport fully buffers (responseSingle bug), both arrive at ~1000ms.
        // With streaming, frame 1 arrives at ~500ms, frame 2 at ~1000ms.
        // We assert frame 1 arrived before 900ms (generous margin).
        assertThat(arrivalTimesMs.get(0))
                .as("First SSE frame should stream through before second is sent (not buffered)")
                .isLessThan(900L);
    }

    @Test
    @DisplayName("Simple GET response streams correctly through the pipe")
    void simpleGetStreamsBody() throws Exception {
        ReactorNettyGatewayHttpTransport transport = new ReactorNettyGatewayHttpTransport(
                Duration.ofSeconds(2), Duration.ofSeconds(5));

        ForwardRequest request = ForwardRequest.ofStream(
                "GET", "/api/v1/health",
                Collections.emptyMap(),
                null, 0L, null
        );

        ForwardResponse response = transport.send(
                "local-test", "http://localhost:" + serverPort,
                request, Collections.emptyMap()
        );

        assertThat(response.statusCode()).isEqualTo(200);

        String body = new String(response.bodyStream().readAllBytes(), StandardCharsets.UTF_8);
        response.close();

        assertThat(body).contains("UP");
    }
}
