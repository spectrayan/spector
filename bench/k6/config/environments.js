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

export const ENV = {
    BASE_URL: __ENV.BASE_URL || 'http://localhost:7070',
    API_KEY: __ENV.API_KEY || 'spector-dev-key',
    AUTH_ENABLED: __ENV.AUTH_ENABLED === 'true',
    DEFAULT_TIMEOUT: __ENV.TIMEOUT || '10s',
    DEFAULT_USER_COUNT: parseInt(__ENV.USER_COUNT || '10', 10),
    DEFAULT_SESSION_COUNT: parseInt(__ENV.SESSION_COUNT || '5', 10),
};

export const PATHS = {
    // Core Memory Endpoints
    MEMORY_STORE: '/api/v1/memory',
    MEMORY_REMEMBER: '/api/v1/memory/remember',
    MEMORY_RECALL: '/api/v1/memory/recall',
    MEMORY_SEARCH: '/api/v1/memory/search',
    MEMORY_BROWSE: '/api/v1/memory/browse',
    MEMORY_TABLE: '/api/v1/memory/table',
    MEMORY_CONSOLIDATE: '/api/v1/memory/consolidate',
    MEMORY_TOPOLOGY: '/api/v1/memory/topology-stats',
    MEMORY_GRAPH_OVERVIEW: '/api/v1/memory/graph/overview',
    MEMORY_STATS: '/api/v1/memory/stats',
    MEMORY_STATS_SCORING: '/api/v1/memory/stats/scoring',
    MEMORY_DIAGNOSTICS: '/api/v1/memory/diagnostics',
    MEMORY_PROJECTION: '/api/v1/memory/vector-space/projection',
    MEMORY_HARDWARE: '/api/v1/memory/hardware',
    MEMORY_LIVE_METRICS: '/api/v1/memory/metrics/live',

    // Per-Memory Sub-resources
    memoryItem: (id) => `/api/v1/memory/${id}`,
    memoryVector: (id) => `/api/v1/memory/${id}/vector`,
    memoryGraph: (id) => `/api/v1/memory/${id}/graph`,
    memoryReinforce: (id) => `/api/v1/memory/${id}/reinforce`,
    memorySuppress: (id) => `/api/v1/memory/${id}/suppress`,

    // Auth Endpoints
    AUTH_LOGIN: '/api/v1/auth/login',
    AUTH_REGISTER: '/api/v1/auth/register',
    AUTH_API_KEYS: '/api/v1/auth/api-keys',
    
    // System Endpoints
    HEALTH: '/actuator/health',
    METRICS: '/actuator/prometheus',
};
