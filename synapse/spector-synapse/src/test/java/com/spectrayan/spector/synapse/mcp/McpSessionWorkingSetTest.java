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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.synapse.mcp.McpSessionContext.ActiveWorkingItem;

@DisplayName("McpSessionContext Working Set Specifications")
class McpSessionWorkingSetTest {

    private static final String SESSION_1 = "sess-conn-1";
    private static final String SESSION_2 = "sess-conn-2";

    @AfterEach
    void tearDown() {
        McpSessionContext.clearSession(SESSION_1);
        McpSessionContext.clearSession(SESSION_2);
    }

    @Test
    @DisplayName("Working set items survive across namespace_switch")
    void workingSetSurvivesNamespaceSwitch() {
        McpSessionContext.setSessionDefault(SESSION_1, "project-alpha");
        McpSessionContext.addWorkingItem(SESSION_1, new ActiveWorkingItem("w-1", "Task context A", Map.of(), System.currentTimeMillis()));
        McpSessionContext.addWorkingItem(SESSION_1, new ActiveWorkingItem("w-2", "Task context B", Map.of(), System.currentTimeMillis()));

        assertThat(McpSessionContext.getSessionDefault(SESSION_1)).contains("project-alpha");
        assertThat(McpSessionContext.getWorkingItems(SESSION_1)).hasSize(2);

        // Perform namespace_switch to project-beta
        McpSessionContext.setSessionDefault(SESSION_1, "project-beta");

        assertThat(McpSessionContext.getSessionDefault(SESSION_1)).contains("project-beta");
        List<ActiveWorkingItem> items = McpSessionContext.getWorkingItems(SESSION_1);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).text()).isEqualTo("Task context A");
        assertThat(items.get(1).text()).isEqualTo("Task context B");
    }

    @Test
    @DisplayName("Working set enforces FIFO eviction at 100 capacity")
    void fifoEviction() {
        for (int i = 0; i < 110; i++) {
            McpSessionContext.addWorkingItem(SESSION_1, new ActiveWorkingItem("w-" + i, "Context " + i, Map.of(), System.currentTimeMillis()));
        }

        List<ActiveWorkingItem> items = McpSessionContext.getWorkingItems(SESSION_1);
        assertThat(items).hasSize(100);
        assertThat(items.get(0).id()).isEqualTo("w-10");
        assertThat(items.get(99).id()).isEqualTo("w-109");
    }

    @Test
    @DisplayName("Different sessions have isolated working sets")
    void sessionIsolation() {
        McpSessionContext.addWorkingItem(SESSION_1, new ActiveWorkingItem("w-1", "Session 1 item", Map.of(), System.currentTimeMillis()));
        McpSessionContext.addWorkingItem(SESSION_2, new ActiveWorkingItem("w-2", "Session 2 item", Map.of(), System.currentTimeMillis()));

        assertThat(McpSessionContext.getWorkingItems(SESSION_1)).hasSize(1);
        assertThat(McpSessionContext.getWorkingItems(SESSION_1).get(0).text()).isEqualTo("Session 1 item");

        assertThat(McpSessionContext.getWorkingItems(SESSION_2)).hasSize(1);
        assertThat(McpSessionContext.getWorkingItems(SESSION_2).get(0).text()).isEqualTo("Session 2 item");
    }
}
