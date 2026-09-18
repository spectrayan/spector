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
package com.spectrayan.spector.synapse.security.injection;

/**
 * Classification of a detected prompt-injection attempt.
 */
public enum InjectionType {
    /** None detected. */
    NONE,
    /** Jailbreak / system-prompt override in user-facing input. */
    DIRECT,
    /** Malicious instructions embedded in files, URLs, RAG chunks, or tool output. */
    INDIRECT
}
