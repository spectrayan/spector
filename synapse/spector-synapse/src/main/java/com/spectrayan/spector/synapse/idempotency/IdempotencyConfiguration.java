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
package com.spectrayan.spector.synapse.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.cluster.idempotency.IdempotencyPersistenceBackend;
import com.spectrayan.spector.cluster.idempotency.IdempotencyStore;
import com.spectrayan.spector.cluster.idempotency.InMemoryIdempotencyStore;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;

/**
 * Spring configuration wiring the {@link IdempotencyStore}, durable JDBC persistence backend,
 * and {@link IdempotencyFilter} for owner and standalone Synapse deployments.
 */
@Configuration
public class IdempotencyConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyPersistenceBackend idempotencyPersistenceBackend(
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider
    ) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return null;
        }
        return new JdbcIdempotencyPersistenceBackend(jdbcTemplate, objectMapperProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyStore idempotencyStore(
            SynapseProperties properties,
            ObjectProvider<IdempotencyPersistenceBackend> persistenceBackendProvider
    ) {
        IdempotencyProperties idemProps = properties.getIdempotency() != null
                ? properties.getIdempotency()
                : new IdempotencyProperties();
        long ttlSeconds = Math.max(1L, idemProps.getTtlSeconds());
        int maxEntries = Math.max(1, idemProps.getMaxEntries());
        return new InMemoryIdempotencyStore(
                Duration.ofSeconds(ttlSeconds),
                maxEntries,
                Clock.systemUTC(),
                persistenceBackendProvider.getIfAvailable()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyFilter idempotencyFilter(
            SynapseProperties properties,
            IdempotencyStore idempotencyStore,
            ObjectProvider<ObjectMapper> objectMapperProvider
    ) {
        return new IdempotencyFilter(properties, idempotencyStore, objectMapperProvider.getIfAvailable());
    }
}
