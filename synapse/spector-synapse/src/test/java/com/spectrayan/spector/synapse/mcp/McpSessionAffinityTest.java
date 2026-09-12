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
package com.spectrayan.spector.synapse.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpSessionAffinityTest {

    private static final String SESSION_ID = "mcp-session-test-42";

    @BeforeEach
    @AfterEach
    void cleanUp() {
        McpSessionContext.clearSession(SESSION_ID);
    }

    @Test
    @DisplayName("Req R10.1: MCP session binds to owner and preserves affinity across operations")
    void testSessionBindsToOwner() {
        McpSessionContext.bindSessionOwner(SESSION_ID, "node-1", 1L);
        assertThat(McpSessionContext.getSessionOwner(SESSION_ID)).contains("node-1");

        // Working items added
        McpSessionContext.addWorkingItem(SESSION_ID, new McpSessionContext.ActiveWorkingItem("item-1", "text", Map.of(), System.currentTimeMillis()));
        assertThat(McpSessionContext.getWorkingItems(SESSION_ID)).hasSize(1);

        // Check affinity against same owner and epoch -> true
        boolean intact = McpSessionContext.checkSessionAffinity(SESSION_ID, "node-1", 1L);
        assertThat(intact).isTrue();
        assertThat(McpSessionContext.getWorkingItems(SESSION_ID)).hasSize(1);
    }

    @Test
    @DisplayName("Req R10.2: Mid-session owner or epoch change terminates session and clears working items")
    void testMidSessionOwnerChangeTerminatesSession() {
        McpSessionContext.bindSessionOwner(SESSION_ID, "node-1", 1L);
        McpSessionContext.addWorkingItem(SESSION_ID, new McpSessionContext.ActiveWorkingItem("item-1", "volatile state", Map.of(), System.currentTimeMillis()));
        assertThat(McpSessionContext.getWorkingItems(SESSION_ID)).hasSize(1);

        // Rebalance or failover happens: owner shifts to node-2 at epoch 2
        boolean intact = McpSessionContext.checkSessionAffinity(SESSION_ID, "node-2", 2L);

        // Affinity check fails
        assertThat(intact).isFalse();

        // Volatile working set was cleared to avoid split-state execution on different node
        assertThat(McpSessionContext.getWorkingItems(SESSION_ID)).isEmpty();
        assertThat(McpSessionContext.getSessionOwner(SESSION_ID)).isEmpty();
    }
}
