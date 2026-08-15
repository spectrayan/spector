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
package com.spectrayan.spector.synapse.channel;

import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.AgentChatResponse;
import com.spectrayan.spector.synapse.agent.chat.service.ChatService;
import com.spectrayan.spector.synapse.channel.model.ChannelType;
import com.spectrayan.spector.synapse.channel.model.UnifiedMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central routing hub for all inbound and outbound channel messages.
 *
 * <p>Dispatches incoming messages to appropriate {@link ChannelAdapter} instances,
 * routes normalized messages to {@link ChatService} for agent cognition, and sends
 * agent replies back out to the originating channel.</p>
 */
@Component
public class ChannelRouter {

    private static final Logger log = LoggerFactory.getLogger(ChannelRouter.class);

    private final Map<String, ChannelAdapter> adapters = new ConcurrentHashMap<>();
    private final ObjectProvider<ChatService> chatServiceProvider;

    @Autowired
    public ChannelRouter(List<ChannelAdapter> adapterList,
                         ObjectProvider<ChatService> chatServiceProvider) {
        this.chatServiceProvider = chatServiceProvider;
        if (adapterList != null) {
            for (ChannelAdapter adapter : adapterList) {
                register(adapter);
            }
        }
        log.info("[ChannelRouter] Initialized with {} channel adapter(s): {}",
                adapters.size(), adapters.keySet());
    }

    public ChannelRouter(List<ChannelAdapter> adapterList, ChatService chatService) {
        this(adapterList, new ObjectProvider<>() {
            @Override public ChatService getObject() { return chatService; }
            @Override public ChatService getIfAvailable() { return chatService; }
            @Override public ChatService getIfUnique() { return chatService; }
            @Override public ChatService getObject(Object... args) { return chatService; }
        });
    }

    public ChannelRouter(List<ChannelAdapter> adapterList) {
        this(adapterList, (ObjectProvider<ChatService>) null);
    }

    public void register(ChannelAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter cannot be null");
        String key = normalizeKey(adapter.channelId());
        adapters.put(key, adapter);
        log.debug("[ChannelRouter] Registered adapter for channel '{}' ({})",
                adapter.channelId(), adapter.displayName());
    }

    public Optional<ChannelAdapter> adapter(String channelId) {
        if (channelId == null) return Optional.empty();
        return Optional.ofNullable(adapters.get(normalizeKey(channelId)));
    }

    public Optional<ChannelAdapter> adapter(ChannelType channelType) {
        if (channelType == null) return Optional.empty();
        return adapter(channelType.id());
    }

    public java.util.Set<String> channelIds() {
        return Collections.unmodifiableSet(adapters.keySet());
    }

    public List<ChannelAdapter> enabledAdapters() {
        return adapters.values().stream()
                .filter(ChannelAdapter::isEnabled)
                .toList();
    }

    public int size() {
        return adapters.size();
    }

    public UnifiedMessage routeInbound(String channelId, Object nativeMessage) {
        ChannelAdapter ad = adapter(channelId).orElseThrow(() ->
                new ChannelAdapter.ChannelException(channelId, "No adapter registered for channel: " + channelId));

        if (!ad.isEnabled()) {
            throw new ChannelAdapter.ChannelException(channelId, "Channel is currently disabled: " + channelId);
        }

        UnifiedMessage msg = ad.normalize(nativeMessage);
        log.info("[ChannelRouter] Inbound normalized: [{}] sender={}, thread={}, {} chars",
                msg.channel(), msg.senderId(), msg.threadId(),
                msg.content() != null ? msg.content().length() : 0);
        return msg;
    }

    public UnifiedMessage routeInboundAndProcess(String channelId, Object nativeMessage) {
        UnifiedMessage inbound = routeInbound(channelId, nativeMessage);

        ChatService chatService = chatServiceProvider != null ? chatServiceProvider.getIfAvailable() : null;
        if (chatService == null) {
            log.warn("[ChannelRouter] ChatService is not available; returning inbound message without execution");
            return inbound;
        }

        String sessionId = inbound.threadId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        log.info("[ChannelRouter] Executing agent chat turn for [{}] session={}", channelId, sessionId);
        try {
            AgentChatResponse response = chatService.executeChat(
                    inbound.content(),
                    sessionId,
                    null,
                    null,
                    20,
                    null,
                    null
            );

            String replyContent = response != null && response.response() != null
                    ? response.response()
                    : "";

            UnifiedMessage reply = UnifiedMessage.reply(inbound, replyContent);
            routeOutbound(reply);
            return reply;
        } catch (Exception e) {
            log.error("[ChannelRouter] Failed to process message turn for channel {}: {}",
                    channelId, e.getMessage(), e);
            throw new ChannelAdapter.ChannelException(channelId, "Agent turn processing failed: " + e.getMessage(), e);
        }
    }

    public void routeOutbound(UnifiedMessage message) {
        Objects.requireNonNull(message, "message cannot be null");
        ChannelAdapter ad = adapter(message.channel()).orElseThrow(() ->
                new ChannelAdapter.ChannelException(message.channel(),
                        "No adapter registered for outbound channel: " + message.channel()));

        log.info("[ChannelRouter] Outbound dispatch: [{}] to={}, thread={}",
                message.channel(), message.senderId(), message.threadId());
        ad.send(message);
    }

    public List<ChannelAdapter.ChannelHealth> healthStatus() {
        return adapters.values().stream()
                .map(ChannelAdapter::health)
                .toList();
    }

    private String normalizeKey(String channelId) {
        return channelId != null ? channelId.trim().toLowerCase() : "";
    }
}
