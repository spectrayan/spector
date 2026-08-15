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

import com.spectrayan.spector.connector.model.RouteConfig;

import java.util.List;
import java.util.Optional;

/**
 * SPI for persisting and loading route configurations.
 *
 * <p>Implementations back this with a database (Postgres, MongoDB) or
 * in-memory storage for testing.</p>
 */
public interface RouteConfigProvider {

    /** Save or update a route configuration. */
    void save(RouteConfig config);

    /** Delete a route configuration. */
    void delete(String routeId);

    /** Load a route configuration by ID. */
    Optional<RouteConfig> findById(String routeId);

    /** Load all route configurations for a tenant. */
    List<RouteConfig> findByTenantId(String tenantId);

    /** Load all enabled route configurations. */
    List<RouteConfig> findAllEnabled();

    /** Load all route configurations. */
    List<RouteConfig> findAll();
}
