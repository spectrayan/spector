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
package com.spectrayan.spector.bench.conformance.model;

/**
 * Valence window range for MF-001 recall queries.
 */
public record MfValenceWindow(int min, int max) {
    public byte minByte() {
        return (byte) Math.clamp(min, -128, 127);
    }

    public byte maxByte() {
        return (byte) Math.clamp(max, -128, 127);
    }
}
