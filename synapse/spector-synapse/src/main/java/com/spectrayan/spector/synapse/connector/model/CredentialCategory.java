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
