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

import static com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_EVENTS_ASYNC;

import java.io.Serializable;
import java.util.Objects;

/**
 * Configuration properties POJO for Spector Event Bus subsystem.
 */
public class EventsProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean async = DEFAULT_EVENTS_ASYNC;

    public EventsProperties() {}

    public EventsProperties(boolean async) {
        this.async = async;
    }

    public boolean isAsync() { return async; }
    public void setAsync(boolean async) { this.async = async; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventsProperties that = (EventsProperties) o;
        return async == that.async;
    }

    @Override
    public int hashCode() {
        return Objects.hash(async);
    }

    public EventsProperties copy() {
        return new EventsProperties(this.async);
    }
}
