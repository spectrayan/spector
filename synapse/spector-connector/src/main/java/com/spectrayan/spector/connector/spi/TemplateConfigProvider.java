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
package com.spectrayan.spector.connector.spi;

import com.spectrayan.spector.connector.model.TemplateDescriptor;

import java.util.List;
import java.util.Optional;

/**
 * SPI for persisting and loading custom template descriptors.
 *
 * <p>Default implementation reads from the local filesystem
 * ({@code connectors/templates/}). Future implementations can
 * load from a database or remote config service.</p>
 */
public interface TemplateConfigProvider {

    /**
     * Returns all custom templates.
     */
    List<TemplateDescriptor> findAll();

    /**
     * Finds a custom template by its ID.
     */
    Optional<TemplateDescriptor> findByTemplateId(String templateId);

    /**
     * Saves or updates a custom template.
     *
     * @return the saved template
     */
    TemplateDescriptor save(TemplateDescriptor template);

    /**
     * Deletes a custom template by ID.
     */
    void deleteByTemplateId(String templateId);
}
