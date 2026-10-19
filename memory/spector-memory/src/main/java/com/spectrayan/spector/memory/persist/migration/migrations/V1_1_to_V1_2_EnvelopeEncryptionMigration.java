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
package com.spectrayan.spector.memory.persist.migration.migrations;

import com.spectrayan.spector.memory.persist.DataEncryptor;
import com.spectrayan.spector.memory.persist.migration.NamespaceEncryptionMigrator;
import com.spectrayan.spector.memory.persist.migration.NamespaceMigration;
import com.spectrayan.spector.memory.persist.migration.SchemaVersion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Migration step upgrading a {@link SchemaVersion#V1_1_0} plaintext namespace to
 * {@link SchemaVersion#V1_2_0} ADR-0088 envelope encryption using a per-namespace DEK.
 */
public final class V1_1_to_V1_2_EnvelopeEncryptionMigration implements NamespaceMigration {

    private final DataEncryptor encryptor;

    public V1_1_to_V1_2_EnvelopeEncryptionMigration(DataEncryptor encryptor) {
        this.encryptor = Objects.requireNonNull(encryptor, "encryptor must not be null");
    }

    @Override
    public SchemaVersion fromVersion() {
        return SchemaVersion.V1_1_0;
    }

    @Override
    public SchemaVersion toVersion() {
        return SchemaVersion.V1_2_0;
    }

    @Override
    public String description() {
        return "Encrypt namespace bundles, WAL chunks, and synaptic tags with ADR-0088 per-namespace DEK";
    }

    @Override
    public void migrate(Path namespaceDir) throws IOException {
        NamespaceEncryptionMigrator.migrate(namespaceDir, encryptor);
    }
}
