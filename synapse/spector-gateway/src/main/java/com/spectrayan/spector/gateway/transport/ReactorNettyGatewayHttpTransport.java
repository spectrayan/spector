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
import com.spectrayan.spector.cluster.gateway.GatewayHttpTransport;
import io.netty.handler.codec.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reactor Netty transport implementation for the Spector Gateway (ADR-0081 §8 Phase 1 &amp; 2).
 *
 * <p>Streams both request bodies (chunked upload) and response bodies (streaming download)
 * without full buffering. Response headers are returned as soon as they arrive;
 * the response body streams asynchronously through a {@link PipedInputStream}/{@link PipedOutputStream}
 * pair, allowing SSE and large recalls to flow without sitting in memory.
 */
public class ReactorNettyGatewayHttpTransport implements GatewayHttpTransport {

    private static final Logger log = LoggerFactory.getLogger(ReactorNettyGatewayHttpTransport.class);

    private final HttpClient httpClient;
    private final Duration timeout;

    public ReactorNettyGatewayHttpTransport(Duration timeout) {
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(10);
        this.httpClient = HttpClient.create()
                .responseTimeout(this.timeout)
                .followRedirect(false);
    }

    @Override
    public ForwardResponse send(
            String targetNodeId,
            String targetUrl,
            ForwardRequest request,
            Map<String, String> routingHeaders
    ) throws Exception {

        String fullUrl = targetUrl + request.uriPath();

        HttpClient.RequestSender sender = httpClient
                .headers(headers -> {
                    for (Map.Entry<String, List<String>> entry : request.headers().entrySet()) {
                        String name = entry.getKey();
                        if (!"host".equalsIgnoreCase(name)
                                && !"content-length".equalsIgnoreCase(name)
                                && !"connection".equalsIgnoreCase(name)
                                && !"transfer-encoding".equalsIgnoreCase(name)) {
                            headers.add(name, entry.getValue());
                        }
                    }
                    for (Map.Entry<String, String> entry : routingHeaders.entrySet()) {
                        headers.set(entry.getKey(), entry.getValue());
                    }
                    if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
                        headers.set("Idempotency-Key", request.idempotencyKey());
                    }
                })
                .request(HttpMethod.valueOf(request.method().toUpperCase()))
                .uri(fullUrl);

        HttpClient.ResponseReceiver<?> receiver;
        if (request.contentLength() > 0 || (request.contentLength() < 0 && request.bodySupplier() != null)) {
            Flux<byte[]> chunkFlux = Flux.generate(
                    request::openBodyStream,
                    (in, sink) -> {
                        try {
                            byte[] buf = new byte[8192];
                            int read = in.read(buf);
                            if (read == -1) {
                                sink.complete();
                            } else if (read < buf.length) {
                                sink.next(Arrays.copyOf(buf, read));
                            } else {
                                sink.next(buf);
                            }
                        } catch (IOException e) {
                            sink.error(e);
                        }
                        return in;
                    },
                    in -> {
                        try { in.close(); } catch (Exception ignored) {}
                    }
            );
            receiver = sender.send((req, out) -> out.sendByteArray(chunkFlux));
        } else {
            receiver = sender;
        }

        // Stream the response: block only until headers arrive, then pipe body asynchronously.
        // This allows SSE, large recalls, and chunked responses to flow without full buffering.
        CountDownLatch headersReady = new CountDownLatch(1);
        AtomicReference<ForwardResponse> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        PipedOutputStream pipeOut = new PipedOutputStream();
        PipedInputStream pipeIn = new PipedInputStream(pipeOut, 65536); // 64 KiB pipe buffer

        receiver.responseConnection((res, conn) -> {
            // Headers are available immediately — capture status and headers
            int statusCode = res.status().code();
            Map<String, List<String>> resHeaders = new HashMap<>();
            for (Map.Entry<String, String> entry : res.responseHeaders()) {
                resHeaders.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
            }

            // Publish the ForwardResponse with the piped InputStream — caller can start reading
            resultRef.set(new ForwardResponse(statusCode, resHeaders, pipeIn, targetNodeId, 1));
            headersReady.countDown();

            // Stream response body chunks into the pipe asynchronously
            return conn.inbound().receive().asByteArray()
                    .doOnNext(bytes -> {
                        try {
                            pipeOut.write(bytes);
                            pipeOut.flush();
                        } catch (IOException e) {
                            log.debug("Pipe write error during response streaming: {}", e.getMessage());
                        }
                    })
                    .doOnComplete(() -> {
                        try { pipeOut.close(); } catch (IOException ignored) {}
                    })
                    .doOnError(err -> {
                        try { pipeOut.close(); } catch (IOException ignored) {}
                        // If headers haven't been delivered yet, propagate the error
                        if (headersReady.getCount() > 0) {
                            errorRef.set(err);
                            headersReady.countDown();
                        }
                    })
                    .then();
        }).subscribe(
                unused -> {},
                err -> {
                    // Connection-level error before headers arrived
                    try { pipeOut.close(); } catch (IOException ignored) {}
                    if (headersReady.getCount() > 0) {
                        errorRef.set(err);
                        headersReady.countDown();
                    }
                }
        );

        // Block only until headers arrive (not until the entire body is received)
        if (!headersReady.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            try { pipeOut.close(); } catch (IOException ignored) {}
            try { pipeIn.close(); } catch (IOException ignored) {}
            throw new IOException("Timed out waiting for response headers from " + fullUrl);
        }

        Throwable error = errorRef.get();
        if (error != null) {
            try { pipeIn.close(); } catch (IOException ignored) {}
            if (error instanceof Exception ex) throw ex;
            throw new RuntimeException(error);
        }

        return resultRef.get();
    }
}
