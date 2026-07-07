/*
 * Copyright 2025–2026 Spectrayan. Licensed under the Apache License, Version 2.0.
 */
package com.spectrayan.spector.synapse.channel.adapters;

import com.spectrayan.spector.synapse.channel.ChannelAdapter;
import com.spectrayan.spector.synapse.channel.model.UnifiedMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Slack channel adapter — sends and receives messages via Slack Events API / Web API.
 *
 * <p>Enabled when {@code spector.channels.slack.enabled=true} is set.
 * Requires a Slack Bot Token (xoxb-...) and signing secret.</p>
 */
@Component
@ConditionalOnProperty(name = "spector.channels.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackChannelAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(SlackChannelAdapter.class);

    @Override
    public String channelId() { return "slack"; }

    @Override
    public String displayName() { return "Slack"; }

    @Override
    public boolean isEnabled() { return true; }

    @Override
    public UnifiedMessage normalize(Object nativeMessage) {
        if (nativeMessage instanceof Map<?, ?> slackEvent) {
            // Slack Events API payload (message event)
            return new UnifiedMessage(
                    getOrDefault(slackEvent, "client_msg_id", java.util.UUID.randomUUID().toString()),
                    "slack",
                    getOrDefault(slackEvent, "user", "unknown"),
                    getOrDefault(slackEvent, "user_name", null),
                    getOrDefault(slackEvent, "text", ""),
                    parseSlackTimestamp(getOrDefault(slackEvent, "ts", null)),
                    getOrDefault(slackEvent, "thread_ts", getOrDefault(slackEvent, "channel", null)),
                    getOrDefault(slackEvent, "thread_ts", null),
                    parseSlackFiles(slackEvent),
                    Map.of(
                            "channel", getOrDefault(slackEvent, "channel", ""),
                            "team", getOrDefault(slackEvent, "team", ""),
                            "ts", getOrDefault(slackEvent, "ts", "")
                    )
            );
        }
        return UnifiedMessage.text("slack", "unknown", nativeMessage.toString());
    }

    @Override
    public void send(UnifiedMessage message) {
        // TODO: Wire to Slack Web API via HttpClient (chat.postMessage)
        log.info("[Slack] Sending to channel {} — {} chars", message.threadId(), message.content().length());
    }

    @Override
    public ChannelHealth health() {
        return new ChannelHealth("slack", true, "ready");
    }

    private static String getOrDefault(Map<?, ?> map, String key, String defaultVal) {
        var val = map.get(key);
        return val != null ? val.toString() : defaultVal;
    }

    private static java.time.Instant parseSlackTimestamp(String ts) {
        if (ts == null || ts.isBlank()) return java.time.Instant.now();
        try {
            // Slack timestamps are Unix epoch seconds with microsecond decimals: "1625000000.000100"
            double epochSecs = Double.parseDouble(ts);
            return java.time.Instant.ofEpochSecond((long) epochSecs);
        } catch (NumberFormatException _) {
            return java.time.Instant.now();
        }
    }

    private static List<UnifiedMessage.Attachment> parseSlackFiles(Map<?, ?> slackEvent) {
        // Slack files are in the "files" array
        return List.of();
    }
}
