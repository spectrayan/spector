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

import static com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_CONCURRENCY_STRUCTURED;

import java.io.Serializable;
import java.util.Objects;

/**
 * Configuration properties POJO for Spector Concurrency subsystem.
 */
public class ConcurrencyProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean structured = DEFAULT_CONCURRENCY_STRUCTURED;

    public ConcurrencyProperties() {}

    public ConcurrencyProperties(boolean structured) {
        this.structured = structured;
    }

    public boolean isStructured() { return structured; }
    public void setStructured(boolean structured) { this.structured = structured; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConcurrencyProperties that = (ConcurrencyProperties) o;
        return structured == that.structured;
    }

    @Override
    public int hashCode() {
        return Objects.hash(structured);
    }

    public ConcurrencyProperties copy() {
        return new ConcurrencyProperties(this.structured);
    }
}
