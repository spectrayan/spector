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

import java.util.Objects;

/**
 * A {@link Runnable} wrapper that explicitly declares its target {@link ThreadPlane} and pool name.
 */
public final class PlaneAwareRunnable implements Runnable, PlaneAware {

    private final ThreadPlane plane;
    private final String poolName;
    private final Runnable delegate;

    public PlaneAwareRunnable(ThreadPlane plane, String poolName, Runnable delegate) {
        this.plane = plane != null ? plane : ThreadPlane.VIRTUAL;
        this.poolName = poolName != null && !poolName.isBlank() ? poolName : "default";
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
    }

    @Override
    public ThreadPlane plane() {
        return plane;
    }

    @Override
    public String poolName() {
        return poolName;
    }

    @Override
    public void run() {
        delegate.run();
    }
}