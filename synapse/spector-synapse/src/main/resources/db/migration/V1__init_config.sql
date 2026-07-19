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

CREATE TABLE scoped_config (
    scope VARCHAR(255) NOT NULL,
    category VARCHAR(255) NOT NULL,
    config_json CLOB NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    updated_by VARCHAR(255),
    PRIMARY KEY (scope, category)
);
