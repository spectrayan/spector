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
package com.spectrayan.spector.synapse.agent.chat.model;

import java.time.Instant;

/**
 * Record representing a conversation session.
 *
 * @param sessionId       unique session identifier
 * @param messageCount    number of messages in the session
 * @param preview         short text preview of the conversation
 * @param startedAt       creation time
 * @param lastActivityAt  last message time
 */
public record Conversation(
        String sessionId,
        int messageCount,
        String preview,
        Instant startedAt,
        Instant lastActivityAt
) {}
