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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reactor Netty transport implementation for the Spector Gateway (ADR-0081 §8 Phase 1 & 2).
 * Streams request bodies in chunks and returns streaming responses without full buffering.
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

        return receiver.responseSingle((res, byteBufMono) -> {
            int statusCode = res.status().code();
            Map<String, List<String>> resHeaders = new HashMap<>();
            for (Map.Entry<String, String> entry : res.responseHeaders()) {
                resHeaders.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
            }

            return byteBufMono.asInputStream().map(in ->
                    new ForwardResponse(statusCode, resHeaders, in, targetNodeId, 1)
            ).defaultIfEmpty(new ForwardResponse(statusCode, resHeaders, InputStream.nullInputStream(), targetNodeId, 1));
        }).block(timeout);
    }
}
