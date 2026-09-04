/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.kernel.layout;

/**
 * Dedicated encoding header layout for the Working memory tier (ADR-0030).
 *
 * <p>Extends {@link EncodingHeaderLayout} to provide type identity for working
 * memory ring-buffer entries.</p>
 *
 * @since 1.5.0
 * @see EncodingHeaderLayout
 */
public class WorkingHeaderLayout extends EncodingHeaderLayout {

    public static final WorkingHeaderLayout INSTANCE = new WorkingHeaderLayout();

    public WorkingHeaderLayout() {
        super();
    }

    public static WorkingHeaderLayout defaultLayout() {
        return INSTANCE;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof WorkingHeaderLayout;
    }

    @Override
    public int hashCode() {
        return WorkingHeaderLayout.class.hashCode();
    }

    @Override
    public String toString() {
        return "WorkingHeaderLayout[]";
    }
}
