# Spector Helm Chart

Official Helm chart for deploying Spector Cognitive Memory & Vector Search on Kubernetes.

For complete documentation, topology guides, and local development configurations, refer to the [Helm Deployment Guide](../README.md).

## Quick Install

```bash
# Add GHCR OCI chart (Helm 3.8+)
helm install spector oci://ghcr.io/spectrayan/charts/spector \
  --namespace spector \
  --create-namespace
```

## Local Development Install

```bash
helm install spector-cell . \
  -n spector-cell \
  -f values-local-dev.yaml
```

## Key Configuration Values

See [`values.yaml`](values.yaml) for production multi-node defaults and [`values-local-dev.yaml`](values-local-dev.yaml) for single-node local dev setups.

### Cell Topology & Node Roles (ADR-0081)

The chart supports two topology modes configured via `topology.mode`:

| Topology Mode | Description | Workloads Deployed |
|:---|:---|:---|
| `split` (Default) | Enterprise high-availability cell separation. Separates memory write leases, read replicas, and stateless ingress gateways. | `statefulset-owner`, `statefulset-replica`, `deployment-gateway` |
| `single-role` | Lightweight developer mode running an all-in-one standalone stateful set. | `statefulset` |

#### Node Roles (`SPECTOR_NODE_ROLE`)

In `split` mode, pods are assigned specialized roles:

- **`owner`** (`SPECTOR_NODE_ROLE=owner`): Hosts authoritative partition writers, coordinates write locks/fences, and manages background snapshot exports and WAL replication.
- **`replica`** (`SPECTOR_NODE_ROLE=replica`): Read-only memory nodes receiving replicated bundle snapshots and serving read-heavy recall/browse queries within bounded lag budgets.
- **`gateway`** (`SPECTOR_NODE_ROLE=gateway`): Stateless ingress gateway nodes that route incoming HTTP/MCP requests to the correct cell partition owner via consistent hashing and cache rings.

