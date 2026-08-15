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
package com.spectrayan.spector.connector.e2e;

import com.spectrayan.spector.connector.model.RouteConfig;
import com.spectrayan.spector.connector.spi.RouteConfigProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory route config provider for integration tests.
 *
 * <p>Stores route configurations in a ConcurrentHashMap — no database needed.
 * Useful for E2E tests that need to configure and deploy routes dynamically.</p>
 */
public class InMemoryRouteConfigProvider implements RouteConfigProvider {

    private final Map<String, RouteConfig> configs = new ConcurrentHashMap<>();

    @Override
    public Optional<RouteConfig> findById(String routeId) {
        return Optional.ofNullable(configs.get(routeId));
    }

    @Override
    public List<RouteConfig> findAll() {
        return List.copyOf(configs.values());
    }

    @Override
    public List<RouteConfig> findAllEnabled() {
        return configs.values().stream()
                .filter(RouteConfig::enabled)
                .toList();
    }

    @Override
    public List<RouteConfig> findByTenantId(String tenantId) {
        return configs.values().stream()
                .filter(c -> tenantId.equals(c.tenantId()))
                .toList();
    }

    @Override
    public void save(RouteConfig config) {
        configs.put(config.id(), config);
    }

    @Override
    public void delete(String routeId) {
        configs.remove(routeId);
    }

    /** Add a route config. Convenience alias for save(). */
    public void addRoute(RouteConfig config) {
        save(config);
    }

    /** Clear all stored configs. */
    public void clear() {
        configs.clear();
    }

    /** Returns the count of stored configs. */
    public int size() {
        return configs.size();
    }
}
