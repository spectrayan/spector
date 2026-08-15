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
import com.spectrayan.spector.synapse.channel.ChannelRouter;
import com.spectrayan.spector.synapse.channel.model.ChannelType;
import com.spectrayan.spector.synapse.channel.model.UnifiedMessage;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Agent MCP tool to send alerts and notifications through configured messaging channels.
 *
 * <p>Allows LLMs to deliver messages to Slack, Telegram, WhatsApp, Email, Discord,
 * and Webhooks when tasks finish, reminders trigger, or critical events occur.</p>
 */
@Component
public class NotificationTool extends McpToolHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationTool.class);

    private final ChannelRouter channelRouter;

    public NotificationTool(ChannelRouter channelRouter) {
        this.channelRouter = channelRouter;
    }

    @Override
    public String name() {
        return "send_notification";
    }

    @Override
    public String description() {
        return "Send an alert, message, or notification via a configured messaging channel (e.g. email, slack, telegram, whatsapp, discord).";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "channel", Map.of(
                                "type", "string",
                                "description", "Target channel identifier (e.g., 'email', 'slack', 'telegram', 'whatsapp', 'discord', 'sms')"
                        ),
                        "recipient", Map.of(
                                "type", "string",
                                "description", "Recipient address, username, channel name, or phone number"
                        ),
                        "message", Map.of(
                                "type", "string",
                                "description", "Notification text content"
                        ),
                        "subject", Map.of(
                                "type", "string",
                                "description", "Optional message subject (for email or rich notifications)"
                        )
                ),
                "required", List.of("channel", "recipient", "message")
        );
    }

    @Override
    public McpToolCategory category() {
        return McpToolCategory.NETWORK;
    }

    @Override
    public Set<String> requiredScopes() {
        return Set.of("channel:write");
    }

    @Override
    public McpSchema.CallToolResult execute(SpectorRuntime runtime, Map<String, Object> args) throws Exception {
        String channelId = (String) args.get("channel");
        String recipient = (String) args.get("recipient");
        String messageText = (String) args.get("message");
        String subject = (String) args.get("subject");

        if (channelId == null || channelId.isBlank()) {
            return errorResult("Parameter 'channel' is required.");
        }
        if (recipient == null || recipient.isBlank()) {
            return errorResult("Parameter 'recipient' is required.");
        }
        if (messageText == null || messageText.isBlank()) {
            return errorResult("Parameter 'message' is required.");
        }

        var channelOpt = ChannelType.fromId(channelId);
        String resolvedChannelId = channelOpt.map(ChannelType::id).orElse(channelId.trim().toLowerCase());

        var adapterOpt = channelRouter.adapter(resolvedChannelId);
        if (adapterOpt.isEmpty()) {
            return errorResult("Channel '" + resolvedChannelId + "' is not registered or supported. Available channels: "
                    + channelRouter.channelIds());
        }

        var adapter = adapterOpt.get();
        if (!adapter.isEnabled()) {
            return errorResult("Channel '" + resolvedChannelId + "' is disabled in configuration.");
        }

        Map<String, String> metadata = new HashMap<>();
        if (subject != null && !subject.isBlank()) {
            metadata.put("subject", subject);
        }
        metadata.put("source", "NotificationTool");

        UnifiedMessage unifiedMessage = new UnifiedMessage(
                UUID.randomUUID().toString(),
                resolvedChannelId,
                recipient,
                null,
                messageText,
                Instant.now(),
                recipient,
                null,
                List.of(),
                Map.copyOf(metadata)
        );

        try {
            channelRouter.routeOutbound(unifiedMessage);
            log.info("[NotificationTool] Delivered notification via {} to {}", resolvedChannelId, recipient);
            return textResult("Notification sent successfully via " + adapter.displayName() + " to " + recipient + ".");
        } catch (Exception e) {
            log.error("[NotificationTool] Failed to deliver notification via {}: {}", resolvedChannelId, e.getMessage(), e);
            return errorResult("Failed to send notification via " + resolvedChannelId + ": " + e.getMessage());
        }
    }
}
