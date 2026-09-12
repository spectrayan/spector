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
 * Immutable response representation from forwarded gateway execution (ADR-0034 §8, Req R7.4).
 *
 * @param statusCode   HTTP response status code
 * @param headers      response headers
 * @param body         response body bytes
 * @param finalOwnerId node identifier that handled the final execution attempt
 * @param attempts     total forwarding attempts made (including bounded retries)
 */
public record ForwardResponse(
        int statusCode,
        Map<String, List<String>> headers,
        byte[] body,
        String finalOwnerId,
        int attempts
) {

    public ForwardResponse {
        if (headers == null) {
            headers = Collections.emptyMap();
        }
        if (body == null) {
            body = new byte[0];
        }
    }
}
