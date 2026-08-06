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

import static com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_CONSOLIDATION_INTERVAL;

import java.io.Serializable;

/**
 * Memory consolidation configuration properties.
 */
public class ConsolidationProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private long interval = DEFAULT_CONSOLIDATION_INTERVAL.toMillis();

    public ConsolidationProperties() {}

    public ConsolidationProperties(long interval) {
        if (interval > 0) {
            this.interval = interval;
        }
    }

    public long getInterval() {
        return interval;
    }

    public void setInterval(long interval) {
        if (interval > 0) {
            this.interval = interval;
        }
    }

    public long interval() { return getInterval(); }
}
