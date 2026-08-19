<!--
  ~ Copyright 2026 Spectrayan
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

# Spector Connector (`spector-connector`)

Apache Camel enterprise integration engine and declarative connector framework for Spector Cognitive Memory.

## Overview

The `spector-connector` module bridges external SaaS platforms, databases, document storage, and message streams into Spector's bio-inspired cognitive memory pipeline (`SpectorIngestionSink` &rarr; working, episodic, semantic, and procedural memory tiers).

All connectors are defined as declarative Apache Camel YAML route templates, dynamically loaded and managed at runtime with audit execution logging, multi-tenant isolation, and rate throttling.

## Supported Connectors & Protocols

### Tier 1: Direct, Local & Streaming Protocols
- **`direct`**: In-memory direct route dispatch for high-throughput programmatic ingestion.
- **`file-watch`**: Directory file watcher streaming `.txt`, `.md`, PDF, and DOCX documents with delta hashing and deduplication.
- **`db-query`**: SQL database poller supporting H2, PostgreSQL, MySQL, and Oracle with row-level splitting and dynamic cognitive record generation.
- **`rest-api-poll`**: Generic REST API poller with bearer/custom auth and configurable polling schedules.
- **`web-scraper`**: Web crawler & HTML scraper ingesting web pages and documentation.
- **`rss`**: RSS 2.0 & Atom feed consumer with entry-level splitting.
- **`webhook-receiver`**: Inbound Netty HTTP webhook endpoint with token bucket rate throttling.
- **`email-notify`**: SMTP notification dispatcher for alert delivery.

### Tier 2: Enterprise SaaS Integrations
- **`jira`**: Jira Cloud REST API poller with JQL filtering and `$.issues[*]` JSONPath splitting.
- **`confluence`**: Confluence Cloud space documentation poller with `$.results[*]` JSONPath splitting.
- **`github-ingest`**: GitHub commit and pull request stream ingestion via `$.[*]` JSONPath splitting.
- **`notion-pages`**: Notion database query ingestion via `$.results[*]` JSONPath splitting.
- **`salesforce`**: Salesforce SOQL query poller for CRM cases and customer records.
- **`google-drive`**: Google Drive v3 REST API poller for cloud documents and spreadsheets.
- **`sharepoint`**: Microsoft Graph API poller for enterprise document libraries.
- **`slack-ingest` / `slack-notify`**: Bi-directional Slack conversation ingestion and outbound channel alerting.

### Tier 3: Event Streaming & NoSQL
- **`kafka-consumer`**: Apache Kafka topic consumer for real-time event streaming.
- **`mongodb-poll`**: MongoDB change stream & document poller.
- **`s3-poll`**: AWS S3 bucket file poller.

## Architecture

```
 External Systems ───► Apache Camel Route Templates ───► SpectorIngestionSink
 (Jira, DB, RSS, HTTP)  (YAML Templates & Dynamic Routes)     │
                                                              ▼
                                                   Spector Cognitive Memory
                                                   ├── PII Scrubbing Filter
                                                   ├── SIMD Vector Embedding
                                                   ├── HNSW & BM25 Indexing
                                                   └── Multi-Tier Storage
```

## E2E Testing Harness

All connector templates include 100% deterministic local test suites running against embedded JDK `HttpServer`, in-memory H2 SQL engines, and Netty endpoints with zero external network dependencies:

- `DatabaseQueryIngestionE2ETest`: Multi-table SQL schemas & row splitting.
- `FileWatchIngestionE2ETest`: Local markdown wiki and doc ingestion.
- `RestAndScraperIngestionE2ETest`: REST JSON telemetry & HTML web scraping.
- `RssFeedIngestionE2ETest`: RSS 2.0 security advisories & CVE recall.
- `SaaSApiIngestionE2ETest`: Jira, Confluence, GitHub, Notion, Salesforce, Google Drive, SharePoint.
- `EmailAndWebhookIngestionE2ETest`: Netty HTTP webhooks & Slack notifications.
