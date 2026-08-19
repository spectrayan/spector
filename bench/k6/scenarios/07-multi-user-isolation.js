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
import { MULTI_USER_THRESHOLDS } from '../config/thresholds.js';
import { getHeaders, loginUser, registerUser } from '../utils/auth.js';
import { userIsolationViolations, recallDuration } from '../utils/metrics.js';

export const options = {
    vus: 10,
    iterations: 20,
    thresholds: MULTI_USER_THRESHOLDS,
};

const users = JSON.parse(open('../data/sample-users.json'));

export function setup() {
    // If auth is enabled, ensure all test users are registered
    if (ENV.AUTH_ENABLED) {
        for (const user of users) {
            registerUser(user.username, user.password, user.role);
        }
    }
    return { users };
}

export default function (data) {
    const userIndex = (__VU - 1) % users.length;
    const currentUser = users[userIndex];
    const userSecretTag = `SECRET_PROJECT_FOR_USER_${userIndex}`;
    const userSecretFact = `Top secret project authorization code: ALPHA-${userIndex}-${__ITER}`;

    let authToken = null;
    if (ENV.AUTH_ENABLED) {
        authToken = loginUser(currentUser.username, currentUser.password);
    }

    const headers = getHeaders(authToken, `sess-${userIndex}-${__ITER}`, `tenant-${currentUser.tenant}`);

    // 1. Ingest user private secret memory
    const storeRes = http.post(
        `${ENV.BASE_URL}${PATHS.MEMORY_STORE}`,
        JSON.stringify({
            text: userSecretFact,
            tags: [userSecretTag, 'private-auth'],
        }),
        { headers }
    );
    check(storeRes, { 'User private store 201': (r) => r.status === 201 || r.status === 200 });

    sleep(0.1);

    // 2. Recall query looking for secrets
    const start = Date.now();
    const recallRes = http.post(
        `${ENV.BASE_URL}${PATHS.MEMORY_RECALL}`,
        JSON.stringify({
            query: 'Top secret project authorization code',
            topK: 10,
        }),
        { headers }
    );
    recallDuration.add(Date.now() - start);

    if (check(recallRes, { 'Recall 200': (r) => r.status === 200 })) {
        const body = JSON.parse(recallRes.body);
        const results = Array.isArray(body) ? body : (body.results || []);

        // 3. Strict Multi-User Isolation Invariant Check:
        // Current user must NEVER see secrets belonging to other users!
        for (const item of results) {
            const text = item.text || '';
            for (let otherIdx = 0; otherIdx < users.length; otherIdx++) {
                if (otherIdx !== userIndex) {
                    const foreignSecretPattern = `ALPHA-${otherIdx}-`;
                    if (text.includes(foreignSecretPattern)) {
                        userIsolationViolations.add(1);
                        console.error(`ISOLATION VIOLATION: User ${currentUser.username} leaked secret belonging to user ${otherIdx}!`);
                    }
                }
            }
        }
    }

    sleep(0.2);
}
