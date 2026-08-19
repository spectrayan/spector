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
import { SPIKE_THRESHOLDS } from '../config/thresholds.js';
import { getHeaders } from '../utils/auth.js';
import { generateMemoryPayload } from '../utils/generator.js';

export const options = {
    stages: [
        { duration: '10s', target: 5 },   // Normal baseline
        { duration: '10s', target: 250 }, // Instant spike burst
        { duration: '1m', target: 250 },  // Hold spike
        { duration: '15s', target: 5 },   // Recovery
        { duration: '15s', target: 0 },
    ],
    thresholds: SPIKE_THRESHOLDS,
};

export default function () {
    const headers = getHeaders();
    const isRecall = Math.random() < 0.5;

    if (isRecall) {
        const res = http.post(
            `${ENV.BASE_URL}${PATHS.MEMORY_RECALL}`,
            JSON.stringify({
                query: 'virtual thread spike concurrency verification',
                topK: 3,
            }),
            { headers }
        );
        check(res, { 'Recall 200 during spike': (r) => r.status === 200 });
    } else {
        const res = http.post(
            `${ENV.BASE_URL}${PATHS.MEMORY_REMEMBER}`,
            JSON.stringify(generateMemoryPayload('spike')),
            { headers }
        );
        check(res, { 'Remember 202 during spike': (r) => r.status === 202 });
    }

    sleep(0.05);
}
