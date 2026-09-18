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
