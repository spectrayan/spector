--
-- Copyright 2026 Spectrayan
--
-- Licensed under the Business Source License 1.1 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
--
-- Change Date: July 6, 2030
-- Change License: Apache License, Version 2.0
--

-- Phase 6: Multi-Tenant Identity, Compliance Floors, and Legal Hold (ADR-0029 v4).

ALTER TABLE namespaces ADD COLUMN IF NOT EXISTS legal_hold BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS legal_hold BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);

CREATE TABLE IF NOT EXISTS tenants (
    tenant_id          VARCHAR(64)   NOT NULL,
    name               VARCHAR(255)  NOT NULL,
    description        VARCHAR(2048),
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    legal_hold         BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (tenant_id)
);
