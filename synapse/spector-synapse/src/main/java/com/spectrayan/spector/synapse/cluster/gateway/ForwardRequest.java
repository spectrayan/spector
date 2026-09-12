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
package com.spectrayan.spector.synapse.cluster.gateway;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable request representation for gateway forwarding (ADR-0034 §8, Req R7.2, R7.5).
 *
 * @param method         HTTP method (GET, POST, etc.)
 * @param uriPath        subpath and query parameters (e.g. {@code /api/v1/memory/remember?namespace=alpha})
 * @param headers        request headers
 * @param body           request body bytes
 * @param idempotencyKey original client idempotency key reused across retries (Invariant K7)
 */
public record ForwardRequest(
        String method,
        String uriPath,
        Map<String, List<String>> headers,
        byte[] body,
        String idempotencyKey
) {

    public ForwardRequest {
        if (headers == null) {
            headers = Collections.emptyMap();
        }
        if (body == null) {
            body = new byte[0];
        }
    }
}
