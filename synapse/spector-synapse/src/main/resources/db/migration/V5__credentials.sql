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

CREATE TABLE IF NOT EXISTS credentials (
    credential_id    VARCHAR(64)  NOT NULL,
    tenant_id        VARCHAR(64)  NOT NULL DEFAULT 'default',
    user_id          VARCHAR(64),
    name             VARCHAR(128) NOT NULL,
    category         VARCHAR(32)  NOT NULL,
    provider         VARCHAR(64)  NOT NULL,
    credential_type  VARCHAR(32)  NOT NULL DEFAULT 'API_KEY',
    ciphertext       CLOB         NOT NULL,
    iv               VARCHAR(32)  NOT NULL,
    auth_tag         VARCHAR(32)  NOT NULL,
    masked_preview   VARCHAR(128) NOT NULL,
    properties_json  CLOB,
    is_default       BOOLEAN      NOT NULL DEFAULT FALSE,
    description      VARCHAR(255),
    version          INT          NOT NULL DEFAULT 1,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at       TIMESTAMP,
    last_used_at     TIMESTAMP,
    PRIMARY KEY (credential_id),
    CONSTRAINT uq_credentials_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX IF NOT EXISTS idx_credentials_tenant_provider ON credentials(tenant_id, provider);
CREATE INDEX IF NOT EXISTS idx_credentials_tenant_category ON credentials(tenant_id, category);
CREATE INDEX IF NOT EXISTS idx_credentials_user ON credentials(user_id);
