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
package com.spectrayan.spector.synapse.channel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Consolidated type-safe configuration properties for all Spector messaging channels.
 */
@ConfigurationProperties(prefix = "spector.channels")
public class ChannelProperties {

    private boolean enabled = true;
    private WebChatConfig webchat = new WebChatConfig();
    private SlackConfig slack = new SlackConfig();
    private TelegramConfig telegram = new TelegramConfig();
    private WhatsAppConfig whatsapp = new WhatsAppConfig();
    private DiscordConfig discord = new DiscordConfig();
    private EmailConfig email = new EmailConfig();
    private MSTeamsConfig msteams = new MSTeamsConfig();
    private GoogleChatConfig googlechat = new GoogleChatConfig();
    private SignalConfig signal = new SignalConfig();
    private SmsConfig sms = new SmsConfig();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public WebChatConfig getWebchat() { return webchat; }
    public void setWebchat(WebChatConfig webchat) { this.webchat = webchat; }

    public SlackConfig getSlack() { return slack; }
    public void setSlack(SlackConfig slack) { this.slack = slack; }

    public TelegramConfig getTelegram() { return telegram; }
    public void setTelegram(TelegramConfig telegram) { this.telegram = telegram; }

    public WhatsAppConfig getWhatsapp() { return whatsapp; }
    public void setWhatsapp(WhatsAppConfig whatsapp) { this.whatsapp = whatsapp; }

    public DiscordConfig getDiscord() { return discord; }
    public void setDiscord(DiscordConfig discord) { this.discord = discord; }

    public EmailConfig getEmail() { return email; }
    public void setEmail(EmailConfig email) { this.email = email; }

    public MSTeamsConfig getMsteams() { return msteams; }
    public void setMsteams(MSTeamsConfig msteams) { this.msteams = msteams; }

    public GoogleChatConfig getGooglechat() { return googlechat; }
    public void setGooglechat(GoogleChatConfig googlechat) { this.googlechat = googlechat; }

    public SignalConfig getSignal() { return signal; }
    public void setSignal(SignalConfig signal) { this.signal = signal; }

    public SmsConfig getSms() { return sms; }
    public void setSms(SmsConfig sms) { this.sms = sms; }

    public static class WebChatConfig {
        private boolean enabled = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class SlackConfig {
        private boolean enabled = true;
        private String botToken;
        private String signingSecret;
        private String defaultChannel;
        private String webhookUrl;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBotToken() { return botToken; }
        public void setBotToken(String botToken) { this.botToken = botToken; }
        public String getSigningSecret() { return signingSecret; }
        public void setSigningSecret(String signingSecret) { this.signingSecret = signingSecret; }
        public String getDefaultChannel() { return defaultChannel; }
        public void setDefaultChannel(String defaultChannel) { this.defaultChannel = defaultChannel; }
        public String getWebhookUrl() { return webhookUrl; }
        public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    }

    public static class TelegramConfig {
        private boolean enabled = true;
        private String botToken;
        private String webhookUrl;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBotToken() { return botToken; }
        public void setBotToken(String botToken) { this.botToken = botToken; }
        public String getWebhookUrl() { return webhookUrl; }
        public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    }

    public static class WhatsAppConfig {
        private boolean enabled = true;
        private String apiToken;
        private String phoneNumberId;
        private String verifyToken;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getApiToken() { return apiToken; }
        public void setApiToken(String apiToken) { this.apiToken = apiToken; }
        public String getPhoneNumberId() { return phoneNumberId; }
        public void setPhoneNumberId(String phoneNumberId) { this.phoneNumberId = phoneNumberId; }
        public String getVerifyToken() { return verifyToken; }
        public void setVerifyToken(String verifyToken) { this.verifyToken = verifyToken; }
    }

    public static class DiscordConfig {
        private boolean enabled = true;
        private String botToken;
        private String defaultChannelId;
        private String guildId;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBotToken() { return botToken; }
        public void setBotToken(String botToken) { this.botToken = botToken; }
        public String getDefaultChannelId() { return defaultChannelId; }
        public void setDefaultChannelId(String defaultChannelId) { this.defaultChannelId = defaultChannelId; }
        public String getGuildId() { return guildId; }
        public void setGuildId(String guildId) { this.guildId = guildId; }
    }

    public static class EmailConfig {
        private boolean enabled = true;
        private String host;
        private int port = 587;
        private String username;
        private String password;
        private String fromAddress;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getFromAddress() { return fromAddress; }
        public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }
    }

    public static class MSTeamsConfig {
        private boolean enabled = true;
        private String appId;
        private String appPassword;
        private String tenantId;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getAppPassword() { return appPassword; }
        public void setAppPassword(String appPassword) { this.appPassword = appPassword; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    }

    public static class GoogleChatConfig {
        private boolean enabled = true;
        private String webhookUrl;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getWebhookUrl() { return webhookUrl; }
        public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    }

    public static class SignalConfig {
        private boolean enabled = true;
        private String httpEndpoint;
        private String sourceNumber;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getHttpEndpoint() { return httpEndpoint; }
        public void setHttpEndpoint(String httpEndpoint) { this.httpEndpoint = httpEndpoint; }
        public String getSourceNumber() { return sourceNumber; }
        public void setSourceNumber(String sourceNumber) { this.sourceNumber = sourceNumber; }
    }

    public static class SmsConfig {
        private boolean enabled = true;
        private String accountSid;
        private String authToken;
        private String fromNumber;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAccountSid() { return accountSid; }
        public void setAccountSid(String accountSid) { this.accountSid = accountSid; }
        public String getAuthToken() { return authToken; }
        public void setAuthToken(String authToken) { this.authToken = authToken; }
        public String getFromNumber() { return fromNumber; }
        public void setFromNumber(String fromNumber) { this.fromNumber = fromNumber; }
    }
}
