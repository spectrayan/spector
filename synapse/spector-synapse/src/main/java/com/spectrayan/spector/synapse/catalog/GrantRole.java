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
package com.spectrayan.spector.synapse.catalog;

import java.util.Objects;

/**
 * Role-based grant level for namespace trace access. OWNER &gt; ADMIN &gt; WRITER &gt; READER.
 */
public enum GrantRole {

    /**
     * Full ownership role with permission to manage lifecycle, grants, and data.
     */
    OWNER,

    /**
     * Administrative role with permission to manage grants and data.
     */
    ADMIN,

    /**
     * Write role with permission to read and append trace records.
     */
    WRITER,

    /**
     * Read-only role with permission to view trace records.
     */
    READER;

    /**
     * Checks if this role is at least as privileged as the specified minimum role.
     *
     * @param minimum the minimum required role
     * @return {@code true} if this role is greater than or equal to the minimum role in privilege
     * @throws NullPointerException if {@code minimum} is null
     */
    public boolean isAtLeast(GrantRole minimum) {
        Objects.requireNonNull(minimum, "minimum role must not be null");
        return this.ordinal() <= minimum.ordinal();
    }
}
