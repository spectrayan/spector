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
package com.spectrayan.spector.synapse.config.replication;

import com.spectrayan.spector.config.SpectorPropertyConstants;

import java.io.Serializable;

/**
 * Configuration properties for Cell Snapshot Replication, WAL Tail Streaming, and Replica Recall
 * (ADR-0034 §9.7, §10, §15.5, Phase 3, Req R1, R3, R6, R8, R10).
 *
 * <p>Prefix: {@code spector.replication.*}</p>
 */
public class ReplicationProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean enabled = SpectorPropertyConstants.DEFAULT_REPLICATION_ENABLED;
    private int port = SpectorPropertyConstants.DEFAULT_REPLICATION_PORT;
    private String bindHost = SpectorPropertyConstants.DEFAULT_REPLICATION_BIND_HOST;
    private long snapshotIntervalSeconds = SpectorPropertyConstants.DEFAULT_REPLICATION_SNAPSHOT_INTERVAL_SECONDS;
    private int snapshotMinChanges = SpectorPropertyConstants.DEFAULT_REPLICATION_SNAPSHOT_MIN_CHANGES;
    private long maxReplicaLagSeconds = SpectorPropertyConstants.DEFAULT_REPLICATION_MAX_REPLICA_LAG_SECONDS;
    private long fullResyncLagThresholdSeconds = SpectorPropertyConstants.DEFAULT_REPLICATION_FULL_RESYNC_LAG_THRESHOLD_SECONDS;
    private boolean replicaReadsEnabled = SpectorPropertyConstants.DEFAULT_REPLICATION_REPLICA_READS_ENABLED;
    private int replicaHotCap = SpectorPropertyConstants.DEFAULT_REPLICATION_REPLICA_HOT_CAP;

    public ReplicationProperties() {}

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getBindHost() {
        return bindHost;
    }

    public void setBindHost(String bindHost) {
        this.bindHost = bindHost;
    }

    public long getSnapshotIntervalSeconds() {
        return snapshotIntervalSeconds;
    }

    public void setSnapshotIntervalSeconds(long snapshotIntervalSeconds) {
        this.snapshotIntervalSeconds = snapshotIntervalSeconds;
    }

    public int getSnapshotMinChanges() {
        return snapshotMinChanges;
    }

    public void setSnapshotMinChanges(int snapshotMinChanges) {
        this.snapshotMinChanges = snapshotMinChanges;
    }

    public long getMaxReplicaLagSeconds() {
        return maxReplicaLagSeconds;
    }

    public void setMaxReplicaLagSeconds(long maxReplicaLagSeconds) {
        this.maxReplicaLagSeconds = maxReplicaLagSeconds;
    }

    public long getFullResyncLagThresholdSeconds() {
        return fullResyncLagThresholdSeconds;
    }

    public void setFullResyncLagThresholdSeconds(long fullResyncLagThresholdSeconds) {
        this.fullResyncLagThresholdSeconds = fullResyncLagThresholdSeconds;
    }

    public boolean isReplicaReadsEnabled() {
        return replicaReadsEnabled;
    }

    public void setReplicaReadsEnabled(boolean replicaReadsEnabled) {
        this.replicaReadsEnabled = replicaReadsEnabled;
    }

    public int getReplicaHotCap() {
        return replicaHotCap;
    }

    public void setReplicaHotCap(int replicaHotCap) {
        this.replicaHotCap = replicaHotCap;
    }
}
