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
 * Feature flags for an account. Controlled by AccountProfile packaging, not the identity model.
 *
 * @param multiNamespace whether the account can create and manage multiple namespaces
 * @param sharing        whether the account can share namespaces across principals
 * @param federation     whether the account can participate in cross-instance federation
 */
public record AccountFlags(
        boolean multiNamespace,
        boolean sharing,
        boolean federation
) {

    /**
     * Resolves default feature flags for a given account packaging profile.
     *
     * @param profile the account profile to look up flags for
     * @return the resolved {@link AccountFlags}
     * @throws NullPointerException if {@code profile} is null
     */
    public static AccountFlags forProfile(AccountProfile profile) {
        Objects.requireNonNull(profile, "profile must not be null");
        return switch (profile) {
            case HUMAN_SOLO -> new AccountFlags(true, false, false);
            case HUMAN_TEAM -> new AccountFlags(true, true, false);
            case AGENT      -> new AccountFlags(true, false, false);
            case SERVICE    -> new AccountFlags(true, true, true);
            case UNLIMITED  -> new AccountFlags(true, true, true);
        };
    }
}
