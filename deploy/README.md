# Spector Deployment Registry

> **Unified deployment directory for Spector Cognitive Memory across Kubernetes, Docker, Cloud Platforms, and Agent MCP environments.**

This directory houses all packaging, container definitions, infrastructure-as-code (IaC), and orchestration manifests for Spector.

---

## Deployment Modalities & Navigation

Choose the deployment modality that matches your target environment:

| Modality | Path | Primary Use Case | Key Highlights |
|:---|:---|:---|:---|
| **Kubernetes (Helm)** | [`helm/`](helm/) | Production Cell HA & local multi-pod testing | Split-role topology (Gateway, Owner, Replica), mTLS replication, coordinator leases, [`values-local-dev.yaml`](helm/spector/values-local-dev.yaml) |
| **Kubernetes (Raw Manifest)** | [`k8s-statefulset.yaml`](k8s-statefulset.yaml) | Quick standalone K8s evaluation | Single StatefulSet, hostpath/local-storage PVC, non-root security context |
| **Docker Containers** | [`docker/`](docker/) | Container images & standalone execution | Multi-stage build, host-assisted local build, Nginx reverse proxy, Cortex UI on `:7700` |
| **Docker Compose (Cluster)** | [`compose/cell-3node/`](compose/cell-3node/) | Local 3-node Cell HA cluster simulation | Consistent Ketama hash ring with 160 vnodes, 0 external dependencies |
| **Terraform (Multi-Cloud)** | [`terraform/`](terraform/) | Managed cloud container services | AWS ECS Fargate + EFS, GCP Cloud Run + GCS, Azure Container Apps + Azure Files |
| **Zero-Install MCP Runner** | [`npm/spector/`](npm/spector/) | Desktop AI tools (Claude, Cursor, Windsurf) | `@spectrayan/spector` CLI/MCP launcher with zero manual Java/SIMD configuration |
| **Disaster Recovery Drills** | [`dr/`](dr/) | Operational verification & DR validation | `dr-drill.sh` automated backup, restore, and tenant erasure drills |

---

## Port Architecture Summary

Across all deployment targets, Spector enforces strict network boundary isolation (ADR-0034):

| Port | Service | Audience | Access Notes |
|:---|:---|:---|:---|
| **`7700`** | **Cortex UI Dashboard** | Web browsers, operators | Angular SPA served by internal Nginx reverse proxy |
| **`7070`** | **Synapse REST / MCP API** | AI agents, client SDKs, health checks | Spring Boot HTTP, SSE event streams, and `/actuator/health` |
| **`9090`** | **Cell Replication Plane** | Intra-cell nodes only | Strict mutual TLS 1.3 frame streaming between owners & replicas; never exposed publicly |

---

## Quick Reference Commands

### Local Kubernetes (Docker Desktop / Kind)
```bash
helm install spector-cell deploy/helm/spector -n spector-cell -f deploy/helm/spector/values-local-dev.yaml
kubectl port-forward -n spector-cell svc/spector-cell 7700:7700 7070:7070
```

### Standalone Docker
```bash
docker run -d -p 7700:8080 -p 7070:7070 -v spector-data:/data spector:latest
```

### 3-Node Cell Cluster (Compose)
```bash
docker compose -f deploy/compose/cell-3node/docker-compose.yml up -d
```

### Cloud Terraform (AWS ECS Example)
```bash
cd deploy/terraform && terraform init
```
