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

import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomInt } from 'k6/crypto';
import { ENV, PATHS } from '../config/environments.js';
import { MULTI_USER_THRESHOLDS } from '../config/thresholds.js';
import { getHeaders } from '../utils/auth.js';
import { generateMemoryPayload } from '../utils/generator.js';
import { recallDuration, rememberDuration } from '../utils/metrics.js';

export const options = {
    stages: [
        { duration: '20s', target: 20 },
        { duration: '1m', target: 50 },
        { duration: '20s', target: 0 },
    ],
    thresholds: MULTI_USER_THRESHOLDS,
};

export default function () {
    // Generate high-cardinality user & session IDs to force UserMemoryRegistry cache churn & LRU eviction
    const syntheticUserId = `tenant-user-${randomInt(200)}`;
    const syntheticSessionId = `session-${randomInt(1000)}`;

    const headers = getHeaders(null, syntheticSessionId, syntheticUserId);

    const isRecall = Math.random() < 0.7;
    if (isRecall) {
        const start = Date.now();
        const res = http.post(
            `${ENV.BASE_URL}${PATHS.MEMORY_RECALL}`,
            JSON.stringify({
                query: 'high cardinality user registry resolution test',
                topK: 5,
            }),
            { headers }
        );
        recallDuration.add(Date.now() - start);
        check(res, { 'User registry recall 200': (r) => r.status === 200 });
    } else {
        const memory = generateMemoryPayload('lru-churn');
        const start = Date.now();
        const res = http.post(
            `${ENV.BASE_URL}${PATHS.MEMORY_REMEMBER}`,
            JSON.stringify(memory),
            { headers }
        );
        rememberDuration.add(Date.now() - start);
        check(res, { 'User registry remember 202': (r) => r.status === 202 });
    }

    sleep(0.05);
}
