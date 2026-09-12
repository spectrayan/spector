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
package com.spectrayan.spector.synapse.config.dr;

import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.dr.DisasterRecoveryExporter;
import com.spectrayan.spector.synapse.dr.DisasterRecoveryRestorer;
import com.spectrayan.spector.synapse.dr.ObjectStoreClient;
import com.spectrayan.spector.synapse.dr.S3CompatibleObjectStoreClient;
import com.spectrayan.spector.synapse.dr.TenantErasureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Spring auto-configuration for Cell Disaster Recovery, mutable cloud snapshot export,
 * standby hydration, and compliance erasure (ADR-0034 §11.2, §14, §16, Phase 6).
 *
 * <p>Wires:
 * <ul>
 *   <li>{@link ObjectStoreClient} (S3-compatible object store client)</li>
 *   <li>{@link DisasterRecoveryExporter} (cloud export of mutable namespaces)</li>
 *   <li>{@link DisasterRecoveryRestorer} (hydration from object storage)</li>
 *   <li>{@link TenantErasureService} (GDPR/compliance erasure across disk, cloud, and replicas)</li>
 * </ul>
 * </p>
 */
@Configuration
public class DisasterRecoveryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DisasterRecoveryConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spector.dr", name = "object-store-endpoint")
    public ObjectStoreClient objectStoreClient(SynapseProperties properties, Environment env) {
        DisasterRecoveryProperties drProps = properties.getDr();
        String endpoint = drProps.getObjectStoreEndpoint();
        String region = drProps.getObjectStoreRegion();
        String accessKey = env.getProperty("AWS_ACCESS_KEY_ID", env.getProperty("spector.dr.access-key", ""));
        String secretKey = env.getProperty("AWS_SECRET_ACCESS_KEY", env.getProperty("spector.dr.secret-key", ""));
        long bandwidthLimit = drProps.getBandwidthLimitBytesPerSec();

        log.info("[DisasterRecoveryConfiguration] Initializing S3CompatibleObjectStoreClient (endpoint={}, region={})",
                endpoint, region);

        return new S3CompatibleObjectStoreClient(endpoint, region, accessKey, secretKey, bandwidthLimit);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ObjectStoreClient.class)
    @ConditionalOnProperty(prefix = "spector.dr", name = "export-enabled", havingValue = "true")
    public DisasterRecoveryExporter disasterRecoveryExporter(
            ObjectStoreClient objectStoreClient,
            SynapseProperties properties,
            ObjectProvider<AccountCatalog> accountCatalogProvider
    ) {
        DisasterRecoveryProperties drProps = properties.getDr();
        log.info("[DisasterRecoveryConfiguration] Initializing DisasterRecoveryExporter (bucket={})",
                drProps.getObjectStoreBucket());
        return new DisasterRecoveryExporter(
                objectStoreClient,
                drProps,
                drProps.getObjectStoreBucket(),
                accountCatalogProvider.getIfAvailable()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ObjectStoreClient.class)
    public DisasterRecoveryRestorer disasterRecoveryRestorer(
            ObjectStoreClient objectStoreClient,
            SynapseProperties properties,
            ObjectProvider<AccountCatalog> accountCatalogProvider
    ) {
        DisasterRecoveryProperties drProps = properties.getDr();
        log.info("[DisasterRecoveryConfiguration] Initializing DisasterRecoveryRestorer (bucket={}, region={})",
                drProps.getObjectStoreBucket(), drProps.getObjectStoreRegion());
        return new DisasterRecoveryRestorer(
                objectStoreClient,
                drProps.getObjectStoreBucket(),
                drProps.getObjectStoreRegion(),
                accountCatalogProvider.getIfAvailable(),
                drProps
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(AccountCatalog.class)
    public TenantErasureService tenantErasureService(
            SynapseProperties properties,
            AccountCatalog accountCatalog,
            ObjectProvider<ObjectStoreClient> objectStoreClientProvider
    ) {
        DisasterRecoveryProperties drProps = properties.getDr();
        String bucket = drProps != null ? drProps.getObjectStoreBucket() : null;
        log.info("[DisasterRecoveryConfiguration] Initializing TenantErasureService (bucket={}, path={})",
                bucket, properties.remembererRoot());
        return new TenantErasureService(
                accountCatalog,
                objectStoreClientProvider.getIfAvailable(),
                bucket,
                properties.remembererRoot()
        );
    }
}
