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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link RouteConfigProvider} for testing and development.
 */
public class InMemoryRouteConfigProvider implements RouteConfigProvider {

    private final Map<String, RouteConfig> configs = new ConcurrentHashMap<>();

    @Override
    public void save(RouteConfig config) {
        configs.put(config.id(), config);
    }

    @Override
    public void delete(String routeId) {
        configs.remove(routeId);
    }

    @Override
    public Optional<RouteConfig> findById(String routeId) {
        return Optional.ofNullable(configs.get(routeId));
    }

    @Override
    public List<RouteConfig> findByTenantId(String tenantId) {
        return configs.values().stream()
                .filter(c -> c.tenantId().equals(tenantId))
                .toList();
    }

    @Override
    public List<RouteConfig> findAllEnabled() {
        return configs.values().stream()
                .filter(RouteConfig::enabled)
                .toList();
    }

    @Override
    public List<RouteConfig> findAll() {
        return List.copyOf(configs.values());
    }
}
