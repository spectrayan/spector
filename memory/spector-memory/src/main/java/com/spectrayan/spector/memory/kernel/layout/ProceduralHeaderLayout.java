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
 * Dedicated encoding header layout for the Procedural memory tier (ADR-0030).
 *
 * <p>Extends {@link SemanticProceduralHeaderLayout} to provide type identity
 * for procedural skill engrams.</p>
 *
 * @since 1.5.0
 * @see SemanticProceduralHeaderLayout
 */
public class ProceduralHeaderLayout extends SemanticProceduralHeaderLayout {

    public static final ProceduralHeaderLayout INSTANCE = new ProceduralHeaderLayout();

    public ProceduralHeaderLayout() {
        super();
    }

    public static ProceduralHeaderLayout defaultLayout() {
        return INSTANCE;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ProceduralHeaderLayout;
    }

    @Override
    public int hashCode() {
        return ProceduralHeaderLayout.class.hashCode();
    }

    @Override
    public String toString() {
        return "ProceduralHeaderLayout[]";
    }
}
