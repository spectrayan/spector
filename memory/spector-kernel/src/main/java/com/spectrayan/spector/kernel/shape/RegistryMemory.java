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
package com.spectrayan.spector.kernel.shape;

import com.spectrayan.spector.kernel.layout.RegistryLayout;
import com.spectrayan.spector.kernel.shape.Memory;

/**
 * Shape interface for small string-to-int dictionaries.
 * Backs TypeRegistry, name resolution, etc.
 */
public interface RegistryMemory extends Memory<RegistryLayout> {
    /**
     * Interns a string name, returning its stable integer ID.
     * If the name is already known, returns the existing ID.
     * Names are case-insensitive and normalized to uppercase.
     * @param name the string to intern
     * @return the integer ID
     */
    int intern(String name);
    
    /**
     * Directly inserts a name-to-ID mapping without allocating a new ID.
     * Used for loading/migration.
     */
    void putDirect(String name, int id);

    /**
     * Returns the string name for the given integer ID.
     * @param id the integer ID
     * @return the name, or null if not found
     */
    String nameOf(int id);

    /**
     * Returns the integer ID for the given name if registered, or -1 otherwise.
     * @param name the string name
     * @return the integer ID, or -1 if not registered
     */
    int idOf(String name);

    /**
     * Returns an unmodifiable map of all registered name-to-ID mappings.
     * @return map of name to integer ID
     */
    java.util.Map<String, Integer> entries();
}
