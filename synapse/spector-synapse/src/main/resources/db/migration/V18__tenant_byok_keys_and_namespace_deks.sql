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

-- Issue #1070 / ADR-0088: Per-tenant BYOK KEK configuration and per-namespace wrapped DEK catalog.

CREATE TABLE IF NOT EXISTS tenant_kek_config (
    tenant_id        VARCHAR(64)   NOT NULL,
    provider         VARCHAR(32)   NOT NULL,
    kek_key_id       VARCHAR(512)  NOT NULL,
    credential_name  VARCHAR(128),
    region           VARCHAR(64),
    endpoint         VARCHAR(512),
    key_version      VARCHAR(128)  NOT NULL DEFAULT 'v1',
    status           VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
    last_verified_at TIMESTAMP WITH TIME ZONE,
    last_error       VARCHAR(1024),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_tenant_kek_provider ON tenant_kek_config (provider, status);

CREATE TABLE IF NOT EXISTS namespace_encryption_key (
    tenant_id        VARCHAR(64)   NOT NULL,
    namespace_id     VARCHAR(128)  NOT NULL,
    dek_version      INT           NOT NULL DEFAULT 1,
    kek_provider     VARCHAR(32)   NOT NULL,
    kek_key_id       VARCHAR(512)  NOT NULL,
    kek_version      VARCHAR(128)  NOT NULL DEFAULT 'v1',
    wrapped_dek      VARCHAR(4096) NOT NULL,
    wrap_iv          VARCHAR(128),
    wrap_aad         VARCHAR(512)  NOT NULL,
    status           VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rotated_at       TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (tenant_id, namespace_id, dek_version)
);

CREATE INDEX IF NOT EXISTS idx_ns_enc_key_active ON namespace_encryption_key (tenant_id, namespace_id, status);
