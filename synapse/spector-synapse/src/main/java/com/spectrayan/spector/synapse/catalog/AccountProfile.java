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

/**
 * Packaging profile that assigns default quotas and feature flags to an account.
 */
public enum AccountProfile {

    /**
     * Individual human developer or user profile.
     */
    HUMAN_SOLO,

    /**
     * Collaborative human team profile.
     */
    HUMAN_TEAM,

    /**
     * Dedicated autonomous agent profile.
     */
    AGENT,

    /**
     * Automated service or integration profile.
     */
    SERVICE,

    /**
     * Uncapped profile with unlimited quotas and all feature flags enabled.
     */
    UNLIMITED
}
