---
title: Kubernetes & Helm Deployment
description: "Deploy Spector Cognitive Memory to Kubernetes clusters using the official Helm chart."
---

# ☸️ Kubernetes & Helm Deployment

> **Developer-friendly, production-ready Helm chart for deploying Spector on Kubernetes.**

Spector packages an official Helm chart published to the GitHub Container Registry as an OCI artifact (`oci://ghcr.io/spectrayan/charts/spector`).

---

## Quick Start

### 1. Install from GHCR (OCI)

No external repository setup is needed. Install directly using Helm 3.8+:

```bash
helm install spector oci://ghcr.io/spectrayan/charts/spector \
  --namespace spector \
  --create-namespace
```

### 2. Verify Deployment

```bash
kubectl get pods -n spector
kubectl get pvc -n spector
```

Port-forward to access Synapse locally:
```bash
kubectl port-forward svc/spector 7070:7070 -n spector
```

---

## Architecture & Workload Layout

The Helm chart provisions a multi-role cell topology designed for high availability and resource isolation:

```
                      ┌─────────────────────────┐
                      │    Ingress / Client     │
                      └────────────┬────────────┘
                                   │ :7070
                      ┌────────────▼────────────┐
                      │    spector-gateway      │  (Stateless Deployment, HPA)
                      └─────┬─────────────┬─────┘
                     :7070  │             │ :7070 (X-Spector-Allow-Replica)
           ┌────────────────▼───┐     ┌───▼────────────────┐
           │   spector-owner    │     │  spector-replica   │
           │   (StatefulSet)    ├────►│  (StatefulSet)     │
           │   Local NVMe / PVC │:9090│  Local NVMe / PVC  │
           └────────────────────┘     └────────────────────┘
```

- **`spector-owner` (StatefulSet)**: Authoritative single-writers holding live memory namespaces and local NVMe storage. Enforces `requests.memory == limits.memory == 16Gi` (with 2 GiB heap) to protect OS page cache, strict host anti-affinity (`kubernetes.io/hostname`), and a `PodDisruptionBudget` (`maxUnavailable: 1`).
- **`spector-replica` (StatefulSet)**: Read/warm followers receiving asynchronous snapshot bundles and WAL streams over replication port `:9090`. Configured with `replica.hotCap: 500`.
- **`spector-gateway` (Deployment)**: Stateless API proxy handling HTTP traffic on port `:7070` and routing requests. Autoscaling via Horizontal Pod Autoscaler (HPA) is supported for gateways.
- **Headless Services**: Dedicated headless services (`spector-owner-headless`, `spector-replica-headless`) for stable individual pod DNS addressing and ring membership.
- **Cell Coordinator Lease & RBAC**: A Kubernetes `coordination.k8s.io/v1` Lease per cell with least-privilege RBAC restricted solely to Lease and ConfigMap operations.
- **Network Boundaries**: Default-deny `NetworkPolicy` restricting port `:7070` ingress to gateways, isolating replication port `:9090` strictly between owners and replicas, and restricting Redis `:6379`.

---

## Single-Role Compatibility & Migration Path

The chart supports two topology modes configured via `topology.mode`:

1. **`split` (Default)**: Distinct owner, replica, and gateway tiers for multi-node production cells.
2. **`single-role`**: Retains the unified single StatefulSet naming (`spector-node`) matching standalone deployments.

### Split-Topology Upgrade Path

> [!WARNING]
> Upgrading from `single-role` to `split` mode involves creating new StatefulSets (`spector-owner`). Because Kubernetes StatefulSets do not automatically migrate existing PersistentVolumeClaims, transitioning existing data between sets requires an explicit storage migration procedure. For in-place upgrades of existing single-node installations without data migration, set `topology.mode: single-role`.

---

## Capacity Guidance & Hardware Projections

The following sizing figures represent architectural projections for production cell deployments:

| Resource Dimension | Projected Production Guidance | Helm Default (Test Cluster Safe) |
|:---|:---|:---|
| **L1 Active Namespaces** | ~2,000 active namespaces per owner node (projected) | 2,000 (`pager.hotCap`) |
| **L2 Secondary Namespaces** | ~20,000 secondary namespaces on local NVMe (projected) | 20,000 (`pager.warmCap`) |
| **Container Memory** | 16 GiB cgroup limit (`requests.memory == limits.memory`) | 16 GiB (owner split) / 3 GiB (single-role) |
| **JVM Heap Allocation** | 2 GiB heap (`-Xms2G -Xmx2G`), balance to OS page cache | 2 GiB heap |
| **Storage Capacity** | 500 GiB – 2 TiB NVMe per owner node (projected) | 100 GiB PVC default |
| **Storage Class** | Local NVMe (`spector-nvme-local`) mandatory for L1/L2 | `spector-nvme-local` |

*Note: Total cell RAM is not simply `owners × namespaces` because replica nodes maintain a bounded subset (`replica.hotCap: 500`). EBS gp3 storage is recommended only for warm-only replica experiments.*

---

## Local Cluster Profiles (Docker Desktop / Kind / Minikube)

For local single-node clusters with limited memory and unprivileged nodes, use the curated `values-local-dev.yaml`:

```bash
helm install spector-cell ./deploy/helm/spector \
  --namespace spector-cell \
  --create-namespace \
  -f ./deploy/helm/spector/values-local-dev.yaml
```

This profile sets:
- **Soft Anti-Affinity**: Allows co-scheduling multiple owners and replicas on a single local node.
- **Lightweight Memory Limits**: 512Mi request / 1Gi limit per node with `-Xms256m -Xmx512m`.
- **Standard Storage**: Uses `hostpath` persistence.
- **Disabled Sysctl & NetworkPolicy**: Avoids CNI/privilege conflicts on unprivileged local Docker Desktop nodes.

### Accessing Cortex UI & API Locally

Forward the service ports to your machine:
```bash
kubectl port-forward -n spector-cell svc/spector-cell 7700:7700 7070:7070
```
- **Cortex Neural Dashboard**: [`http://localhost:7700`](http://localhost:7700)
- **Synapse REST API**: [`http://localhost:7070/actuator/health`](http://localhost:7070/actuator/health)

---

## Configuration Reference (`values.yaml`)

Key parameters in `deploy/helm/spector/values.yaml`:

| Parameter | Default | Description |
|:---|:---|:---|
| `cell.id` | `cell-1` | Unique cell identifier |
| `cell.region` | `us-east-1` | Geographic region for the cell |
| `topology.mode` | `split` | Deployment mode: `split` (owner/replica/gateway) or `single-role` |
| `owners.replicas` | `3` | Number of authoritative owner nodes in the cell |
| `replicas.replicas` | `2` | Number of read/warm replica nodes in the cell |
| `gateway.replicas` | `2` | Number of stateless API gateway proxy pods |
| `pager.hotCap` | `2000` | Maximum active memory namespaces per owner |
| `pager.warmCap` | `20000` | Maximum tiered local NVMe namespaces per owner |
| `replicas.hotCap` | `500` | Maximum active memory namespaces per replica |
| `sysctl.enabled` | `true` | Enable kernel tuning init container (`vm.max_map_count`, `vm.swappiness`) |
| `sysctl.vmMaxMapCount` | `262144` | Kernel max memory map count |
| `networkPolicy.enabled` | `true` | Enforce default-deny network isolation |
| `serviceMonitor.enabled` | `true` | Provision Prometheus Operator ServiceMonitor |

### Upgrading a Release

```bash
helm upgrade spector ./deploy/helm/spector \
  --namespace spector \
  --values my-values.yaml
```

### Uninstalling

```bash
helm uninstall spector --namespace spector
```
*(Note: PersistentVolumeClaims are retained by default to prevent accidental data loss.)*
