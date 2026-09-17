# ADR-0041: Unified Connector Architecture for Ingestion

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-15 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

**Stakeholder**: Bharat (Project Lead)  
**Target Repositories**: `spectrayan/spector` (OSS) and `spectrayan/spector-enterprise` (Enterprise)  
**Related Issues**: #140, #141, #142, #143, #144, #145, #166, #167, #181, #218, #219, #241, #242, #243, #244, #266  

---

## 1. Context & Problem Statement

Data ingestion is the lifeblood of Spector's cognitive memory engine. Users need automated, event-driven pipelines that extract knowledge from enterprise document repositories, cloud object stores, ticketing systems, databases, and APIs.

Currently, Spector's connector ecosystem is in an inconsistent state:
1. In the open-source repository (`spectrayan/spector`), `ConnectorController` in `spector-synapse` is a mock in-memory controller that does not execute real ingestion pipelines. Apache Camel is missing from the Maven reactor, and no connector engine module exists.
2. In `spectrayan/spector-enterprise`, a full `spector-connector-engine` was developed with Apache Camel 4.11.0, YAML route templates, pre-flight connection probers, and ingestion sinks. However, it has not been ported to the OSS repository, leaving OSS users without data connectors and creating divergence.
3. Neither repository provides an agent tool (`CamelRouteInvoker`) to allow autonomous agents in Spector to trigger connector ingestion on demand.
4. Several high-value connector integrations requested in issues #218 (Confluence), #219 (Google Drive), #241 (SharePoint/OneDrive), #242 (Jira), #243 (Web Scraper), #244 (RSS/Atom), and #266 (Salesforce) require standardized YAML templates and extractor pipelines.

---

## 2. Decision & Architecture

We establish a unified, two-tiered connector architecture:

### 2.1 Core OSS Tier (`spector` -> `connectors/spector-connector-engine`)
The open-source core will include:
- **`CamelConnectorEngine`**: Standalone, non-Spring `DefaultCamelContext` manager capable of dynamically deploying, updating, starting, stopping, and removing route templates.
- **`RouteLifecycleService`**: Orchestrates template validation, parameter resolution, connectivity probing, and engine deployment.
- **`TemplateRegistry` & `YamlTemplateLoader`**: Loads route template definitions and descriptors from YAML specifications (`src/main/resources/templates/connectors/*.yaml`) with runtime validation via `TemplateDescriptorValidator`.
- **`SpectorIngestionSink`**: High-throughput Camel `Processor` bridging incoming exchange payloads directly into Spector's `IngestionTarget` and `EmbeddingProvider`, with PII scrubbing, chunk change detection, and Cognitive DLQ error handling.
- **`ConnectionProbers`**: Pre-flight connectivity testing for all supported connector protocols (Local Filesystem, HTTP REST, S3, JDBC, Kafka, MongoDB).
- **Standard Connector Catalog**:
  - `file-watch`: Local directory watcher with recursive glob filters.
  - `rest-api-poll`: Periodic REST HTTP polling with JSONPath extraction.
  - `github-ingest`: Git repository clone and markdown/code ingestion.
  - `s3-poll`: AWS S3 bucket polling with prefix filtering.
  - `webhook-receiver`: Generic HTTP webhook ingestion endpoint.
  - `db-query`: JDBC polling with incremental timestamp/ID tracking.
  - `notion-pages`: Notion API page and block ingestion.
  - `slack-ingest` & `slack-notify`: Inbound Slack events and outbound alert dispatching.
  - `kafka-consumer`: Streaming Apache Kafka event ingestion.
  - `mongodb-poll`: MongoDB query and change stream ingestion.
  - `email-notify`: SMTP notification dispatch.
  - `rss`: RSS/Atom feed polling with GUID deduplication.
  - `web-scraper`: Recursive web crawler with Jsoup HTML-to-Markdown extraction.
  - `confluence`, `jira`, `google-drive`, `sharepoint`, `salesforce`: Extended enterprise templates.

### 2.2 Enterprise Tier (`spector-enterprise` -> `connectors/spector-connector-engine`)
The enterprise tier extends OSS `spector-connector-engine` by layering enterprise-only capabilities:
- **`TenantMemoryRegistry`**: Per-tenant workspace routing, JIT paging of memory instances, and LRU memory cache eviction under memory constraints.
- **Tenant Resource Quotas**: Hard rate-limits, chunk quotas, and tenant isolation policies.
- **Enterprise Credential Providers**: HashiCorp Vault, AWS Secrets Manager, and Azure Key Vault resolvers.
- **Cross-Cluster Replication**: Streaming change data capture (CDC) to secondary Spector clusters.

### 2.3 Synapse & Agent Tooling (`spector-synapse`)
- **`ConnectorController`**: Refactored to delegate directly to `RouteLifecycleService` and `TemplateRegistry`.
- **`CamelRouteInvoker`**: Registered as an `AgentTool` (`@Component`) allowing LLM agents in Spector to discover running routes and invoke connector jobs on demand with parameters.

---

## 3. Component Interaction Diagram

```mermaid
graph TD
    subgraph "Clients & Agents"
        CORTEX["Cortex Management UI"]
        AGENT["Autonomous Agent Graph<br/>(CamelRouteInvoker Tool)"]
        REST["Synapse REST API<br/>(/api/v1/connectors)"]
    end

    subgraph "spector-connector-engine (OSS)"
        RLS["RouteLifecycleService"]
        CCE["CamelConnectorEngine<br/>(Apache Camel 4.11.0)"]
        TR["TemplateRegistry + YamlTemplateLoader"]
        PROBE["ConnectionProbers"]
        SINK["SpectorIngestionSink"]
        DLQ["CognitiveDlq"]
        BATCH["BatchIngestionRegistry"]
    end

    subgraph "Spector Core Engine"
        INGEST["spector-ingestion<br/>(IngestionPipeline)"]
        EMBED["EmbeddingProvider<br/>(Local / Ollama / Remote)"]
        KERNEL["spector-memory<br/>(Cognitive Kernel)"]
    end

    CORTEX --> REST
    REST --> RLS
    AGENT -->|AgentTool SPI| RLS
    RLS -->|validate| TR
    RLS -->|pre-flight test| PROBE
    RLS -->|deploy/stop| CCE

    CCE -->|direct:spector-ingest| SINK
    SINK -->|embed text| EMBED
    SINK -->|write vectors| INGEST
    INGEST --> KERNEL
    SINK -->|failures| DLQ
    SINK -->|saga tracking| BATCH
```

---

## 4. Consequences & Benefits

- **Unified Codebase**: Resolves divergence between OSS and Enterprise repos; eliminates mock controllers in OSS.
- **Zero Lock-in**: Connectors are declared in declarative YAML route templates utilizing Apache Camel's mature ecosystem of 300+ components.
- **Agentic Integration**: Autonomous agents can now act as active data ingestion managers via `CamelRouteInvoker`.
- **Fault-Tolerant Ingestion**: Cognitive DLQ and batch Saga rollback ensure no corrupted or orphaned memories persist on ingestion errors.
