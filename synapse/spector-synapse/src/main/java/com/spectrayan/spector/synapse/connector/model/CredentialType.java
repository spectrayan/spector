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
 * Format and structure of the sensitive credential payload.
 */
public enum CredentialType {
    API_KEY,
    BEARER_TOKEN,
    BASIC_AUTH,
    CONNECTION_STRING,
    OAUTH2,
    SERVICE_ACCOUNT_JSON,
    KEY_PAIR,
    APP_ROLE
}
