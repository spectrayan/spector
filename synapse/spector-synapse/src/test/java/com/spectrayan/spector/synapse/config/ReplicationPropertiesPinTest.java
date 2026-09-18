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
package com.spectrayan.spector.synapse.config;

import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.synapse.config.replication.ReplicationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins replication properties defaults reflectively to prevent javac inlining hiding stale defaults
 * and enforces that replication is disabled by default (ADR-0034 §9.7, §10, §15.5, Req R12.1, Task 0.3).
 */
class ReplicationPropertiesPinTest {

    @Test
    @DisplayName("Pins replication defaults reflectively in SpectorPropertyConstants")
    void testReflectiveReplicationDefaults() throws Exception {
        assertReflectiveConstant("DEFAULT_REPLICATION_ENABLED", false);
        assertReflectiveConstant("DEFAULT_REPLICATION_PORT", 9090);
        assertReflectiveConstant("DEFAULT_REPLICATION_BIND_HOST", "127.0.0.1");
        assertReflectiveConstant("DEFAULT_REPLICATION_SNAPSHOT_INTERVAL_SECONDS", 60L);
        assertReflectiveConstant("DEFAULT_REPLICATION_SNAPSHOT_MIN_CHANGES", 100);
        assertReflectiveConstant("DEFAULT_REPLICATION_MAX_REPLICA_LAG_SECONDS", 30L);
        assertReflectiveConstant("DEFAULT_REPLICATION_FULL_RESYNC_LAG_THRESHOLD_SECONDS", 300L);
        assertReflectiveConstant("DEFAULT_REPLICATION_REPLICA_READS_ENABLED", false);
        assertReflectiveConstant("DEFAULT_REPLICATION_REPLICA_HOT_CAP", 100);
    }

    @Test
    @DisplayName("ReplicationProperties matches defaults upon instantiation")
    void testReplicationPropertiesInstantiationDefaults() {
        ReplicationProperties props = new ReplicationProperties();

        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getPort()).isEqualTo(9090);
        assertThat(props.getBindHost()).isEqualTo("127.0.0.1");
        assertThat(props.getSnapshotIntervalSeconds()).isEqualTo(60L);
        assertThat(props.getSnapshotMinChanges()).isEqualTo(100);
        assertThat(props.getMaxReplicaLagSeconds()).isEqualTo(30L);
        assertThat(props.getFullResyncLagThresholdSeconds()).isEqualTo(300L);
        assertThat(props.isReplicaReadsEnabled()).isFalse();
        assertThat(props.getReplicaHotCap()).isEqualTo(100);
    }

    private void assertReflectiveConstant(String fieldName, Object expectedValue) throws Exception {
        Field field = SpectorPropertyConstants.class.getField(fieldName);
        Object rawValue = field.get(null);
        assertThat(rawValue)
                .describedAs("Field SpectorPropertyConstants." + fieldName)
                .isEqualTo(expectedValue);
    }
}
