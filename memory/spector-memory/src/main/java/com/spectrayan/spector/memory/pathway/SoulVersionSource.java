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
package com.spectrayan.spector.memory.pathway;

/**
 * Accessor source providing the current soul / persona version.
 *
 * <p>Mutable at runtime via persona evolution and soul-drift updates. Bound into
 * {@link com.spectrayan.spector.commons.pathway.PathwayContext} as an accessor,
 * not a fixed snapshot value, ensuring downstream pathways (such as Reflect and Dream)
 * observe live version transitions across the application lifecycle.</p>
 */
@FunctionalInterface
public interface SoulVersionSource {

    /**
     * Returns the current soul / persona version.
     *
     * @return current soul version
     */
    short currentSoulVersion();
}
