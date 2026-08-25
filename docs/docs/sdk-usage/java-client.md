# ☕ Java SDK & Embedded Usage Guide

> **Type-safe, thread-safe Java access to Spector — embedded directly in your JVM application.** Embed cognitive memory and vector search directly with zero network overhead, or integrate seamlessly with Spring AI.

---

## 📦 Installation

**Embedded Memory Engine** (in-process, zero network overhead):

```xml
<dependency>
    <groupId>com.spectrayan</groupId>
    <artifactId>spector-memory</artifactId>
    <version>0.1.0-alpha</version>
</dependency>
```

**Spring AI Starter** (Spring Boot / Spring AI VectorStore):

```xml
<dependency>
    <groupId>com.spectrayan</groupId>
    <artifactId>spring-ai-starter-spector-store</artifactId>
    <version>0.1.0-alpha</version>
</dependency>
```

---

## ⚡ Embedded Memory (`SpectorMemory`)

For applications that want in-process hybrid search and biologically-inspired cognitive memory:

### 🔧 Creating the Engine

```java
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.ollama.OllamaEmbeddingProvider;

// Load default properties (overridden by spector.yml if present in the working directory)
SpectorProperties props = SpectorProperties.load();

// Provide an embedding provider instance
EmbeddingProvider embedder = new OllamaEmbeddingProvider(props.provider().embedding());

// Build embedded SpectorMemory instance
try (SpectorMemory memory = DefaultSpectorMemory.builder()
        .properties(props)
        .embeddingProvider(embedder)
        .build()) {
    // Memory engine is ready — zero network overhead, fully embedded inside the JVM
}
```

---

### 📥 Remembering Memories

```java
import com.spectrayan.spector.memory.model.MemoryType;

// Store a factual/semantic memory
String memoryId = memory.remember(
    "Java virtual threads enable high-throughput concurrent workflows with minimal memory footprint.",
    MemoryType.SEMANTIC
);

System.out.println("Stored memory: " + memoryId);
```

---

### 🔍 Cognitive Recall & Search

```java
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.TextSearchMode;
import com.spectrayan.spector.memory.model.CognitiveResult;

RecallOptions options = RecallOptions.builder()
        .topK(5)
        .textSearchMode(TextSearchMode.FULL_STACK)  // Enable BM25 + dense + ColBERT reranking
        .enableReranker(true)                       // ColBERT MaxSim reranking
        .minImportance(4.0f)                        // Filter out low-importance items
        .build();

List<CognitiveResult> memories = memory.recall("virtual threads concurrency", options);

for (CognitiveResult mem : memories) {
    System.out.printf("%s → %.4f (Tier: %s)%n", mem.id(), mem.score(), mem.memoryType());
}
```

---

### 🗑️ Forgetting Memories

```java
memory.forget(memoryId);
```

---

## 🎯 Complete Embedded Example

```java
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.config.SpectorProperties;

import java.util.List;

public class SpectorEmbeddedExample {
    public static void main(String[] args) throws Exception {
        SpectorProperties props = SpectorProperties.load();

        try (SpectorMemory memory = DefaultSpectorMemory.builder()
                .properties(props)
                .build()) {

            // Store memories
            String id1 = memory.remember(
                "Java virtual threads enable millions of concurrent tasks on modern JVMs.",
                MemoryType.SEMANTIC
            );
            String id2 = memory.remember(
                "The Vector API provides SIMD hardware acceleration for dot product and distance math.",
                MemoryType.SEMANTIC
            );

            // Recall
            List<CognitiveResult> results = memory.recall(
                "SIMD hardware acceleration",
                RecallOptions.builder().topK(5).build()
            );

            System.out.println("Results:");
            for (var r : results) {
                System.out.printf("  %s (%.4f): %s%n", r.id(), r.score(), r.text());
            }

            // Cleanup
            memory.forget(id1);
            memory.forget(id2);
        }
    }
}
```

---

## 🔗 See Also

- [Spring AI Integration](spring-ai.md) — Spring AI VectorStore adapter
- [MCP Server Guide](mcp-server.md) — Connect AI agents to Spector over STDIO / HTTP
- [REST API Reference](../api-reference/rest-endpoints.md) — Synapse HTTP endpoints
- [Configuration Guide](../configuration/parameters.md) — All engine configuration parameters
- [Getting Started](../getting-started/quickstart.md) — Quick start guide