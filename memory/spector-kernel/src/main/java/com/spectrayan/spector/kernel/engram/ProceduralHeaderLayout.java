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
package com.spectrayan.spector.kernel.engram;

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
