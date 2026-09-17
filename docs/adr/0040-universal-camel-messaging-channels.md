# ADR-0040: Universal Apache Camel Messaging Channels

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

**Approver**: Bharat (Project Lead)  
**Target Module**: `synapse/spector-synapse` (`com.spectrayan.spector.synapse.channel`), integrated with `synapse/spector-connector`  
**Related Issues**: #146, #147, #169, #238

---

## 1. Context and Problem Statement
Prior channel implementations in `spector-synapse` relied on ad-hoc HTTP client stubs, unchecked `Map<?, ?>` casting, fragile string keys (`map.get("client_msg_id")`, `map.get("chat_id")`), and duplicated webhook logic without unified rate limiting, retries, or execution logging.

Meanwhile, the newly built `spector-connector` provides a robust, production-grade Apache Camel runtime (`CamelConnectorEngine`, `RouteLifecycleService`, `ConnectorExecutionAuditNotifier`). We need to unify messaging channels on this same foundation to eliminate technical debt, provide end-to-end type safety, and connect channels directly with the agent chat lifecycle.

---

## 2. Decision Drivers
- **Standardization**: Avoid multiple independent HTTP client and webhook handling approaches.
- **Type Safety**: Replace unchecked map manipulation with typed Jackson DTOs and `ChannelType` enum.
- **Reliability & Observability**: Leverage Apache Camel's error handling, retry/backoff, and MDC tracing.
- **Agent Integration**: Seamless bridge between incoming channel messages, session Tsid management, `ChatService`, and outgoing responses.
- **Extensibility**: Enable LLM agents to trigger multi-channel notifications via `NotificationTool` (#238).

---

## 3. Considered Options
1. **Option 1: Ad-hoc Spring HTTP Clients / Custom Webhook Controllers**
   - *Pros*: Simple at first glance.
   - *Cons*: High boilerplate, independent rate limiters and error handlers needed per platform, no centralized audit trail, duplicate infrastructure.
2. **Option 2: Unify on Apache Camel Connector Engine (Selected)**
   - *Pros*: Reuses existing Camel engine; built-in connectors for Slack, Telegram, Mail; unified webhook ingestion; automated metrics and audit via `ConnectorExecutionAuditNotifier`; dynamic route lifecycle; clean decoupling through `direct:channel-inbound` and `direct:channel-outbound-${channel}`.
   - *Cons*: Requires defining route templates and normalizers.

---

## 4. Decision
We adopt **Option 2**:
- Define `ChannelType` enum and typed payload models in `com.spectrayan.spector.synapse.channel.model`.
- Implement `CamelChannelAdapter` bridging channel I/O with Camel `direct:channel-inbound` and `direct:channel-outbound-${channel}`.
- Refactor `ChannelRouter` to orchestrate dispatch to `ChatService` and handle outbound response delivery.
- Implement `NotificationTool` for agent-driven alerts.

---

## 5. Consequences
- **Positive**: Single integration architecture across data ingestion, outbound alerts, and messaging channels. All channels gain tracing, metrics, and lifecycle controls.
- **Negative**: Existing stub tests must be updated to test against the typed Camel channel architecture.
