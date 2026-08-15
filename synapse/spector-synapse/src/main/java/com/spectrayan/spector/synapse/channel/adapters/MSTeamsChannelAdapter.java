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
package com.spectrayan.spector.synapse.channel.adapters;

import com.spectrayan.spector.connector.core.CamelConnectorEngine;
import com.spectrayan.spector.synapse.channel.config.ChannelProperties;
import com.spectrayan.spector.synapse.channel.model.ChannelType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Microsoft Teams channel adapter — dispatches messages via Camel route templates.
 */
@Component
@ConditionalOnProperty(name = "spector.channels.msteams.enabled", havingValue = "true", matchIfMissing = true)
public class MSTeamsChannelAdapter extends CamelChannelAdapter {

    @Autowired
    public MSTeamsChannelAdapter(ChannelProperties properties,
                                 @Autowired(required = false) CamelConnectorEngine connectorEngine) {
        super(ChannelType.MSTEAMS, properties, connectorEngine);
    }

    public MSTeamsChannelAdapter(ChannelProperties properties) {
        this(properties, null);
    }

    public MSTeamsChannelAdapter() {
        this(new ChannelProperties(), null);
    }
}
