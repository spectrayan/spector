/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.dopamine;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.memory.model.ImportanceContext;
import com.spectrayan.spector.memory.model.ImportanceResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.OrgUnitSoul;
import com.spectrayan.spector.memory.model.TenantSoul;
import com.spectrayan.spector.memory.model.UserSoul;
import com.spectrayan.spector.memory.neurodivergent.IcnuWeights;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;

@DisplayName("DefaultImportanceProvider Multi-Soul Composition (ADR-0029 §2.5.2)")
class DefaultImportanceProviderMultiSoulTest {

    private DefaultImportanceProvider provider;

    @BeforeEach
    void setUp() {
        SurpriseDetector detector = new SurpriseDetector();
        FlashbulbPolicy policy = new FlashbulbPolicy(3.5);
        IcnuWeights weights = new IcnuWeights(0.4f, 0.2f, 0.2f, 0.2f, 0.5f, 5.0f);
        provider = new DefaultImportanceProvider(detector, policy, weights);
    }

    @Test
    @DisplayName("Tenant compliance rules enforce non-negotiable importance floor")
    void tenantSoulEnforcesComplianceFloor() {
        TenantSoul tenantSoul = new TenantSoul(
                "tenant-hospital",
                "Hospital System",
                "Healthcare provider",
                List.of("medical", "patient"),
                List.of("access review", "hipaa", "audit finding"),
                new float[]{0.1f, 0.2f},
                (short) 1,
                Instant.now(),
                Instant.now()
        );

        // Low-interest memory cue that happens to contain a compliance rule trigger
        IngestionHints hints = new IngestionHints(0.1f, 0.1f, 0.1f);
        ImportanceContext ctx = new ImportanceContext(
                "failed access review on Ward B",
                new float[]{0.1f, 0.2f},
                hints,
                null,
                MemoryType.EPISODIC,
                1.0f,
                0.0,
                true,
                List.of(tenantSoul)
        );

        ImportanceResult result = provider.score(ctx);
        assertThat(result.importance()).isGreaterThanOrEqualTo(7.0f);
    }

    @Test
    @DisplayName("OrgUnitSoul provides expertise boost for aligned embedding")
    void orgUnitSoulProvidesExpertiseBoost() {
        float[] alignedEmbedding = new float[]{1.0f, 0.0f};
        OrgUnitSoul orgSoul = new OrgUnitSoul(
                "org-sec-audit",
                "Security Audit Unit",
                "Audit specialist team",
                List.of("security", "compliance"),
                alignedEmbedding,
                (short) 1,
                Instant.now(),
                Instant.now()
        );

        IngestionHints hints = new IngestionHints(0.5f, 0.5f, 0.5f);
        ImportanceContext ctxWithoutSoul = new ImportanceContext(
                "Routine server maintenance log",
                alignedEmbedding,
                hints,
                null,
                MemoryType.EPISODIC,
                1.0f,
                0.0,
                true,
                List.of()
        );

        ImportanceContext ctxWithOrg = new ImportanceContext(
                "Routine server maintenance log",
                alignedEmbedding,
                hints,
                null,
                MemoryType.EPISODIC,
                1.0f,
                0.0,
                true,
                List.of(orgSoul)
        );

        ImportanceResult unboosted = provider.score(ctxWithoutSoul);
        ImportanceResult boosted = provider.score(ctxWithOrg);

        assertThat(boosted.importance()).isGreaterThan(unboosted.importance());
    }

    @Test
    @DisplayName("Composition law accumulates tenant floor, org boost, and user baseline")
    void compositionLawAccumulatesAcrossFullStack() {
        TenantSoul tenantSoul = new TenantSoul(
                "tenant-hospital", "Hospital", "Health",
                List.of(), List.of("critical"), null, (short) 1, Instant.now(), Instant.now()
        );
        OrgUnitSoul orgSoul = new OrgUnitSoul(
                "org-1", "Org", "Unit", List.of(), new float[]{1.0f, 0.0f}, (short) 1, Instant.now(), Instant.now()
        );
        UserSoul userSoul = new UserSoul(
                "user-auditor", "Auditor", "Security auditor",
                null, null, (short) 1, Instant.now(), Instant.now()
        );

        ImportanceContext ctx = new ImportanceContext(
                "critical security alert found",
                new float[]{1.0f, 0.0f},
                new IngestionHints(0.2f, 0.2f, 0.2f),
                null,
                MemoryType.EPISODIC,
                1.0f,
                0.0,
                true,
                List.of(tenantSoul, orgSoul, userSoul)
        );

        ImportanceResult result = provider.score(ctx);
        assertThat(result.importance()).isGreaterThanOrEqualTo(7.0f);
    }
}
