# 📈 Observability & Metrics

> **Scrape Spector Synapse with Prometheus and visualize it in Grafana.** This page covers the `/actuator/prometheus` endpoint, the JVM and Spector metrics it exposes, a Prometheus scrape configuration, the bundled Grafana dashboard, and the Helm `ServiceMonitor`.

---

## 🔌 The `/actuator/prometheus` Endpoint

Spector Synapse exposes metrics through Spring Boot Actuator backed by Micrometer. The endpoint is enabled in `synapse/spector-synapse/src/main/resources/application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

The endpoint is served on the main API port (`spector.port`, default `7070`).

> [!IMPORTANT]
> The Prometheus exposition format requires the `micrometer-registry-prometheus` dependency on the classpath. Without it, `/actuator/prometheus` is not available even when `prometheus` is listed in `management.endpoints.web.exposure.include`. `spector-synapse` declares it in its `pom.xml`:
>
> ```xml
> <dependency>
>     <groupId>io.micrometer</groupId>
>     <artifactId>micrometer-registry-prometheus</artifactId>
> </dependency>
> ```

Spector-specific gauges are registered only when a Micrometer `MeterRegistry` is present **and** `spector.metrics.enabled` is `true` (the default in `application.yml`).

### Checking the endpoint

```bash
# Full scrape output
curl -s http://localhost:7070/actuator/prometheus

# Only JVM heap and thread metrics
curl -s http://localhost:7070/actuator/prometheus | grep -E '^jvm_(memory_used_bytes|threads_live_threads)'

# Only Spector metrics
curl -s http://localhost:7070/actuator/prometheus | grep '^spector_'
```

> [!NOTE]
> **Verified locally:** scraping `/actuator/prometheus` on a standalone Synapse node returned valid Prometheus-format output (JVM metrics plus `spector_memory_*` and `spector_ns_owner`) without any authentication hurdles.

### Authentication

With the default `spector.auth.enabled=false`, every path is permitted and the endpoint can be scraped without credentials. Do not expose an unauthenticated node beyond a trusted network.

When `spector.auth.enabled=true`, only `spector.auth.public-paths` are anonymous. The default list contains `/actuator/health` but **not** `/actuator/prometheus`, so a scraper would receive `401`. Either add the path explicitly (and restrict network access to the port), or configure the scraper with credentials:

```bash
SPECTOR_AUTH_PUBLIC_PATHS=/actuator/health,/actuator/prometheus,/api/docs,/swagger-ui.html,/swagger-ui/**,/v3/api-docs/**,/v3/api-docs
```

---

## 🏷️ Metric Naming

Spector registers meters with Micrometer's dotted names. The Prometheus registry converts them to Prometheus conventions:

| Micrometer rule | Example |
|---|---|
| Dots become underscores | `spector.memory.count` → `spector_memory_count` |
| Counters get a `_total` suffix | `spector.route.stale` → `spector_route_stale_total` |
| Timers are exported in seconds as `_seconds_count`, `_seconds_sum`, `_seconds_max` | `spector.route.lookup` → `spector_route_lookup_seconds_count` |
| Tags become labels | `type="soft"`, `mode="..."` |

> [!NOTE]
> The memory count gauge is named `spector.memory.count` (`spector_memory_count`). There is no `spector.memory.records` metric.

---

## 📊 Available Metrics

### JVM (Spring Boot / Micrometer defaults)

| Prometheus name | Labels | Description |
|---|---|---|
| `jvm_memory_used_bytes` | `area` (`heap`/`nonheap`), `id` (pool) | Used memory per pool |
| `jvm_memory_committed_bytes` | `area`, `id` | Committed memory per pool |
| `jvm_buffer_memory_used_bytes` | `id` (`direct`/`mapped`) | NIO buffer pool usage |
| `jvm_threads_live_threads` | — | Live JVM threads |

These are standard Micrometer JVM binders; see the full scrape output for the complete list (GC, classes, CPU, etc.).

### Spector memory

Registered by `SpectorMemoryGauges` (`memory/spector-metrics`) and `MemoryRequestBinder` (`synapse/spector-synapse`). Present in all deployment modes, including standalone.

| Micrometer name | Prometheus name | Labels | Description |
|---|---|---|---|
| `spector.memory.count` | `spector_memory_count` | — | Total number of memories across all tiers |
| `spector.memory.pinned.bytes` | `spector_memory_pinned_bytes` | — | Off-heap bytes pinned in RAM |
| `spector.memory.page.faults` | `spector_memory_page_faults` | `type` (`soft`/`hard`) | Cumulative process page faults, read from `/proc/self/stat`. **Linux only** — reports `0` elsewhere |
| `spector.ns.owner` | `spector_ns_owner` | — | Number of namespace memory instances cached by the namespace resolver |

### Routing (clustered / non-standalone only)

| Micrometer name | Type | Tags | Where |
|---|---|---|---|
| `spector.route.lookup` | Timer | `mode` | `MemoryRequestBinder.enforceOwnership()` |
| `spector.route.lookup` | Counter | `tier` | `ClusterRoutingConfiguration` routing cache listener |
| `spector.route.not_owner` | Counter | `namespace`, `owner` | Request refused: namespace owned by another node |
| `spector.route.stale` | Counter | `namespace`, `owner` | Request refused: incoming epoch older than active epoch |
| `spector.route.fenced` | Counter | `namespace` | Request refused: fence token mismatch |

> [!WARNING]
> **Routing metrics are not produced in standalone mode.** `MemoryRequestBinder.enforceOwnership()` and `enforceFence()` return immediately when the node role is `STANDALONE`, and `ClusterRoutingConfiguration` is only loaded when `spector.cell.role` is set to something other than `standalone`. On a standalone node these series are absent, and the routing panels in the Grafana dashboard stay empty. Counter series additionally only appear after their first increment.

> [!NOTE]
> `spector.route.lookup` is registered both as a timer (tag `mode`) and as a counter (tag `tier`). Routing metrics have not yet been verified against a running clustered deployment, so check which `spector_route_lookup_*` series your scrape actually contains. The bundled dashboard uses the timer series (`spector_route_lookup_seconds_*`).

---

## 🎯 Prometheus Scrape Configuration

Minimal static configuration for a node on `localhost:7070`:

```yaml
scrape_configs:
  - job_name: spector-synapse
    metrics_path: /actuator/prometheus
    scrape_interval: 15s
    scrape_timeout: 10s
    static_configs:
      - targets:
          - localhost:7070
```

When Prometheus runs in Docker and Spector runs on the host, replace `localhost` with `host.docker.internal` (Docker Desktop) or the host IP.

Verify the target is `UP` at `http://<prometheus>:9090/targets`, then query e.g. `spector_memory_count` in the Prometheus UI.

---

## 📉 Grafana Dashboard

A ready-to-import dashboard is provided at [`deploy/monitoring/grafana-dashboard.json`](https://github.com/spectrayan/spector/blob/main/deploy/monitoring/grafana-dashboard.json).

| Row | Panels |
|---|---|
| **JVM** | Heap used vs. committed · Non-heap and NIO buffer pools · Live threads |
| **Spector memory** | Memory count (stat + time series) · Pinned off-heap bytes · Page faults by type · Cached namespace instances |
| **Routing** *(non-standalone only)* | Route lookups/s by mode · Average route lookup latency · Route rejections/s (not_owner, stale, fenced) |

### Importing

1. In Grafana, open **Dashboards → New → Import**.
2. Click **Upload dashboard JSON file** and select `deploy/monitoring/grafana-dashboard.json` (or paste its contents).
3. Select your Prometheus datasource when prompted, then click **Import**.
4. Use the **Datasource** and **Instance** variables at the top of the dashboard to switch Prometheus sources or filter nodes.

The dashboard requires Grafana 10 or newer and only a Prometheus datasource.

---

## ☸️ Kubernetes: ServiceMonitor

The Helm chart ships a [Prometheus Operator](https://prometheus-operator.dev/) `ServiceMonitor` in `deploy/helm/spector/templates/servicemonitor.yaml`. It is controlled by `values.yaml`:

```yaml
# ── Observability ──
serviceMonitor:
  enabled: true
  interval: "15s"
```

When enabled, the chart renders a `ServiceMonitor` that:

- selects Services labeled `app.kubernetes.io/name: <chart name>`,
- scrapes the Service port named `api` (Service port `7070` → container port `7070`),
- uses path `/actuator/prometheus`, `serviceMonitor.interval` (default `15s`), and a `10s` scrape timeout.

```bash
# Install / upgrade with the ServiceMonitor enabled (the default)
helm upgrade --install spector deploy/helm/spector --set serviceMonitor.interval=30s

# Disable it on clusters without the Prometheus Operator CRDs
helm upgrade --install spector deploy/helm/spector --set serviceMonitor.enabled=false

# Inspect the rendered manifest without installing
helm template spector deploy/helm/spector --show-only templates/servicemonitor.yaml
```

Things to check in your cluster:

- **CRDs required.** The `monitoring.coreos.com/v1` `ServiceMonitor` CRD must be installed (e.g. via kube-prometheus-stack), otherwise the install fails. Set `serviceMonitor.enabled=false` if it is not.
- **Operator selection.** Many Prometheus Operator installs only pick up `ServiceMonitor`s carrying a specific label (for kube-prometheus-stack, commonly `release: <prometheus-release>`). The chart does not add such a label, so configure the Prometheus `serviceMonitorSelector` accordingly.
- **Which pods are scraped.** The main Service selects `gateway` pods in the default `split` topology and `owner` pods in `single-role` topology, so the ServiceMonitor scrapes those pods through that Service.
- **Authentication.** If `spector.auth.enabled=true`, add `/actuator/prometheus` to `SPECTOR_AUTH_PUBLIC_PATHS` as described [above](#authentication).

> [!NOTE]
> The ServiceMonitor configuration above is derived from the Helm chart templates. Scraping through the Prometheus Operator and routing metrics in a clustered (HA) deployment have **not** yet been verified at runtime; only local standalone scraping of `/actuator/prometheus` has been verified.
