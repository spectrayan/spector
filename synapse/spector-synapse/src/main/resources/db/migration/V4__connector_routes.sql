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

CREATE TABLE IF NOT EXISTS connector_routes (
    route_id         VARCHAR(128) NOT NULL,
    tenant_id        VARCHAR(64)  NOT NULL DEFAULT 'default',
    name             VARCHAR(255) NOT NULL,
    template_id      VARCHAR(64)  NOT NULL,
    connector_type   VARCHAR(32)  NOT NULL,
    source           VARCHAR(255),
    schedule         VARCHAR(128),
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    parameters_json  CLOB         NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_executed_at TIMESTAMP,
    PRIMARY KEY (route_id)
);

CREATE INDEX IF NOT EXISTS idx_connector_routes_tenant ON connector_routes(tenant_id);
CREATE INDEX IF NOT EXISTS idx_connector_routes_enabled ON connector_routes(enabled);
