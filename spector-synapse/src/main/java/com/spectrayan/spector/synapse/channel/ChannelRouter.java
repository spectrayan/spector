/*
 * Copyright 2025–2026 Spectrayan. Licensed under the Apache License, Version 2.0.
 */
package com.spectrayan.spector.synapse.channel;

import com.spectrayan.spector.synapse.channel.model.UnifiedMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes messages between channels and the agent orchestration layer.
 *
 * <p>All {@link ChannelAdapter} beans are auto-discovered by Spring and
 * registered here. Inbound messages are normalized to {@link UnifiedMessage}
 * and dispatched to the agent. Outbound responses are routed back through
 * the originating channel.</p>
 */
@Component
public class ChannelRouter {

    private static final Logger log = LoggerFactory.getLogger(ChannelRouter.class);

    private final Map<String, ChannelAdapter> adapters = new ConcurrentHashMap<>();

    /**
     * Auto-wired constructor — registers all channel adapter beans.
     */
    public ChannelRouter(List<ChannelAdapter> channelAdapters) {
        channelAdapters.forEach(adapter -> {
            adapters.put(adapter.channelId(), adapter);
            log.info("📡 Registered channel adapter: {} ({})", adapter.displayName(),
                    adapter.isEnabled() ? "enabled" : "disabled");
        });
        log.info("⚡ ChannelRouter: {} channels registered", adapters.size());
    }

    /** Look up a channel adapter by ID. */
    public Optional<ChannelAdapter> adapter(String channelId) {
        return Optional.ofNullable(adapters.get(channelId));
    }

    /** Get all registered channel IDs. */
    public java.util.Set<String> channelIds() {
        return Collections.unmodifiableSet(adapters.keySet());
    }

    /** Get all enabled channel adapters. */
    public List<ChannelAdapter> enabledAdapters() {
        return adapters.values().stream()
                .filter(ChannelAdapter::isEnabled)
                .toList();
    }

    /**
     * Route an inbound message from a specific channel to the agent.
     *
     * @param channelId     the source channel
     * @param nativeMessage the raw message from the channel API
     * @return the normalized unified message
     */
    public UnifiedMessage routeInbound(String channelId, Object nativeMessage) {
        var adapter = adapters.get(channelId);
        if (adapter == null) {
            throw new ChannelAdapter.ChannelException(channelId, "No adapter registered for channel: " + channelId);
        }
        if (!adapter.isEnabled()) {
            throw new ChannelAdapter.ChannelException(channelId, "Channel is disabled: " + channelId);
        }
        return adapter.normalize(nativeMessage);
    }

    /**
     * Route an outbound response back through the originating channel.
     *
     * @param message the response message to send
     */
    public void routeOutbound(UnifiedMessage message) {
        var adapter = adapters.get(message.channel());
        if (adapter == null) {
            log.warn("No adapter for channel '{}' — dropping outbound message", message.channel());
            return;
        }
        adapter.send(message);
    }

    /** Get health status of all channels. */
    public List<ChannelAdapter.ChannelHealth> healthStatus() {
        return adapters.values().stream()
                .map(ChannelAdapter::health)
                .toList();
    }

    /** Number of registered channels. */
    public int size() { return adapters.size(); }
}
