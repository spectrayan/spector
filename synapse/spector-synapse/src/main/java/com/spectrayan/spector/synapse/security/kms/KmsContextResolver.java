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
package com.spectrayan.spector.synapse.security.kms;

import com.spectrayan.spector.synapse.connector.model.CredentialRecord;
import com.spectrayan.spector.synapse.connector.service.CredentialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Resolves {@link KmsConnectionContext} (credentials, region, endpoint, and properties) for a
 * tenant's KMS invocation by consulting the universal credentials vault ({@link CredentialService})
 * and {@code tenant_kek_config} (ADR-0088 §5, Issue #1070).
 */
@Component
public class KmsContextResolver {

    private static final Logger log = LoggerFactory.getLogger(KmsContextResolver.class);

    private static final ThreadLocal<KmsConnectionContext> OVERRIDE_CONTEXT = new ThreadLocal<>();

    private final CredentialService credentialService;
    private final JdbcClient jdbc;

    @Autowired
    public KmsContextResolver(
            @Autowired(required = false) CredentialService credentialService,
            @Autowired(required = false) JdbcClient jdbc) {
        this.credentialService = credentialService;
        this.jdbc = jdbc;
    }

    /**
     * Executes {@code action} with a temporary candidate {@link KmsConnectionContext} bound to the
     * current thread (used during live KEK registration self-tests before database commit).
     */
    public <T> T withCandidateContext(KmsConnectionContext candidate, Supplier<T> action) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(action, "action must not be null");
        KmsConnectionContext previous = OVERRIDE_CONTEXT.get();
        OVERRIDE_CONTEXT.set(candidate);
        try {
            return action.get();
        } finally {
            if (previous != null) {
                OVERRIDE_CONTEXT.set(previous);
            } else {
                OVERRIDE_CONTEXT.remove();
            }
        }
    }

    /**
     * Builds a candidate {@link KmsConnectionContext} by resolving {@code credentialName} from
     * the universal credentials vault for {@code tenantId}.
     */
    public KmsConnectionContext buildContext(
            String tenantId,
            String provider,
            String kekKeyId,
            String credentialName,
            String region,
            String endpoint) {
        String normTenant = tenantId != null && !tenantId.isBlank() ? tenantId.trim() : "default";
        String secret = null;
        Map<String, Object> props = Map.of();

        if (credentialService != null && credentialName != null && !credentialName.isBlank()) {
            String normCred = credentialName.trim();
            Optional<CredentialRecord> recordOpt = credentialService.getCredential(normTenant, normCred);
            if (recordOpt.isPresent()) {
                props = recordOpt.get().properties() != null ? recordOpt.get().properties() : Map.of();
            }
            secret = credentialService.resolveSecret(normCred, normTenant).orElse(null);
        } else if (credentialService != null && provider != null && !provider.isBlank()) {
            secret = credentialService.resolveSecret(provider.trim(), normTenant).orElse(null);
        }

        return new KmsConnectionContext(
                normTenant,
                provider,
                kekKeyId,
                credentialName,
                secret,
                region,
                endpoint,
                props);
    }

    /**
     * Resolves the active {@link KmsConnectionContext} for the specified tenant and KEK identifier.
     */
    public KmsConnectionContext resolve(String tenantId, String provider, String kekKeyId) {
        String normTenant = tenantId != null && !tenantId.isBlank() ? tenantId.trim() : "default";
        KmsConnectionContext override = OVERRIDE_CONTEXT.get();
        if (override != null && override.tenantId().equalsIgnoreCase(normTenant)) {
            if (override.secret() != null || credentialService == null || override.credentialName() == null) {
                return override;
            }
            return buildContext(
                    normTenant,
                    override.provider() != null ? override.provider() : provider,
                    override.kekKeyId() != null ? override.kekKeyId() : kekKeyId,
                    override.credentialName(),
                    override.region(),
                    override.endpoint());
        }

        String credentialName = null;
        String region = null;
        String endpoint = null;

        if (jdbc != null) {
            try {
                var rowOpt = jdbc.sql("""
                        SELECT credential_name, region, endpoint
                        FROM tenant_kek_config
                        WHERE tenant_id = :tenantId
                        """)
                        .param("tenantId", normTenant)
                        .query((rs, rowNum) -> new String[]{
                                rs.getString("credential_name"),
                                rs.getString("region"),
                                rs.getString("endpoint")
                        })
                        .optional();
                if (rowOpt.isPresent()) {
                    String[] cols = rowOpt.get();
                    credentialName = cols[0];
                    region = cols[1];
                    endpoint = cols[2];
                }
            } catch (Exception e) {
                log.debug("[KmsContextResolver] Lookup in tenant_kek_config skipped for tenant '{}': {}",
                        normTenant, e.getMessage());
            }
        }

        return buildContext(normTenant, provider, kekKeyId, credentialName, region, endpoint);
    }
}
