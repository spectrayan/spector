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
package com.spectrayan.spector.commons.concurrent;

/**
 * Strategy applied when a {@link SpectorTaskQueue} reaches maximum capacity.
 */
public enum BackpressurePolicy {
    /** Rejects submission immediately, returning false and incrementing failure counters. */
    REJECT_FAST,

    /** Discards the oldest task in the queue to make room for the new task. */
    DROP_OLDEST,

    /** Executes the task synchronously on the caller's thread if queue is saturated. */
    CALLER_RUNS
}
