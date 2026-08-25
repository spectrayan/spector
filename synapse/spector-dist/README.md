# spector-dist 📦

> **Single fat JAR distribution — all Spector modules in one deployable artifact.**

`spector-dist` uses the Maven Shade Plugin to produce a single executable JAR that includes the MCP server, CLI, runtime, engine, memory, and all dependencies.

---

## 🏗️ What's Included

```mermaid
graph TD
    DIST["spector-dist<br/><i>Fat JAR</i>"]
    DIST --> MCP["spector-mcp<br/><i>MCP Server (stdio)</i>"]
    DIST --> CLI["spector-cli<br/><i>spectorctl</i>"]
    DIST --> RUNTIME["spector-runtime<br/><i>engine + memory</i>"]
    DIST --> NODE["spector-node<br/><i>Armeria server</i>"]

    RUNTIME --> ENGINE["spector-engine"]
    RUNTIME --> MEMORY["spector-memory"]
    RUNTIME --> INGESTION["spector-ingestion"]
```

---

## 🚀 Building

```bash
# Build the fat JAR (skip tests for speed)
mvn package -pl spector-dist -am -DskipTests
```

Output: `spector-dist/target/spector.jar`

## 🚀 Running

```bash
# Start the MCP server (for AI agents — Claude, Cursor, etc.)
java --add-modules jdk.incubator.vector \
  --enable-native-access=ALL-UNNAMED --enable-preview \
  -jar spector-dist/target/spector.jar \
  --config spector.yml

# Start the Armeria node (REST + gRPC + SSE)
java --add-modules jdk.incubator.vector \
  --enable-native-access=ALL-UNNAMED --enable-preview \
  -cp spector-dist/target/spector.jar \
  com.spectrayan.spector.node.SpectorNode

# Start the file ingestion pipeline
java --add-modules jdk.incubator.vector \
  --enable-native-access=ALL-UNNAMED --enable-preview \
  -cp spector-dist/target/spector.jar \
  com.spectrayan.spector.ingestion.FileIngestionMain \
  --config spector.yml --root .
```

---

## 📊 JAR Contents

The shaded JAR contains all transitive dependencies:

| Component | Modules |
|-----------|---------|
| **Foundation & Acceleration** | `spector-bom`, `spector-commons`, `spector-config`, `spector-core`, `spector-cpu`, `spector-gpu`, `spector-hdc`, `spector-index`, `spector-events` |
| **Cognitive Memory** | `spector-memory`, `spector-provider-api`, `spector-providers`, `spector-ingestion`, `spector-inspect`, `spector-metrics` |
| **Runtime & Gateways** | `spector-runtime`, `spector-synapse`, `spector-connector`, `spector-mcp`, `spector-cli`, `spector-client`, `spector-spring`, `spector-batch` |
