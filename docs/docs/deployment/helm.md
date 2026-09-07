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

The Helm chart provisions:
- **`StatefulSet`**: Ensures persistent node identity and guarantees safe storage unmounting during rolling upgrades.
- **`PersistentVolumeClaim`**: Dedicated block or network storage mounted at `/var/lib/spector` for WAL and off-heap memory segments.
- **`Service`**: Internal `ClusterIP` on port `:7070` with readiness and liveness HTTP probes.
- **`ConfigMap`**: Application properties configuring embedders, storage paths, and logging.

---

## Local Cluster Profiles (kind / k3d / Minikube)

For local development clusters with limited memory:

```bash
helm install spector oci://ghcr.io/spectrayan/charts/spector \
  --namespace spector \
  --create-namespace \
  --set persistence.size=5Gi \
  --set resources.requests.cpu=250m \
  --set resources.requests.memory=1Gi \
  --set resources.limits.memory=2Gi
```

---

## Configuration Reference (`values.yaml`)

Key parameters in `deploy/helm/spector/values.yaml`:

| Parameter | Default | Description |
|:---|:---|:---|
| `image.repository` | `ghcr.io/spectrayan/spector` | Container image repository |
| `image.tag` | `0.1.0-alpha` | Container tag |
| `service.type` | `ClusterIP` | Kubernetes service type (`ClusterIP`, `NodePort`, `LoadBalancer`) |
| `service.port` | `7070` | Service port |
| `persistence.enabled` | `true` | Enable persistent storage via PVC |
| `persistence.size` | `10Gi` | Disk storage allocation |
| `persistence.storageClass` | `""` | Storage class name (empty string uses cluster default) |
| `resources.requests.memory` | `1Gi` | Minimum memory request |
| `resources.limits.memory` | `4Gi` | Maximum memory limit |
| `spector.embedderProvider` | `onnx` | Default embedder provider (`onnx`, `ollama`, `openai`) |

### Upgrading a Release

```bash
helm upgrade spector oci://ghcr.io/spectrayan/charts/spector \
  --namespace spector \
  --values my-values.yaml
```

### Uninstalling

```bash
helm uninstall spector --namespace spector
```
*(Note: PersistentVolumeClaims are retained by default to prevent accidental data loss.)*
