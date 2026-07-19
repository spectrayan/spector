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
 * Utility for resolving security context parameters in Spector OSS.
 * Since Spector OSS is single-tenant and single-user, this utility maps
 * all scopes to the "default" identifier.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static String getTenantId() {
        return "default";
    }

    public static String getUserId() {
        return "default";
    }
}
