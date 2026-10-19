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
package com.spectrayan.spector.commons.crypto;

/**
 * Cryptographic health status of a tenant Key-Encryption Key (KEK) in an external KMS
 * or local provider (ADR-0088 §5.1, Issue #1070).
 */
public enum KeyHealthStatus {

    /** The KEK is enabled, reachable, and capable of wrapping/unwrapping DEKs. */
    HEALTHY,

    /** The KEK is temporarily disabled in the external KMS. */
    DISABLED,

    /** The KEK has been revoked, scheduled for deletion, or permanently destroyed. */
    REVOKED,

    /** The external KMS endpoint could not be reached or timed out. */
    UNREACHABLE
}
