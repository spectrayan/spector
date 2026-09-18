--
-- Copyright 2026 Spectrayan
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--
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
