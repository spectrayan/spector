# Spector Kubernetes & Helm Deployment

> **Official Helm chart for deploying Spector Cognitive Memory in single-node and multi-pod Cell High Availability topologies.**

This directory contains the Spector Helm chart (`spector/`) and automated test suites (`tests/`).

---

## Architecture: Cell HA Workload Layout

The Helm chart provisions a multi-role cell topology designed for high availability, zero-overhead search, and strict resource isolation (ADR-0034):

```
                      ┌─────────────────────────┐
                      │    Ingress / Client     │
                      └────────────┬────────────┘
                                   │ :7700 (Cortex UI) / :7070 (API)
                      ┌────────────▼────────────┐
                      │    spector-gateway      │  (Stateless Deployment, HPA)
                      └─────┬─────────────┬─────┘
                     :7070  │             │ :7070 (X-Spector-Allow-Replica)
           ┌────────────────▼───┐     ┌───▼────────────────┐
           │   spector-owner    │     │  spector-replica   │
           │   (StatefulSet)    ├────►│  (StatefulSet)     │
           │   Local NVMe / PVC │:9090│  Local NVMe / PVC  │
           └────────────────────┘ mTLS└────────────────────┘
```

- **`spector-gateway` (Deployment)**: Stateless API proxy terminating external traffic, evaluating consistent hash routing, and reverse-proxying the Cortex UI on port `8080` (mapped to `7700`).
- **`spector-owner` (StatefulSet)**: Authoritative single-writers holding live memory namespaces, local NVMe partitions, and WAL segments.
- **`spector-replica` (StatefulSet)**: Asynchronous followers receiving snapshot bundles and WAL frames over replication port `:9090` via mutual TLS 1.3.
- **Headless Services**: Stable individual pod network identifiers (`spector-owner-headless`, `spector-replica-headless`) for consistent ring hashing.
- **Kubernetes Lease Coordination**: Cluster leader election and coordinator lease management via `coordination.k8s.io` Leases (`spector-coordinator-<cell-id>`).

---

## Configuration Profiles

| Values File | Target Environment | Key Characteristics |
|:---|:---|:---|
| [`values.yaml`](spector/values.yaml) | **Production Multi-Node Cluster** | Hard anti-affinity (`kubernetes.io/hostname`), 16Gi RAM per owner (`requests == limits` to protect OS page cache), local NVMe storage classes (`spector-nvme-local`), privileged `init-sysctl` (`vm.max_map_count=262144`). |
| [`values-local-dev.yaml`](spector/values-local-dev.yaml) | **Local Dev / Single-Node Cluster** (Docker Desktop, Kind, Minikube) | Soft anti-affinity (enables co-scheduling on single node), 512Mi/1Gi memory limits, standard `hostpath` storage class, `sysctl.enabled: false`. |

---

## Quick Start

### 1. Local Development (Docker Desktop / Kind)

```bash
# 1. Create namespace
kubectl create namespace spector-cell

# 2. Install using local dev values
helm install spector-cell deploy/helm/spector \
  -n spector-cell \
  -f deploy/helm/spector/values-local-dev.yaml

# 3. Check status
kubectl get pods,pvc,svc,lease -n spector-cell

# 4. Access Cortex UI and API
kubectl port-forward -n spector-cell svc/spector-cell 7700:7700 7070:7070
```
- **Cortex Neural Dashboard**: [`http://localhost:7700`](http://localhost:7700)
- **Synapse API / Health**: [`http://localhost:7070/actuator/health`](http://localhost:7070/actuator/health)

---

### 2. Production Deployment

```bash
# Install from OCI registry (or local chart)
helm install spector oci://ghcr.io/spectrayan/charts/spector \
  --namespace spector \
  --create-namespace \
  -f my-prod-values.yaml
```

---

## Topology Modes

Configured via `topology.mode`:

1. **`split` (Default)**: Distinct owner, replica, and gateway tiers for multi-node production cells.
2. **`single-role`**: Retains a unified single StatefulSet (`spector-node`) matching standalone deployments.

---

## Manifest Testing & Validation

Spector includes a comprehensive shell test suite that renders and validates the Helm templates against 14 architectural invariants:

```bash
bash deploy/helm/tests/test-manifests.sh
```

Tests verify:
- Default-deny network policies and isolation of replication port `:9090`
- Cgroup memory symmetry (`requests.memory == limits.memory`)
- Kernel tuning container generation (`vm.max_map_count`, `fs.file-max`)
- Coordinator RBAC least-privilege scoping
