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
import { ENV, PATHS } from '../config/environments.js';

export function getHeaders(tokenOrApiKey = null, sessionId = null, namespaceId = null) {
    const headers = {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
    };

    if (ENV.AUTH_ENABLED) {
        if (tokenOrApiKey) {
            headers['Authorization'] = `Bearer ${tokenOrApiKey}`;
        }
    } else {
        headers['X-API-Key'] = tokenOrApiKey || ENV.API_KEY;
    }

    if (sessionId) {
        headers['X-Session-Id'] = sessionId;
    }
    if (namespaceId) {
        headers['X-Namespace-Id'] = namespaceId;
    }

    return headers;
}

export function loginUser(username, password) {
    const url = `${ENV.BASE_URL}${PATHS.AUTH_LOGIN}`;
    const payload = JSON.stringify({
        username: username,
        password: password,
    });
    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
        },
        timeout: ENV.DEFAULT_TIMEOUT,
    };

    const res = http.post(url, payload, params);
    if (res.status === 200) {
        const body = JSON.parse(res.body);
        return body.accessToken || body.token || null;
    }
    return null;
}

export function registerUser(username, password, role = 'USER') {
    const url = `${ENV.BASE_URL}${PATHS.AUTH_REGISTER}`;
    const payload = JSON.stringify({
        username: username,
        password: password,
        role: role,
    });
    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
        },
        timeout: ENV.DEFAULT_TIMEOUT,
    };

    return http.post(url, payload, params);
}
