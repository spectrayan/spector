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
package com.spectrayan.spector.gateway.config;

import com.spectrayan.spector.cluster.OwnershipResolver;
import com.spectrayan.spector.cluster.gateway.GatewayForwarder;
import com.spectrayan.spector.cluster.gateway.GatewayHttpTransport;
import com.spectrayan.spector.cluster.gateway.RoutingKeyExtractor;
import com.spectrayan.spector.cluster.membership.StaticMembershipSource;
import com.spectrayan.spector.cluster.node.NodeIdentity;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.cluster.routing.ConsistentHashRing;
import com.spectrayan.spector.cluster.routing.RoutingMetricsListener;
import com.spectrayan.spector.cluster.routing.cache.LettuceRedisRoutingCache;
import com.spectrayan.spector.cluster.routing.cache.RedisRoutingCache;
import com.spectrayan.spector.cluster.routing.cache.WaterfallRoutingResolver;
import com.spectrayan.spector.cluster.routing.invalidation.RoutingInvalidationSubscriber;
import com.spectrayan.spector.gateway.transport.ReactorNettyGatewayHttpTransport;
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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Spring configuration wiring the three-tier routing waterfall and forwarder for spector-gateway (ADR-0081 §8 Phase 2).
 */
@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GatewayConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public RoutingMetricsListener routingMetricsListener(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return RoutingMetricsListener.NOOP;
        }
        return source -> registry.counter("spector.gateway.route.lookup", "tier", source.name().toLowerCase()).increment();
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public RedisClient redisClient(GatewayProperties properties) {
        GatewayProperties.RedisProperties redisProps = properties.getRouting().getRedis();
        if (redisProps != null && redisProps.isEnabled()) {
            String redisUri = redisProps.getUri();
            long timeoutMs = Math.max(10L, redisProps.getTimeoutMs());
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
            GatewayProperties properties,
            ObjectProvider<RedisClient> redisClientProvider,
            RoutingMetricsListener metricsListener
    ) {
        GatewayProperties.RedisProperties redisProps = properties.getRouting().getRedis();
        RedisClient client = redisClientProvider.getIfAvailable();
        if (client != null && redisProps != null && redisProps.isEnabled()) {
            log.info("Initializing Gateway Lettuce Redis routing cache at {}", redisProps.getUri());
            return new LettuceRedisRoutingCache(
                    client,
                    redisProps.getUri(),
                    redisProps.getTimeoutMs(),
                    metricsListener
            );
        }
        return null;
    }

    @Bean
    @ConditionalOnMissingBean
    public OwnershipResolver ownershipResolver(GatewayProperties properties) {
        String cellId = properties.getCell().getId();
        String nodeId = properties.getCell().getNodeId();
        String ringMembersStr = properties.getCell().getRingMembers();

        List<String> members = (ringMembersStr != null && !ringMembersStr.isBlank())
                ? Arrays.stream(ringMembersStr.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                : List.of(nodeId);

        NodeIdentity identity = new NodeIdentity(cellId, nodeId, NodeRole.GATEWAY);
        return new OwnershipResolver(identity, new StaticMembershipSource(cellId, 1, members));
    }

    @Bean
    @ConditionalOnMissingBean
    public WaterfallRoutingResolver waterfallRoutingResolver(
            GatewayProperties properties,
            OwnershipResolver ownershipResolver,
            ObjectProvider<RedisRoutingCache> redisCacheProvider,
            RoutingMetricsListener metricsListener
    ) {
        Supplier<ConsistentHashRing> ringSupplier = () -> ownershipResolver.ring()
                .orElseThrow(() -> new IllegalStateException("Gateway has no active consistent hash ring (ADR-0081 §8)"));

        RedisRoutingCache redisCache = redisCacheProvider.getIfAvailable();
        GatewayProperties.RoutingProperties routingProps = properties.getRouting();

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
                null
        );
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public RoutingInvalidationSubscriber routingInvalidationSubscriber(
            GatewayProperties properties,
            ObjectProvider<RedisClient> redisClientProvider,
            WaterfallRoutingResolver resolver,
            RoutingMetricsListener metricsListener
    ) {
        GatewayProperties.RedisProperties redisProps = properties.getRouting().getRedis();
        RedisClient client = redisClientProvider.getIfAvailable();
        if (client != null && redisProps != null && redisProps.isEnabled()) {
            String cellId = properties.getCell().getId();
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
                log.warn("Failed to create Gateway RoutingInvalidationSubscriber: {}", e.getMessage());
            }
        }
        return null;
    }

    @Bean
    @ConditionalOnMissingBean
    public GatewayHttpTransport gatewayHttpTransport(GatewayProperties properties) {
        Duration connectTimeout = properties.getRouting().getGateway().getOwnerTimeout();
        Duration sseIdleTimeout = properties.getRouting().getGateway().getSseIdleTimeout();
        try {
            return new ReactorNettyGatewayHttpTransport(connectTimeout, sseIdleTimeout);
        } catch (Throwable t) {
            log.warn("Falling back to standard JDK HttpClient transport: {}", t.getMessage());
            return GatewayHttpTransport.defaultJdkTransport(connectTimeout);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public GatewayForwarder gatewayForwarder(
            GatewayProperties properties,
            WaterfallRoutingResolver resolver,
            GatewayHttpTransport transport
    ) {
        int retryMax = properties.getRouting().getGateway().getRetryMax();
        return new GatewayForwarder(resolver, retryMax, transport, GatewayForwarder.defaultNodeUrlResolver(7070));
    }

    @Bean
    @ConditionalOnMissingBean
    public RoutingKeyExtractor routingKeyExtractor() {
        return new RoutingKeyExtractor();
    }
}
