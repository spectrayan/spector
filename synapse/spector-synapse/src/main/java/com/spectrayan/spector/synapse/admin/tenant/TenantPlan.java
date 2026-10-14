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
package com.spectrayan.spector.synapse.admin.tenant;

import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.TenantQuotasDto;

/**
 * Packaging profile / subscription plan for a tenant (Issue #1055).
 */
public enum TenantPlan {

    FREE(new TenantQuotasDto(5, 10, 1_073_741_824L)),
    STANDARD(new TenantQuotasDto(50, 200, 10_737_418_240L)),
    ENTERPRISE(new TenantQuotasDto(500, 2000, 107_374_182_400L)),
    UNLIMITED(new TenantQuotasDto(-1, -1, -1L));

    private final TenantQuotasDto defaultQuotas;

    TenantPlan(TenantQuotasDto defaultQuotas) {
        this.defaultQuotas = defaultQuotas;
    }

    public TenantQuotasDto defaultQuotas() {
        return defaultQuotas;
    }

    public static TenantPlan fromStringOrDefault(String plan) {
        if (plan == null || plan.isBlank()) {
            return STANDARD;
        }
        try {
            return TenantPlan.valueOf(plan.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return STANDARD;
        }
    }
}
