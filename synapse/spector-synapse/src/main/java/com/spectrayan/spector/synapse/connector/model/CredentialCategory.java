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
package com.spectrayan.spector.synapse.connector.model;

/**
 * Broad functional domain category of a stored credential.
 */
public enum CredentialCategory {
    LLM("AI & Cognitive Models"),
    CHANNEL("Messaging Channels"),
    DATABASE("Databases & Data Stores"),
    BROKER("Message Brokers & Event Streaming"),
    STORAGE("Cloud & Object Storage"),
    APP("SaaS & Enterprise Applications"),
    VAULT("External KMS & Key Vaults");

    private final String description;

    CredentialCategory(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
