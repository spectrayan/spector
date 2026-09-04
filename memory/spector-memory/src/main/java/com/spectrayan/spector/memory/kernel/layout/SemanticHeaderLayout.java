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
 * Dedicated encoding header layout for the Semantic memory tier (ADR-0030).
 *
 * <p>Extends {@link SemanticProceduralHeaderLayout} to provide type identity
 * for semantic memory records.</p>
 *
 * @since 1.5.0
 * @see SemanticProceduralHeaderLayout
 */
public class SemanticHeaderLayout extends SemanticProceduralHeaderLayout {

    public static final SemanticHeaderLayout INSTANCE = new SemanticHeaderLayout();

    public SemanticHeaderLayout() {
        super();
    }

    public static SemanticHeaderLayout defaultLayout() {
        return INSTANCE;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SemanticHeaderLayout;
    }

    @Override
    public int hashCode() {
        return SemanticHeaderLayout.class.hashCode();
    }

    @Override
    public String toString() {
        return "SemanticHeaderLayout[]";
    }
}
