/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.config.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.cluster.OwnershipResolver;
import com.spectrayan.spector.cluster.routing.ConsistentHashRing;
import com.spectrayan.spector.cluster.routing.RoutingMetricsListener;
import com.spectrayan.spector.cluster.routing.cache.LettuceRedisRoutingCache;
import com.spectrayan.spector.cluster.routing.cache.RedisRoutingCache;
import com.spectrayan.spector.cluster.routing.cache.WaterfallRoutingResolver;
import com.spectrayan.spector.cluster.routing.invalidation.RoutingInvalidationSubscriber;
import com.spectrayan.spector.synapse.cluster.gateway.GatewayForwarder;
import com.spectrayan.spector.synapse.cluster.gateway.GatewayForwardingFilter;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Spring auto-configuration for the three-tier routing cache, pub/sub invalidation bus,
 * and gateway forwarder (ADR-0034 §8, Req R1, R3, R5, R7).
 *
 * <p>Gated on {@link NonStandaloneCondition} to ensure zero cluster infrastructure
 * is allocated in standalone mode (G43).</p>
 */
@Configuration
@Conditional(ClusterRoutingConfiguration.NonStandaloneCondition.class)
public class ClusterRoutingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ClusterRoutingConfiguration.class);

    public static class NonStandaloneCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String role = context.getEnvironment().getProperty("spector.cell.role");
            if (role == null || role.isBlank()) {
                return false; // Standalone by default
            }
            return !"standalone".equalsIgnoreCase(role.trim());
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public RoutingMetricsListener routingMetricsListener(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return RoutingMetricsListener.NOOP;
        }
        return source -> registry.counter("spector.route.lookup", "tier", source.name().toLowerCase()).increment();
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public RedisClient redisClient(SynapseProperties properties) {
        RoutingProperties routingProps = properties.getRouting();
        if (routingProps != null && routingProps.getRedis() != null && routingProps.getRedis().isEnabled()) {
            String redisUri = routingProps.getRedis().getUri();
            long timeoutMs = Math.max(10L, routingProps.getRedis().getTimeoutMs());
            RedisURI uri = RedisURI.create(redisUri);
            uri.setTimeout(Duration.ofMillis(timeoutMs));

            RedisClient client = RedisClient.create(uri);
            client.setOptions(ClientOptions.builder()
                    .autoReconnect(true)
                    .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofMillis(timeoutMs)).build())
                    .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(timeoutMs)))
                    .build());
            return client;
        }
        return null;
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public RedisRoutingCache redisRoutingCache(
            SynapseProperties properties,
            ObjectProvider<RedisClient> redisClientProvider,
            RoutingMetricsListener metricsListener
    ) {
        RoutingProperties routingProps = properties.getRouting();
        RedisClient client = redisClientProvider.getIfAvailable();
        if (client != null && routingProps != null && routingProps.getRedis() != null && routingProps.getRedis().isEnabled()) {
            log.info("Initializing Lettuce Redis routing cache at {}", routingProps.getRedis().getUri());
            return new LettuceRedisRoutingCache(
                    client,
                    routingProps.getRedis().getUri(),
                    routingProps.getRedis().getTimeoutMs(),
                    metricsListener
            );
        }
        return null;
    }

    @Bean
    @ConditionalOnMissingBean
    public WaterfallRoutingResolver waterfallRoutingResolver(
            SynapseProperties properties,
            ObjectProvider<OwnershipResolver> ownershipResolverProvider,
            ObjectProvider<RedisRoutingCache> redisCacheProvider,
            RoutingMetricsListener metricsListener
    ) {
        OwnershipResolver ownershipResolver = ownershipResolverProvider.getIfAvailable();
        if (ownershipResolver == null) {
            throw new IllegalStateException("OwnershipResolver must be present for non-standalone routing (G39)");
        }
        Supplier<ConsistentHashRing> ringSupplier = () -> ownershipResolver.ring()
                .orElseThrow(() -> new IllegalStateException("Non-standalone node has no active ring (G39)"));

        RedisRoutingCache redisCache = redisCacheProvider.getIfAvailable();
        RoutingProperties routingProps = properties.getRouting() != null ? properties.getRouting() : new RoutingProperties();

        Duration caffeineTtl = Duration.ofSeconds(routingProps.getCaffeine().getTtlSeconds());
        long caffeineMaxSize = routingProps.getCaffeine().getMaxSize();
        long redisTtl = routingProps.getRedis().getTtlSeconds();

        return new WaterfallRoutingResolver(
                ringSupplier,
                redisCache,
                caffeineTtl,
                caffeineMaxSize,
                redisTtl,
                metricsListener,
                null // default fork join pool
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public GatewayForwarder gatewayForwarder(
            SynapseProperties properties,
            WaterfallRoutingResolver resolver
    ) {
        RoutingProperties routingProps = properties.getRouting() != null ? properties.getRouting() : new RoutingProperties();
        int retryMax = routingProps.getGateway().getRetryMax();
        return new GatewayForwarder(resolver, retryMax, null, null);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public RoutingInvalidationSubscriber routingInvalidationSubscriber(
            SynapseProperties properties,
            ObjectProvider<RedisClient> redisClientProvider,
            WaterfallRoutingResolver resolver,
            RoutingMetricsListener metricsListener
    ) {
        RoutingProperties routingProps = properties.getRouting();
        RedisClient client = redisClientProvider.getIfAvailable();
        if (client != null && routingProps != null && routingProps.getRedis() != null && routingProps.getRedis().isEnabled()) {
            String cellId = properties.getCell() != null ? properties.getCell().getId() : "default";
            try {
                RoutingInvalidationSubscriber subscriber = new RoutingInvalidationSubscriber(
                        cellId,
                        client,
                        resolver,
                        metricsListener
                );
                subscriber.start();
                return subscriber;
            } catch (Exception e) {
                log.warn("Failed to create RoutingInvalidationSubscriber: {}", e.getMessage());
            }
        }
        return null;
    }

    @Bean
    @ConditionalOnMissingBean
    public GatewayForwardingFilter gatewayForwardingFilter(
            SynapseProperties properties,
            GatewayForwarder forwarder,
            ObjectProvider<ObjectMapper> objectMapperProvider
    ) {
        ObjectMapper mapper = objectMapperProvider.getIfAvailable();
        return new GatewayForwardingFilter(properties, forwarder, mapper);
    }
}
