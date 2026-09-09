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
package com.spectrayan.spector.commons.concurrent.spi;

import java.time.Duration;

/**
 * Result of a cooperative thread and executor drain operation before native resource release.
 *
 * @param completed      true if all tasks finished within the allocated budget; false if timed out
 * @param remainingTasks count of tasks still remaining or active when timeout occurred
 * @param waited         total duration waited during the drain operation
 */
public record DrainResult(
        boolean completed,
        int remainingTasks,
        Duration waited
) {

    /**
     * Creates a successful drain result with zero remaining tasks.
     *
     * @param waited duration waited
     * @return successful DrainResult
     */
    public static DrainResult ok(Duration waited) {
        return new DrainResult(true, 0, waited);
    }

    /**
     * Creates a timed-out drain result with the given remaining task count.
     *
     * @param remainingTasks count of remaining tasks
     * @param waited         duration waited before timeout
     * @return timed-out DrainResult
     */
    public static DrainResult timedOut(int remainingTasks, Duration waited) {
        return new DrainResult(false, remainingTasks, waited);
    }
}
