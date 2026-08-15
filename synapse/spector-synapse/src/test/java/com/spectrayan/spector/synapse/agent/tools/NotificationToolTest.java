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

import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.runtime.SpectorRuntime;
import com.spectrayan.spector.synapse.channel.ChannelAdapter;
import com.spectrayan.spector.synapse.channel.ChannelRouter;
import com.spectrayan.spector.synapse.channel.adapters.EmailChannelAdapter;
import com.spectrayan.spector.synapse.channel.adapters.SlackChannelAdapter;
import com.spectrayan.spector.synapse.channel.config.ChannelProperties;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationToolTest {

    private NotificationTool tool;
    private ChannelRouter channelRouter;
    private ChannelProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ChannelProperties();
        properties.getSlack().setEnabled(true);
        properties.getEmail().setEnabled(true);

        ChannelAdapter slackAdapter = new SlackChannelAdapter(properties);
        ChannelAdapter emailAdapter = new EmailChannelAdapter(properties);

        channelRouter = new ChannelRouter(List.of(slackAdapter, emailAdapter));
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
        SpectorRuntime runtime = Mockito.mock(SpectorRuntime.class);

        // Missing channel
        McpSchema.CallToolResult res1 = tool.execute(runtime, Map.of(
                "recipient", "dev-team",
                "message", "Build finished"
        ));
        assertThat(res1.isError()).isTrue();

        // Missing recipient
        McpSchema.CallToolResult res2 = tool.execute(runtime, Map.of(
                "channel", "slack",
                "message", "Build finished"
        ));
        assertThat(res2.isError()).isTrue();

        // Missing message
        McpSchema.CallToolResult res3 = tool.execute(runtime, Map.of(
                "channel", "slack",
                "recipient", "dev-team"
        ));
        assertThat(res3.isError()).isTrue();
    }

    @Test
    void executeFailsForUnregisteredChannel() throws Exception {
        SpectorRuntime runtime = Mockito.mock(SpectorRuntime.class);

        McpSchema.CallToolResult res = tool.execute(runtime, Map.of(
                "channel", "unregistered_channel",
                "recipient", "user@example.com",
                "message", "Hello"
        ));
        assertThat(res.isError()).isTrue();
    }

    @Test
    void executeSucceedsForSlackNotification() throws Exception {
        SpectorRuntime runtime = Mockito.mock(SpectorRuntime.class);

        McpSchema.CallToolResult res = tool.execute(runtime, Map.of(
                "channel", "slack",
                "recipient", "#general",
                "message", "Deployment succeeded"
        ));
        assertThat(res.isError()).isFalse();
    }

    @Test
    void executeSucceedsForEmailNotificationWithSubject() throws Exception {
        SpectorRuntime runtime = Mockito.mock(SpectorRuntime.class);

        McpSchema.CallToolResult res = tool.execute(runtime, Map.of(
                "channel", "email",
                "recipient", "admin@spectrayan.com",
                "message", "Database backup completed.",
                "subject", "Daily Backup Success"
        ));
        assertThat(res.isError()).isFalse();
    }
}
