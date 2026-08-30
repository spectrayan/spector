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
package com.spectrayan.spector.synapse.catalog;

import java.util.List;

/**
 * Organizational unit within a tenant. Catalog membership is authoritative — JWT org claims
 * only narrow, never widen. Each OrgUnit may have an OrgUnitSoul stored in the tenant identity bundle.
 *
 * @param orgUnitId unique organizational unit identifier
 * @param tenantId identifier of the owning tenant
 * @param name human-readable name of the organizational unit
 * @param memberAccountIds list of account identifiers belonging to this organizational unit
 */
public record OrgUnit(
        String orgUnitId,
        String tenantId,
        String name,
        List<String> memberAccountIds
) {
}
