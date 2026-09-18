/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.synapse.channel.adapters;

import com.spectrayan.spector.connector.core.CamelConnectorEngine;
import com.spectrayan.spector.synapse.channel.config.ChannelProperties;
import com.spectrayan.spector.synapse.channel.model.ChannelType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * WebChat channel adapter — dispatches messages for Cortex UI via SSE/WebSocket and Camel.
 */
@Component
@ConditionalOnProperty(name = "spector.channels.webchat.enabled", havingValue = "true", matchIfMissing = true)
public class WebChatChannelAdapter extends CamelChannelAdapter {

    @Autowired
    public WebChatChannelAdapter(ChannelProperties properties,
                                 @Autowired(required = false) CamelConnectorEngine connectorEngine) {
        super(ChannelType.WEBCHAT, properties, connectorEngine);
    }

    public WebChatChannelAdapter(ChannelProperties properties) {
        this(properties, null);
    }

    public WebChatChannelAdapter() {
        this(new ChannelProperties(), null);
    }
}
