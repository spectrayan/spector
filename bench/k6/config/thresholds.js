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

export const SMOKE_THRESHOLDS = {
    http_req_failed: ['rate<0.01'], // < 1% errors
    http_req_duration: ['p(95)<1500'], // P95 < 1.5s (allows for initial LLM/Ollama cold-start embedding)
};

export const LOAD_THRESHOLDS = {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(90)<200', 'p(95)<500', 'p(99)<1000'],
    'memory_recall_duration': ['p(95)<500'],
    'memory_remember_duration': ['p(95)<50'],
};

export const STRESS_THRESHOLDS = {
    http_req_failed: ['rate<0.05'], // < 5% failure under extreme breaking load
    http_req_duration: ['p(90)<500', 'p(95)<1000', 'p(99)<2500'],
};

export const SPIKE_THRESHOLDS = {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<1000'],
};

export const MULTI_USER_THRESHOLDS = {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
    'user_isolation_violations': ['count==0'],
};
