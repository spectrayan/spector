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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry and lifecycle coordinator for all active {@link SpectorTaskQueue} instances.
 */
public final class TaskQueueManager {

    private static final Map<String, SpectorTaskQueue<?>> REGISTRY = new ConcurrentHashMap<>();

    private TaskQueueManager() {}

    /**
     * Registers an active task queue.
     */
    public static void register(String id, SpectorTaskQueue<?> queue) {
        if (id != null && queue != null) {
            REGISTRY.put(id, queue);
        }
    }

    /**
     * Unregisters a task queue (typically on close).
     */
    public static void unregister(String id) {
        if (id != null) {
            REGISTRY.remove(id);
        }
    }

    /**
     * Returns an unmodifiable snapshot of metrics for all currently registered queues.
     */
    public static List<SpectorTaskQueue.QueueMetrics> allMetrics() {
        List<SpectorTaskQueue.QueueMetrics> list = new ArrayList<>(REGISTRY.size());
        for (SpectorTaskQueue<?> queue : REGISTRY.values()) {
            list.add(queue.metrics());
        }
        return List.copyOf(list);
    }

    /**
     * Returns the number of registered task queues.
     */
    public static int registeredQueueCount() {
        return REGISTRY.size();
    }

    /**
     * Closes and drains all registered task queues.
     */
    public static void closeAll() {
        for (SpectorTaskQueue<?> queue : REGISTRY.values()) {
            try {
                queue.close();
            } catch (Exception ignored) {}
        }
        REGISTRY.clear();
    }
}
