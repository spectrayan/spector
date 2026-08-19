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
import { ENV, PATHS } from '../config/environments.js';
import { SMOKE_THRESHOLDS } from '../config/thresholds.js';
import { getHeaders } from '../utils/auth.js';
import { generateMemoryPayload } from '../utils/generator.js';

export const options = {
    vus: 1,
    iterations: 1,
    thresholds: SMOKE_THRESHOLDS,
};

export default function () {
    const headers = getHeaders();
    const testMemory = generateMemoryPayload('smoke');

    // 1. Health check
    let res = http.get(`${ENV.BASE_URL}${PATHS.HEALTH}`, { headers });
    check(res, { 'Health check is 200': (r) => r.status === 200 });

    // 2. Synchronous store (uses List<String> tags)
    res = http.post(
        `${ENV.BASE_URL}${PATHS.MEMORY_STORE}`,
        JSON.stringify({
            text: testMemory.text,
            tags: ['smoke', 'sync-test'],
        }),
        { headers }
    );
    check(res, { 'Sync Store status is 201': (r) => r.status === 201 });
    let createdId = null;
    if (res.status === 201) {
        const body = JSON.parse(res.body);
        createdId = body.id;
    }

    // 3. Asynchronous remember (uses comma-separated tags string)
    res = http.post(
        `${ENV.BASE_URL}${PATHS.MEMORY_REMEMBER}`,
        JSON.stringify(testMemory),
        { headers }
    );
    check(res, { 'Async Remember status is 202': (r) => r.status === 202 });

    // 4. Memory table paginated list
    res = http.get(`${ENV.BASE_URL}${PATHS.MEMORY_TABLE}?page=0&pageSize=10`, { headers });
    check(res, { 'Memory Table status is 200': (r) => r.status === 200 });

    // 5. Cognitive recall
    res = http.post(
        `${ENV.BASE_URL}${PATHS.MEMORY_RECALL}`,
        JSON.stringify({
            query: 'synthetic benchmark memory',
            topK: 5,
        }),
        { headers }
    );
    check(res, { 'Recall status is 200': (r) => r.status === 200 });

    // 6. Semantic search
    res = http.post(
        `${ENV.BASE_URL}${PATHS.MEMORY_SEARCH}`,
        JSON.stringify({
            query: 'benchmark scale',
            limit: 5,
        }),
        { headers }
    );
    check(res, { 'Search status is 200': (r) => r.status === 200 });

    // 7. Tag browsing
    res = http.post(
        `${ENV.BASE_URL}${PATHS.MEMORY_BROWSE}`,
        JSON.stringify({
            tags: ['benchmark'],
            limit: 10,
        }),
        { headers }
    );
    check(res, { 'Browse status is 200': (r) => r.status === 200 });

    // 8. Stats & telemetry
    res = http.get(`${ENV.BASE_URL}${PATHS.MEMORY_STATS}`, { headers });
    check(res, { 'Memory Stats status is 200': (r) => r.status === 200 });

    res = http.get(`${ENV.BASE_URL}${PATHS.MEMORY_DIAGNOSTICS}`, { headers });
    check(res, { 'Diagnostics status is 200': (r) => r.status === 200 });

    // 9. Point lookup if memory was created
    if (createdId) {
        res = http.get(`${ENV.BASE_URL}${PATHS.memoryItem(createdId)}`, { headers });
        check(res, { 'Get Memory by ID status is 200': (r) => r.status === 200 });

        // 10. Reinforce
        res = http.post(
            `${ENV.BASE_URL}${PATHS.memoryReinforce(createdId)}`,
            JSON.stringify({ valence: 50 }),
            { headers }
        );
        check(res, { 'Reinforce status is 200': (r) => r.status === 200 });

        // 11. Suppress
        res = http.post(
            `${ENV.BASE_URL}${PATHS.memorySuppress(createdId)}`,
            JSON.stringify({ action: 'suppress', reason: 'smoke test' }),
            { headers }
        );
        check(res, { 'Suppress status is 200': (r) => r.status === 200 });

        // 12. Forget
        res = http.del(`${ENV.BASE_URL}${PATHS.memoryItem(createdId)}`, null, { headers });
        check(res, { 'Delete status is 200': (r) => r.status === 200 });
    }

    sleep(0.5);
}
