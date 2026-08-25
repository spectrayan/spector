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
package com.spectrayan.spector.synapse.agent.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.synapse.channel.ChannelAdapter;
import com.spectrayan.spector.synapse.channel.ChannelRouter;

import io.modelcontextprotocol.spec.McpSchema;

class NotificationToolTest {

    private ChannelRouter channelRouter;
    private NotificationTool tool;

    @BeforeEach
    void setUp() {
        ChannelAdapter mockSlack = Mockito.mock(ChannelAdapter.class);
        Mockito.when(mockSlack.channelId()).thenReturn("slack");
        Mockito.when(mockSlack.isEnabled()).thenReturn(true);

        ChannelAdapter mockEmail = Mockito.mock(ChannelAdapter.class);
        Mockito.when(mockEmail.channelId()).thenReturn("email");
        Mockito.when(mockEmail.isEnabled()).thenReturn(true);

        channelRouter = new ChannelRouter(List.of(mockSlack, mockEmail));
        tool = new NotificationTool(channelRouter);
    }

    @Test
    void toolMetadataIsCorrect() {
        assertThat(tool.name()).isEqualTo("send_notification");
        assertThat(tool.description()).contains("messaging channel");
        assertThat(tool.category()).isEqualTo(McpToolHandler.McpToolCategory.NETWORK);
        assertThat(tool.requiredScopes()).contains("channel:write");
        assertThat(tool.inputSchema()).containsKey("properties");
    }

    @Test
    void executeRequiresMandatoryParameters() throws Exception {
        // Missing channel
        McpSchema.CallToolResult res1 = tool.execute(Map.of(
                "recipient", "dev-team",
                "message", "Build finished"
        ));
        assertThat(res1.isError()).isTrue();

        // Missing recipient
        McpSchema.CallToolResult res2 = tool.execute(Map.of(
                "channel", "slack",
                "message", "Build finished"
        ));
        assertThat(res2.isError()).isTrue();

        // Missing message
        McpSchema.CallToolResult res3 = tool.execute(Map.of(
                "channel", "slack",
                "recipient", "dev-team"
        ));
        assertThat(res3.isError()).isTrue();
    }

    @Test
    void executeFailsForUnregisteredChannel() throws Exception {
        McpSchema.CallToolResult res = tool.execute(Map.of(
                "channel", "unregistered_channel",
                "recipient", "user@example.com",
                "message", "Hello"
        ));
        assertThat(res.isError()).isTrue();
    }

    @Test
    void executeSucceedsForSlackNotification() throws Exception {
        McpSchema.CallToolResult res = tool.execute(Map.of(
                "channel", "slack",
                "recipient", "#general",
                "message", "Deployment succeeded"
        ));
        assertThat(res.isError()).isFalse();
    }

    @Test
    void executeSucceedsForEmailNotificationWithSubject() throws Exception {
        McpSchema.CallToolResult res = tool.execute(Map.of(
                "channel", "email",
                "recipient", "admin@spectrayan.com",
                "message", "Database backup completed.",
                "subject", "Daily Backup Success"
        ));
        assertThat(res.isError()).isFalse();
    }
}
