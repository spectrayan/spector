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
 * Email channel adapter — dispatches messages via Camel route templates.
 */
@Component
@ConditionalOnProperty(name = "spector.channels.email.enabled", havingValue = "true", matchIfMissing = true)
public class EmailChannelAdapter extends CamelChannelAdapter {

    @Autowired
    public EmailChannelAdapter(ChannelProperties properties,
                               @Autowired(required = false) CamelConnectorEngine connectorEngine) {
        super(ChannelType.EMAIL, properties, connectorEngine);
    }

    public EmailChannelAdapter(ChannelProperties properties) {
        this(properties, null);
    }

    public EmailChannelAdapter() {
        this(new ChannelProperties(), null);
    }
}
