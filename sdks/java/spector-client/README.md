# Spector Java Client SDK

Lightweight, modern Java Client SDK for the **Spector Cognitive Memory & Search** platform. Generated directly from the canonical OpenAPI 3.1 specification (`docs/openapi.yaml`) with a thin ergonomic facade for JVM applications.

---

## Features

- **Standardized OpenAPI 3.1 Core**: Backed by OpenAPI Generator with the native `java.net.http.HttpClient` (Java 21+). Zero external HTTP transport dependencies.
- **Ergonomic Facade**: Fluent `SpectorClient` and `MemoryClient` with intuitive methods: `store()`, `remember()`, `recall()`, `search()`, `get()`, `forget()`, `reinforce()`, `suppress()`, `resolve()`.
- **Typed Domain Exceptions**: Transparent translation of HTTP errors into unchecked domain exceptions:
  - `SpectorClientException` (Base client runtime exception carrying HTTP status and response body)
  - `SpectorAuthException` (HTTP 401 / 403)
  - `MemoryNotFoundException` (HTTP 404)
  - `SpectorValidationException` (HTTP 400 / 422)
  - `SpectorServerException` (HTTP 5xx)
- **Universal API Extensibility**: Call `client.getApi(CustomControllerApi::new)` to access any OpenAPI endpoint across the entire Spector platform with shared authentication and timeouts.
- **AutoCloseable**: Clean lifecycle management with try-with-resources support.

---

## Installation

### Maven

```xml
<dependency>
    <groupId>com.spectrayan</groupId>
    <artifactId>spector-client</artifactId>
    <version>0.1.0-beta</version>
</dependency>
```

> [!IMPORTANT]
> **Java Embed Dependency**: For embedding Spector client access in JVM applications, depend **only** on `com.spectrayan:spector-client`.
> The root coordinate `com.spectrayan:spector` is the multi-module reactor parent POM and does not provide client SDK classes.

---

## Quickstart

### 1. Initialize the Client

```java
import com.spectrayan.spector.client.SpectorClient;
import java.time.Duration;

try (SpectorClient client = SpectorClient.builder()
        .baseUri("http://localhost:8080")
        .apiKey("spector-dev-key")
        .connectTimeout(Duration.ofSeconds(5))
        .readTimeout(Duration.ofSeconds(15))
        .build()) {

    // 2. Synchronous Memory Storage
    var storeResponse = client.memory().store("Spector uses biological memory tiers", List.of("architecture", "ai"));
    System.out.println("Stored memory ID: " + storeResponse.getId());

    // 3. Fused Cognitive Recall
    var results = client.memory().recall("biological memory", 5);
    for (var memory : results) {
        System.out.printf("[%s] %s (score: %.2f)%n", memory.getTier(), memory.getText(), memory.getCognitiveScore());
    }

    // 4. Single Record Retrieval
    var row = client.memory().get(storeResponse.getId());
    System.out.println("Retrieved text: " + row.getText());

    // 5. Reinforce Memory via Long-Term Potentiation (LTP)
    client.memory().reinforce(storeResponse.getId(), 1);

    // 6. Forget (Tombstone) Memory
    client.memory().forget(storeResponse.getId());
}
```

---

## Authentication

### API Key Authentication

```java
SpectorClient client = SpectorClient.builder()
        .baseUri("https://api.spector.internal:9090")
        .apiKey("your-api-key")
        .build();
```

### Bearer JWT Authentication

```java
SpectorClient client = SpectorClient.builder()
        .baseUri("https://api.spector.internal:9090")
        .bearerToken("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        .build();
```

---

## Accessing Generated APIs Directly

For specialized endpoints not yet wrapped in high-level facades, use `getApi()`:

```java
import com.spectrayan.spector.client.generated.api.TokenUsageControllerApi;

TokenUsageControllerApi tokenApi = client.getApi(TokenUsageControllerApi::new);
```

Or access the raw `MemoryApi`:

```java
var rawApi = client.memory().raw();
```

---

## License

Licensed under the Apache License, Version 2.0.
