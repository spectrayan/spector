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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

/**
 * Configuration parameters governing a {@link SpectorTaskQueue}.
 *
 * @param capacity           maximum number of tasks buffered in queue (min 16)
 * @param parallelism        number of concurrent virtual worker threads (min 1)
 * @param pollTimeoutMs      timeout in ms for workers polling the queue
 * @param drainTimeoutMs     timeout in ms to wait for queue drain during close
 * @param maxRetries         maximum retry attempts on transient failure (>= 0)
 * @param retryBackoffMs     delay between retry attempts in milliseconds (>= 0)
 * @param backpressurePolicy policy applied when capacity is exceeded
 */
public record TaskQueueConfig(
        int capacity,
        int parallelism,
        long pollTimeoutMs,
        long drainTimeoutMs,
        int maxRetries,
        long retryBackoffMs,
        BackpressurePolicy backpressurePolicy
) {

    public static final int DEFAULT_CAPACITY = 1000;
    public static final int DEFAULT_PARALLELISM = 1;
    public static final long DEFAULT_POLL_TIMEOUT_MS = 500L;
    public static final long DEFAULT_DRAIN_TIMEOUT_MS = 5000L;
    public static final int DEFAULT_MAX_RETRIES = 2;
    public static final long DEFAULT_RETRY_BACKOFF_MS = 500L;
    public static final BackpressurePolicy DEFAULT_BACKPRESSURE_POLICY = BackpressurePolicy.REJECT_FAST;

    public TaskQueueConfig {
        if (capacity < 16) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_OUT_OF_RANGE, "capacity", 16, Integer.MAX_VALUE, capacity);
        }
        if (parallelism < 1) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_OUT_OF_RANGE, "parallelism", 1, 1024, parallelism);
        }
        if (pollTimeoutMs < 10) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_OUT_OF_RANGE, "pollTimeoutMs", 10, 60000, pollTimeoutMs);
        }
        if (drainTimeoutMs < 50) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_OUT_OF_RANGE, "drainTimeoutMs", 50, 300000, drainTimeoutMs);
        }
        if (maxRetries < 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_OUT_OF_RANGE, "maxRetries", 0, 20, maxRetries);
        }
        if (retryBackoffMs < 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_OUT_OF_RANGE, "retryBackoffMs", 0, 60000, retryBackoffMs);
        }
        if (backpressurePolicy == null) {
            backpressurePolicy = DEFAULT_BACKPRESSURE_POLICY;
        }
    }

    public static TaskQueueConfig ofDefaults() {
        return new TaskQueueConfig(
                DEFAULT_CAPACITY,
                DEFAULT_PARALLELISM,
                DEFAULT_POLL_TIMEOUT_MS,
                DEFAULT_DRAIN_TIMEOUT_MS,
                DEFAULT_MAX_RETRIES,
                DEFAULT_RETRY_BACKOFF_MS,
                DEFAULT_BACKPRESSURE_POLICY
        );
    }

    public static TaskQueueConfig of(int capacity, int parallelism) {
        return new TaskQueueConfig(
                Math.max(16, capacity),
                Math.max(1, parallelism),
                DEFAULT_POLL_TIMEOUT_MS,
                DEFAULT_DRAIN_TIMEOUT_MS,
                DEFAULT_MAX_RETRIES,
                DEFAULT_RETRY_BACKOFF_MS,
                DEFAULT_BACKPRESSURE_POLICY
        );
    }
}
