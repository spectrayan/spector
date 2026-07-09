/*
 * Copyright 2025–2026 Spectrayan. Licensed under the Apache License, Version 2.0.
 */
package com.spectrayan.spector.synapse.platform.events;

import org.springframework.context.ApplicationEvent;

/**
 * Event wrapper for publishing internal synapse events via Spring ApplicationEventPublisher.
 */
public class SpectorEvent extends ApplicationEvent {
    private final String eventType;
    private final Object data;

    public SpectorEvent(Object source, String eventType, Object data) {
        super(source);
        this.eventType = eventType;
        this.data = data;
    }

    public String getEventType() {
        return eventType;
    }

    public Object getData() {
        return data;
    }
}
