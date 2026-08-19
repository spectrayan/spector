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

import { Trend, Counter, Rate } from 'k6/metrics';

// Latency trends for specific operations
export const recallDuration = new Trend('memory_recall_duration', true);
export const rememberDuration = new Trend('memory_remember_duration', true);
export const searchDuration = new Trend('memory_search_duration', true);
export const tableScanDuration = new Trend('memory_table_scan_duration', true);
export const graphTraversalDuration = new Trend('memory_graph_traversal_duration', true);

// Counters
export const memoriesStoredCount = new Counter('memories_stored_total');
export const memoriesRecalledCount = new Counter('memories_recalled_total');
export const userIsolationViolations = new Counter('user_isolation_violations');

// Success rates
export const recallSuccessRate = new Rate('memory_recall_success_rate');
export const rememberSuccessRate = new Rate('memory_remember_success_rate');
