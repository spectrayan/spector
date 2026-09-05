/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.pathway.reflect;

/**
 * Lifecycle status of a reflection consolidation sweep.
 *
 * @since 1.5.0
 */
public enum ReflectSweepStatus {
    /** Sweep configured but not yet started. */
    IDLE,
    /** Sweep actively executing. */
    RUNNING,
    /** Sweep temporarily paused or suspended on rate-limiting. */
    PAUSED,
    /** Sweep finished processing all eligible sessions or reached clean backlog. */
    COMPLETE,
    /** Sweep terminated abruptly due to unrecoverable failure. */
    FAILED
}
