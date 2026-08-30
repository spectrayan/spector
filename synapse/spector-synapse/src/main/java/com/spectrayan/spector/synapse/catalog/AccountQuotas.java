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
package com.spectrayan.spector.synapse.catalog;

import java.util.Objects;

/**
 * Quota limits for an account. Profile-based defaults; tenant plans may override. A value of -1 means unlimited.
 *
 * @param maxNamespaces         maximum number of total namespaces allowed for the account (-1 for unlimited)
 * @param maxHotNamespaces      maximum number of concurrently active (hot) namespaces (-1 for unlimited)
 * @param maxTotalStorageBytes  maximum total storage in bytes allowed across all namespaces (-1 for unlimited)
 * @param maxTotalMemories      maximum total memories allowed across all namespaces (-1 for unlimited)
 */
public record AccountQuotas(
        int maxNamespaces,
        int maxHotNamespaces,
        long maxTotalStorageBytes,
        long maxTotalMemories
) {

    /**
     * Uncapped quota instance with unlimited limits for all dimensions.
     */
    public static final AccountQuotas UNLIMITED = new AccountQuotas(-1, -1, -1, -1);

    /**
     * Resolves default quota limits for a given account packaging profile.
     *
     * @param profile the account profile to look up quotas for
     * @return the resolved {@link AccountQuotas}
     * @throws NullPointerException if {@code profile} is null
     */
    public static AccountQuotas forProfile(AccountProfile profile) {
        Objects.requireNonNull(profile, "profile must not be null");
        return switch (profile) {
            case HUMAN_SOLO -> new AccountQuotas(4, 2, -1, -1);
            case HUMAN_TEAM -> new AccountQuotas(16, 4, -1, -1);
            case AGENT      -> new AccountQuotas(64, 4, -1, -1);
            case SERVICE    -> new AccountQuotas(256, 8, -1, -1);
            case UNLIMITED  -> new AccountQuotas(-1, -1, -1, -1);
        };
    }
}
