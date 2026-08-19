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
    memoriesStoredCount,
    memoriesRecalledCount,
    recallSuccessRate,
    rememberSuccessRate
} from '../utils/metrics.js';

export const options = {
    stages: [
        { duration: '30s', target: 20 },  // Ramp up
        { duration: '2m', target: 50 },   // Sustain 50 VUs
        { duration: '30s', target: 0 },   // Ramp down
    ],
    thresholds: LOAD_THRESHOLDS,
};

const sampleQueries = JSON.parse(open('../data/sample-queries.json'));

export default function () {
    const headers = getHeaders();
    const isRecall = Math.random() < 0.7; // 70% recall, 30% remember

    if (isRecall) {
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
        const duration = Date.now() - start;
        recallDuration.add(duration);

        const ok = check(res, { 'Recall 200 OK': (r) => r.status === 200 });
        recallSuccessRate.add(ok);
        if (ok) {
            memoriesRecalledCount.add(1);
        }
    } else {
        const memoryPayload = generateMemoryPayload('load');
        const start = Date.now();
        const res = http.post(
            `${ENV.BASE_URL}${PATHS.MEMORY_REMEMBER}`,
            JSON.stringify(memoryPayload),
            { headers }
        );
        const duration = Date.now() - start;
        rememberDuration.add(duration);

        const ok = check(res, { 'Remember 202 Accepted': (r) => r.status === 202 });
        rememberSuccessRate.add(ok);
        if (ok) {
            memoriesStoredCount.add(1);
        }
    }

    sleep(0.1 + Math.random() * 0.2);
}
