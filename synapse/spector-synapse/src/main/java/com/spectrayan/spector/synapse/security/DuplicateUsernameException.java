/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.security;

/**
 * Thrown when a user creation request specifies a {@code username} that already exists under
 * case-insensitive comparison (Requirement 16.4).
 *
 * <p>The offending username is exposed only via {@link #getUsername()}; the message is kept
 * generic so it can back a uniform {@code 409 Conflict} response without leaking additional
 * account detail.</p>
 */
public class DuplicateUsernameException extends RuntimeException {

    private final transient String username;

    /**
     * Creates the exception for the given (already-taken) username.
     *
     * @param username the username that is already in use
     */
    public DuplicateUsernameException(String username) {
        super("username already in use");
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
