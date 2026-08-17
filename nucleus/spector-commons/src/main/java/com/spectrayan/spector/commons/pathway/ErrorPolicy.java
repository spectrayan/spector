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
package com.spectrayan.spector.commons.pathway;

/**
 * Defines the policy for handling errors that occur during signal transmission.
 */
public enum ErrorPolicy {

    /**
     * Halts execution immediately and propagates the error when a failure occurs.
     */
    FAIL_FAST,

    /**
     * Logs the error but allows the pathway to continue executing subsequent relays.
     */
    DEGRADE_GRACEFULLY
}
