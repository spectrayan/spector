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

-- Memory Catalog Plane (ADR-0029 v4).
-- Adds catalog-level metadata, namespaces, grants, and org containment to the synapse database.

-- Extend users table with catalog profile, quotas, and membership version
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile VARCHAR(32) DEFAULT 'HUMAN_SOLO';
ALTER TABLE users ADD COLUMN IF NOT EXISTS kind VARCHAR(16) DEFAULT 'HUMAN';
ALTER TABLE users ADD COLUMN IF NOT EXISTS flags VARCHAR(256) DEFAULT '{}';
ALTER TABLE users ADD COLUMN IF NOT EXISTS default_namespace_id VARCHAR(13);
ALTER TABLE users ADD COLUMN IF NOT EXISTS max_namespaces INT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS max_hot_namespaces INT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS membership_version BIGINT DEFAULT 0;

-- Namespaces: catalog metadata and owner linkage for memory data-plane directories
CREATE TABLE IF NOT EXISTS namespaces (
    namespace_id       VARCHAR(13)   NOT NULL,
    owner_account_id   VARCHAR(13)   NOT NULL,
    slug               VARCHAR(63)   NOT NULL,
    type               VARCHAR(16)   NOT NULL,
    status             VARCHAR(16)   NOT NULL,
    display_name       VARCHAR(255),
    description        VARCHAR(2048),
    bias_json          CLOB,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at   TIMESTAMP,
    PRIMARY KEY (namespace_id),
    CONSTRAINT uq_namespaces_owner_slug UNIQUE (owner_account_id, slug),
    CONSTRAINT fk_namespaces_owner FOREIGN KEY (owner_account_id) REFERENCES users (user_id)
);

CREATE INDEX IF NOT EXISTS idx_namespaces_owner ON namespaces (owner_account_id);
CREATE INDEX IF NOT EXISTS idx_namespaces_slug ON namespaces (slug);

-- Grants: multi-principal authorization for namespaces and identity regions
CREATE TABLE IF NOT EXISTS grants (
    grant_id         VARCHAR(13)   NOT NULL,
    object_type      VARCHAR(32)   NOT NULL,
    object_id        VARCHAR(64)   NOT NULL,
    principal_id     VARCHAR(13)   NOT NULL,
    principal_type   VARCHAR(16)   NOT NULL,
    role             VARCHAR(16),
    actions          VARCHAR(128),
    granted_by       VARCHAR(13)   NOT NULL,
    granted_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at       TIMESTAMP,
    revoked_at       TIMESTAMP,
    constraints_json CLOB,
    PRIMARY KEY (grant_id)
);

CREATE INDEX IF NOT EXISTS idx_grants_object ON grants (object_type, object_id);
CREATE INDEX IF NOT EXISTS idx_grants_principal ON grants (principal_id);
CREATE INDEX IF NOT EXISTS idx_grants_active ON grants (object_type, object_id, principal_id, revoked_at);

-- Org units: optional organizational containment
CREATE TABLE IF NOT EXISTS org_units (
    org_unit_id  VARCHAR(13)  NOT NULL,
    tenant_id    VARCHAR(13)  NOT NULL,
    name         VARCHAR(255) NOT NULL,
    PRIMARY KEY (org_unit_id)
);

-- Org unit membership
CREATE TABLE IF NOT EXISTS org_unit_members (
    org_unit_id  VARCHAR(13) NOT NULL,
    account_id   VARCHAR(13) NOT NULL,
    PRIMARY KEY (org_unit_id, account_id),
    CONSTRAINT fk_oum_org FOREIGN KEY (org_unit_id) REFERENCES org_units (org_unit_id),
    CONSTRAINT fk_oum_acct FOREIGN KEY (account_id) REFERENCES users (user_id)
);

CREATE INDEX IF NOT EXISTS idx_oum_account ON org_unit_members (account_id);
