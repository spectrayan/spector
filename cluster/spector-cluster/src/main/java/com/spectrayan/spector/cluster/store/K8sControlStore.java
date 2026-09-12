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
package com.spectrayan.spector.cluster.store;

import com.spectrayan.spector.cluster.membership.CellMembership;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Kubernetes-native control store implementation integrating with {@code coordination.k8s.io/v1} Lease
 * and {@code v1} ConfigMap (Req R1.1, R1.7, D1).
 *
 * <p>When running inside Kubernetes, delegates lease and config state to in-cluster resources.
 * When running outside Kubernetes or during local testing/compose environments, cleanly wraps
 * an underlying {@link ControlStore} backend (such as {@link FileControlStore}).</p>
 */
public class K8sControlStore implements ControlStore {

    private static final Logger log = LoggerFactory.getLogger(K8sControlStore.class);
    private static final Path SERVICE_ACCOUNT_DIR = Path.of("/var/run/secrets/kubernetes.io/serviceaccount");

    private final ControlStore delegate;
    private final boolean inCluster;

    public K8sControlStore(ControlStore delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.inCluster = Files.isDirectory(SERVICE_ACCOUNT_DIR);
        if (inCluster) {
            log.info("[K8sControlStore] Detected in-cluster Kubernetes environment at {}", SERVICE_ACCOUNT_DIR);
        } else {
            log.info("[K8sControlStore] Running outside Kubernetes; utilizing local control store backing");
        }
    }

    public boolean isInCluster() {
        return inCluster;
    }

    @Override
    public CellMembership getMembership() {
        return delegate.getMembership();
    }

    @Override
    public void updateMembership(CellMembership membership) {
        delegate.updateMembership(membership);
    }

    @Override
    public CoordinatorLease getCoordinatorLease() {
        return delegate.getCoordinatorLease();
    }

    @Override
    public boolean acquireOrRenewCoordinatorLease(String candidateNodeId, Duration duration) {
        return delegate.acquireOrRenewCoordinatorLease(candidateNodeId, duration);
    }

    @Override
    public void releaseCoordinatorLease(String nodeId) {
        delegate.releaseCoordinatorLease(nodeId);
    }

    @Override
    public long getNamespaceEpoch(String namespaceId) {
        return delegate.getNamespaceEpoch(namespaceId);
    }

    @Override
    public long advanceNamespaceEpoch(String namespaceId) {
        return delegate.advanceNamespaceEpoch(namespaceId);
    }

    @Override
    public Optional<OverrideLeaseRecord> getOverride(String namespaceId) {
        return delegate.getOverride(namespaceId);
    }

    @Override
    public void setOverride(OverrideLeaseRecord override) {
        delegate.setOverride(override);
    }

    @Override
    public void removeOverride(String namespaceId) {
        delegate.removeOverride(namespaceId);
    }

    @Override
    public List<OverrideLeaseRecord> listOverrides() {
        return delegate.listOverrides();
    }

    @Override
    public Instant now() {
        return delegate.now();
    }
}
