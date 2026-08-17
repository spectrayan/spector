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
package com.spectrayan.spector.config.properties;

import com.spectrayan.spector.commons.concurrent.BackpressurePolicy;
import com.spectrayan.spector.commons.concurrent.TaskQueueConfig;

import java.io.Serializable;
import java.util.Locale;

/**
 * Configuration properties for generic asynchronous task queues.
 */
public class TaskQueueProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private int capacity = TaskQueueConfig.DEFAULT_CAPACITY;
    private int parallelism = TaskQueueConfig.DEFAULT_PARALLELISM;
    private long pollTimeoutMs = TaskQueueConfig.DEFAULT_POLL_TIMEOUT_MS;
    private long drainTimeoutMs = TaskQueueConfig.DEFAULT_DRAIN_TIMEOUT_MS;
    private int maxRetries = TaskQueueConfig.DEFAULT_MAX_RETRIES;
    private long retryBackoffMs = TaskQueueConfig.DEFAULT_RETRY_BACKOFF_MS;
    private String backpressurePolicy = TaskQueueConfig.DEFAULT_BACKPRESSURE_POLICY.name();

    public TaskQueueProperties() {}

    public TaskQueueProperties(int capacity, int parallelism) {
        this.capacity = Math.max(16, capacity);
        this.parallelism = Math.max(1, parallelism);
    }

    public TaskQueueConfig toConfig() {
        BackpressurePolicy policy;
        try {
            policy = BackpressurePolicy.valueOf(backpressurePolicy.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (Exception e) {
            policy = BackpressurePolicy.REJECT_FAST;
        }
        return new TaskQueueConfig(
                Math.max(16, capacity),
                Math.max(1, parallelism),
                Math.max(10, pollTimeoutMs),
                Math.max(50, drainTimeoutMs),
                Math.max(0, maxRetries),
                Math.max(0, retryBackoffMs),
                policy
        );
    }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public int capacity() { return getCapacity(); }

    public int getParallelism() { return parallelism; }
    public void setParallelism(int parallelism) { this.parallelism = parallelism; }
    public int parallelism() { return getParallelism(); }

    public long getPollTimeoutMs() { return pollTimeoutMs; }
    public void setPollTimeoutMs(long pollTimeoutMs) { this.pollTimeoutMs = pollTimeoutMs; }
    public long pollTimeoutMs() { return getPollTimeoutMs(); }

    public long getDrainTimeoutMs() { return drainTimeoutMs; }
    public void setDrainTimeoutMs(long drainTimeoutMs) { this.drainTimeoutMs = drainTimeoutMs; }
    public long drainTimeoutMs() { return getDrainTimeoutMs(); }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public int maxRetries() { return getMaxRetries(); }

    public long getRetryBackoffMs() { return retryBackoffMs; }
    public void setRetryBackoffMs(long retryBackoffMs) { this.retryBackoffMs = retryBackoffMs; }
    public long retryBackoffMs() { return getRetryBackoffMs(); }

    public String getBackpressurePolicy() { return backpressurePolicy; }
    public void setBackpressurePolicy(String backpressurePolicy) { this.backpressurePolicy = backpressurePolicy; }
    public String backpressurePolicy() { return getBackpressurePolicy(); }
}
