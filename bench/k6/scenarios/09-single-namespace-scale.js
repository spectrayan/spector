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
import { Trend, Rate, Counter } from 'k6/metrics';
import { ENV, PATHS } from '../config/environments.js';
import { getHeaders } from '../utils/auth.js';

// Custom metrics for single-namespace scale benchmark
export const scaleRecallBudgetedDuration = new Trend('scale_recall_budgeted_duration', true);
export const scaleRecallUnbudgetedDuration = new Trend('scale_recall_unbudgeted_duration', true);
export const scaleGraphOnDuration = new Trend('scale_graph_on_duration', true);
export const scaleGraphOffDuration = new Trend('scale_graph_off_duration', true);
export const scaleTruncationRate = new Rate('scale_truncation_rate');
export const scalePartitionsVisitedCount = new Counter('scale_partitions_visited_count');
export const scalePartitionsSkippedCount = new Counter('scale_partitions_skipped_count');

const scaleTier = __ENV.SCALE_TIER || '100k';
const visitBudget = parseInt(__ENV.VISIT_BUDGET || '10', 10);
const duration = __ENV.DURATION || '1m';
const vus = parseInt(__ENV.VUS || '10', 10);

export const options = {
    stages: [
        { duration: '15s', target: vus },
        { duration: duration, target: vus },
        { duration: '15s', target: 0 },
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],
        scale_recall_budgeted_duration: ['p(95)<35', 'p(99)<75'],
    },
};

const sampleQueries = [
    'database connection pool tuning',
    'microservice circuit breaker tripping',
    'kafka consumer rebalance latency',
    'redis cache invalidation storm',
    'distributed transaction two-phase commit',
    'panama off-heap memory arena alignment',
    'hebbian synaptic co-activation reinforcement',
    'neural manifold trajectory projection',
    'biological sleep consolidation replay',
    'memory table cursor pagination stability'
];

export default function () {
    const headers = getHeaders();
    const query = sampleQueries[Math.floor(Math.random() * sampleQueries.length)];
    const roll = Math.random();

    if (roll < 0.50) {
        // 1. Budgeted Recall (evaluates visit budget effectiveness and truncation observability)
        const start = Date.now();
        const res = http.post(
            `${ENV.BASE_URL}${PATHS.MEMORY_RECALL}`,
            JSON.stringify({
                query: query,
                topK: 10,
                partitionVisitBudget: visitBudget,
            }),
            { headers }
        );
        const elapsed = Date.now() - start;
        scaleRecallBudgetedDuration.add(elapsed);

        const success = check(res, {
            'Budgeted recall 200': (r) => r.status === 200,
        });

        if (success && res.body) {
            try {
                const body = JSON.parse(res.body);
                const isTruncated = res.headers['X-Spector-Recall-Truncated'] === 'true' ||
                    (Array.isArray(body) && body.some((item) => item.truncated === true)) ||
                    (body.truncated === true);
                scaleTruncationRate.add(isTruncated ? 1 : 0);
            } catch (ignored) {}
        }
    } else if (roll < 0.75) {
        // 2. Hebbian Graph Expansion Enabled (Threshold = 1.0)
        const start = Date.now();
        const res = http.post(
            `${ENV.BASE_URL}${PATHS.MEMORY_RECALL}`,
            JSON.stringify({
                query: query,
                topK: 10,
                graphExpansionThreshold: 1.0,
            }),
            { headers }
        );
        scaleGraphOnDuration.add(Date.now() - start);
        check(res, { 'Graph ON recall 200': (r) => r.status === 200 });
    } else {
        // 3. Hebbian Graph Expansion Disabled (Threshold = 0.0)
        const start = Date.now();
        const res = http.post(
            `${ENV.BASE_URL}${PATHS.MEMORY_RECALL}`,
            JSON.stringify({
                query: query,
                topK: 10,
                graphExpansionThreshold: 0.0,
            }),
            { headers }
        );
        scaleGraphOffDuration.add(Date.now() - start);
        check(res, { 'Graph OFF recall 200': (r) => r.status === 200 });
    }

    sleep(0.05);
}
