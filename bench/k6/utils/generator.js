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

export function randomString(length = 8) {
    const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
    let result = '';
    for (let i = 0; i < length; i++) {
        result += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return result;
}

export function generateTsid() {
    return '01' + randomString(11).toUpperCase();
}

export function generateMemoryPayload(prefix = 'k6') {
    const id = `${prefix}-${generateTsid()}`;
    const tiers = ['SEMANTIC', 'EPISODIC', 'PROCEDURAL'];
    const sources = ['USER_STATED', 'OBSERVED', 'DOCUMENTATION'];
    const tagOptions = ['ui', 'k6', 'benchmark', 'scale', 'database', 'system', 'agent', 'auth'];

    const tier = tiers[Math.floor(Math.random() * tiers.length)];
    const source = sources[Math.floor(Math.random() * sources.length)];
    const tags = [
        tagOptions[Math.floor(Math.random() * tagOptions.length)],
        tagOptions[Math.floor(Math.random() * tagOptions.length)],
    ];

    return {
        id: id,
        text: `Synthetic benchmark memory ${id}: Auto-generated statement for load testing virtual threads and off-heap store ${randomString(16)}.`,
        tier: tier,
        source: source,
        tags: Array.from(new Set(tags)),
        interest: Math.random(),
        challenge: Math.random(),
        urgency: Math.random(),
        valence: Math.floor((Math.random() - 0.5) * 100),
        arousal: Math.floor(Math.random() * 100),
    };
}
