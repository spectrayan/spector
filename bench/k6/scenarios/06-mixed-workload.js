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
import { LOAD_THRESHOLDS } from '../config/thresholds.js';
import { getHeaders } from '../utils/auth.js';
import { generateMemoryPayload } from '../utils/generator.js';
import {
    recallDuration,
    rememberDuration,
    searchDuration,
    tableScanDuration,
    memoriesStoredCount,
    memoriesRecalledCount,
} from '../utils/metrics.js';

export const options = {
    stages: [
        { duration: '30s', target: 25 },
        { duration: '2m', target: 50 },
        { duration: '30s', target: 0 },
    ],
    thresholds: LOAD_THRESHOLDS,
};

const sampleQueries = JSON.parse(open('../data/sample-queries.json'));

export default function () {
    const headers = getHeaders();
    const rand = Math.random();

    if (rand < 0.60) {
        // 60% Fused Cognitive Recall
        const queryItem = sampleQueries[Math.floor(Math.random() * sampleQueries.length)];
        const start = Date.now();
        const res = http.post(
            `${ENV.BASE_URL}${PATHS.MEMORY_RECALL}`,
            JSON.stringify({
                query: queryItem.query,
                topK: queryItem.topK,
            }),
            { headers }
        );
        recallDuration.add(Date.now() - start);
        if (check(res, { 'Recall 200': (r) => r.status === 200 })) {
            memoriesRecalledCount.add(1);
        }
    } else if (rand < 0.85) {
        // 25% Asynchronous Remember
        const memory = generateMemoryPayload('mixed');
        const start = Date.now();
        const res = http.post(
            `${ENV.BASE_URL}${PATHS.MEMORY_REMEMBER}`,
            JSON.stringify(memory),
            { headers }
        );
        rememberDuration.add(Date.now() - start);
        if (check(res, { 'Remember 202': (r) => r.status === 202 })) {
            memoriesStoredCount.add(1);
        }
    } else if (rand < 0.95) {
        // 10% Table View & Search
        if (Math.random() < 0.5) {
            const start = Date.now();
            const res = http.get(`${ENV.BASE_URL}${PATHS.MEMORY_TABLE}?page=0&pageSize=20`, { headers });
            tableScanDuration.add(Date.now() - start);
            check(res, { 'Table 200': (r) => r.status === 200 });
        } else {
            const start = Date.now();
            const res = http.post(
                `${ENV.BASE_URL}${PATHS.MEMORY_SEARCH}`,
                JSON.stringify({ query: 'preferences theme UI build', limit: 5 }),
                { headers }
            );
            searchDuration.add(Date.now() - start);
            check(res, { 'Search 200': (r) => r.status === 200 });
        }
    } else {
        // 5% Telemetry / Diagnostics
        const res = http.get(`${ENV.BASE_URL}${PATHS.MEMORY_STATS}`, { headers });
        check(res, { 'Stats 200': (r) => r.status === 200 });
    }

    sleep(0.1 + Math.random() * 0.1);
}
