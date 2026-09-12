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

import com.spectrayan.spector.cluster.OwnershipResolver;
import com.spectrayan.spector.cluster.routing.ConsistentHashRing;
import com.spectrayan.spector.cluster.routing.RoutingMetricsListener;
import com.spectrayan.spector.cluster.routing.cache.LettuceRedisRoutingCache;
import com.spectrayan.spector.cluster.routing.cache.RedisRoutingCache;
import com.spectrayan.spector.cluster.routing.cache.WaterfallRoutingResolver;
import com.spectrayan.spector.cluster.routing.invalidation.RoutingInvalidationSubscriber;
import com.spectrayan.spector.synapse.cluster.gateway.GatewayForwarder;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Spring auto-configuration for the three-tier routing cache, pub/sub invalidation bus,
 * and gateway forwarder (ADR-0034 §8, Req R1, R3, R5, R7).
 */
@Configuration
public class ClusterRoutingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ClusterRoutingConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public RoutingMetricsListener routingMetricsListener(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return RoutingMetricsListener.NOOP;
        }
        return source -> registry.counter("spector.route.lookup", "tier", source.name().toLowerCase()).increment();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public RedisRoutingCache redisRoutingCache(
            SynapseProperties properties,
            RoutingMetricsListener metricsListener
    ) {
        RoutingProperties routingProps = properties.getRouting();
        if (routingProps != null && routingProps.getRedis() != null && routingProps.getRedis().isEnabled()) {
            log.info("Initializing Lettuce Redis routing cache at {}", routingProps.getRedis().getUri());
            return new LettuceRedisRoutingCache(
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
        ConsistentHashRing ring = (ownershipResolver != null && ownershipResolver.ring().isPresent())
                ? ownershipResolver.ring().get()
                : ConsistentHashRing.of(1, List.of("standalone"));

        RedisRoutingCache redisCache = redisCacheProvider.getIfAvailable();
        RoutingProperties routingProps = properties.getRouting() != null ? properties.getRouting() : new RoutingProperties();

        Duration caffeineTtl = Duration.ofSeconds(routingProps.getCaffeine().getTtlSeconds());
        long caffeineMaxSize = routingProps.getCaffeine().getMaxSize();
        long redisTtl = routingProps.getRedis().getTtlSeconds();

        return new WaterfallRoutingResolver(
                ring,
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
    public RoutingInvalidationSubscriber routingInvalidationSubscriber(
            SynapseProperties properties,
            ObjectProvider<RedisRoutingCache> redisCacheProvider,
            WaterfallRoutingResolver resolver,
            RoutingMetricsListener metricsListener
    ) {
        RedisRoutingCache redisCache = redisCacheProvider.getIfAvailable();
        if (redisCache instanceof LettuceRedisRoutingCache lettuceCache && lettuceCache.isAvailable()) {
            String cellId = properties.getCell() != null ? properties.getCell().getId() : "default";
            try {
                RoutingInvalidationSubscriber subscriber = new RoutingInvalidationSubscriber(
                        cellId,
                        io.lettuce.core.RedisClient.create(io.lettuce.core.RedisURI.create(properties.getRouting().getRedis().getUri())),
                        resolver,
                        metricsListener
                );
                subscriber.start();
                return subscriber;
            } catch (Exception e) {
                log.warn("Failed to start RoutingInvalidationSubscriber: {}", e.getMessage());
            }
        }
        return null;
    }
}
