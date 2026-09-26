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

## 1. Context

Spector interacts with diverse external messaging channels (Slack, Telegram, Discord, Email, Webhooks) to receive incoming user requests and dispatch proactive agent notifications. Meanwhile, the `spector-connector` module provides a production-grade Apache Camel runtime (`CamelConnectorEngine`, `RouteLifecycleService`, `ConnectorExecutionAuditNotifier`).

## 2. Problem Statement

Prior channel implementations in `spector-synapse` relied on ad-hoc HTTP client stubs, unchecked `Map<?, ?>` casting, fragile string keys (`map.get("client_msg_id")`, `map.get("chat_id")`), and duplicated webhook logic without unified rate limiting, retries, or execution logging. This fragmented implementation introduced high maintenance overhead, lack of type safety, and inconsistent error-handling behavior across platforms.

## 3. Decision Drivers

- **Standardization**: Establish a single unified integration architecture across all external messaging channels and data connectors.
- **Type Safety**: Replace unchecked map manipulation with typed Jackson DTOs and `ChannelType` enums.
- **Reliability & Observability**: Leverage Apache Camel's error handling, retry/backoff policies, and MDC tracing.
- **Agent Integration**: Provide a seamless bridge between incoming channel messages, session TSID management, `ChatService`, and outgoing responses.
- **Agent Extensibility**: Enable LLM agents to trigger multi-channel notifications via `NotificationTool`.

## 4. Considered Options

### Option 1: Ad-hoc Spring HTTP Clients & Custom Webhook Controllers

- **Description**: Maintain independent Spring controllers and HTTP client beans for each messaging provider.
- **Advantages**: Simple initial prototyping per platform.
- **Disadvantages**: High boilerplate; independent rate limiters and error handlers needed per platform; no centralized audit trail; duplicate infrastructure.

### Option 2: Universal Apache Camel Connector Engine (Selected)

- **Description**: Unify all messaging channels on Apache Camel route definitions (`direct:channel-inbound` and `direct:channel-outbound-${channel}`), delegating lifecycle and retry management to `spector-connector`.
- **Advantages**: Reuses existing Camel engine; built-in connectors for Slack, Telegram, Mail; unified webhook ingestion; automated metrics and audit via `ConnectorExecutionAuditNotifier`; dynamic route lifecycle.
- **Disadvantages**: Requires defining route templates and normalizers.

## 5. Decision Outcome

**Chosen Option**: Option 2 (Universal Apache Camel Connector Engine).

### Architectural Implementation:

1. **Typed Domain Models**: Define `ChannelType` enum and typed payload models in `com.spectrayan.spector.synapse.channel.model`.
2. **Camel Channel Adapter**: Implement `CamelChannelAdapter` bridging channel I/O with Camel `direct:channel-inbound` and `direct:channel-outbound-${channel}`.
3. **Channel Router**: Refactor `ChannelRouter` to orchestrate dispatch to `ChatService` and handle outbound response delivery.
4. **Agent Notification Tool**: Implement `NotificationTool` for agent-driven alerts across registered channels.

### Positive Consequences

- Single integration architecture across data ingestion, outbound alerts, and messaging channels.
- All channels gain automated MDC tracing, metrics, and lifecycle controls.
- Compile-time type safety across all webhook payloads.

### Negative Consequences & Trade-offs

- Requires defining Apache Camel route templates and normalizers for new channels.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Ad-hoc HTTP Clients** | Simple per-client code | Boilerplate, fragmented error handling, no unified audit |
| **Option 2: Camel Engine (Selected)** | Enterprise EIP patterns, automated retry/audit, type-safe | Route template definitions required |

## 7. Implementation Plan

1. **Phase 1**: Define `ChannelType` and Jackson DTO models in `synapse/spector-synapse/channel`.
2. **Phase 2**: Author `CamelChannelAdapter` and configure inbound/outbound Camel routes.
3. **Phase 3**: Refactor `ChannelRouter` to dispatch to `ChatService`.
4. **Phase 4**: Implement `NotificationTool` and verify end-to-end webhook round-trips.

## 8. Code Reference & Verification

- **Primary Module(s)**: `synapse/spector-synapse`, `synapse/spector-connector`
- **Key Packages**: `com.spectrayan.spector.synapse.channel`, `com.spectrayan.spector.connector.core`
- **Classes**: `CamelChannelAdapter.java`, `ChannelRouter.java`, `NotificationTool.java`, `CamelConnectorEngine.java`
- **Verification Tests**: `CamelChannelAdapterTest.java`, `ChannelRouterIntegrationTest.java`
