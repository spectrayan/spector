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
package com.spectrayan.spector.synapse.security;

import com.spectrayan.spector.synapse.error.SynapseConflictException;

/**
 * Thrown when a user creation request specifies a {@code username} that already exists under
 * case-insensitive comparison (Requirement 16.4).
 *
 * <p>The offending username is exposed only via {@link #getUsername()}; the message is kept
 * generic so it can back a uniform {@code 409 Conflict} response without leaking additional
 * account detail.</p>
 */
public class DuplicateUsernameException extends SynapseConflictException {

    private final transient String username;

    /**
     * Creates the exception for the given (already-taken) username.
     *
     * @param username the username that is already in use
     */
    public DuplicateUsernameException(String username) {
        super("username", username);
        this.username = username;
    }

    /**
     * The username that triggered the conflict.
     *
     * @return the conflicting username
     */
    public String getUsername() {
        return username;
    }
}

