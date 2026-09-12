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
