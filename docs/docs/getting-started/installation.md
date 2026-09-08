---
title: "Installation & Distribution Matrix"
description: "Install Spector: Zero-install NPX runner, one-line scripts, Homebrew, Scoop, Docker Compose, and building from source."
---

# 📦 Installation & Setup

> **Choose the right installation path for your workflow: zero-install agent runner, one-line standalone binary, package manager, container, or client SDK.**

---

## 1. Zero-Install MCP Runner (for AI Agents)

If you are using Claude Desktop, Cursor, Windsurf, or Claude Code:

```bash
npx -y @spectrayan/spector mcp
```

The NPX launcher connects to a running local Spector Synapse daemon on `:7070` if healthy. If no daemon is running, it downloads `spector.jar` to run an in-process memory kernel with embedded ONNX vector embeddings (requires OpenJDK 25+).

---

## 2. One-Line Standalone Installers

Install the standalone `spector` CLI binary directly to `~/.spector/bin` and update your system `PATH`:

=== "Linux / macOS (POSIX)"
    ```bash
    curl -fsSL https://raw.githubusercontent.com/spectrayan/spector/main/scripts/install.sh | sh
    ```

=== "Windows (PowerShell)"
    ```powershell
    irm https://raw.githubusercontent.com/spectrayan/spector/main/scripts/install.ps1 | iex
    ```

Verify the installation:
```bash
spector doctor
```

---

## 3. Package Managers (Available with Release Tags)

> [!NOTE]
> Homebrew and Scoop package manager installations pull `spector.jar` from GitHub Releases. They activate once the release binary is published for your tag.

=== "macOS & Linux (Homebrew)"
    ```bash
    # Tap the official repository and install
    brew tap spectrayan/spector https://github.com/spectrayan/spector
    brew install spector
    ```

=== "Windows (Scoop)"
    ```powershell
    scoop install https://raw.githubusercontent.com/spectrayan/spector/main/packaging/scoop/spector.json
    ```

---

## 4. Docker & Docker Compose

Launch the complete cognitive stack (core memory engine on `:7070` and Cortex 3D neural explorer on `:7700`) with zero local prerequisites:

```bash
# Clone the repository
git clone https://github.com/spectrayan/spector.git
cd spector

# Launch engine and Cortex dashboard
docker compose up -d
```

See the [Docker Deployment Guide](../deployment/docker.md) for multi-stage build instructions and GPU acceleration.

---

## 5. Client SDKs (Zero Java Required)

If you are building applications that interact with Spector over HTTP/SSE:

=== "Python"
    ```bash
    pip install spector-client
    ```
    See the [Python SDK Guide](../sdk-usage/python-sdk.md).

=== "TypeScript / Node.js"
    ```bash
    npm install @spectrayan/spector-client
    ```
    See the [TypeScript SDK Guide](../sdk-usage/typescript-sdk.md).

=== "Java / Spring Boot"
    ```xml
    <dependency>
        <groupId>com.spectrayan</groupId>
        <artifactId>spector-client</artifactId>
        <version>0.1.0-beta</version>
    </dependency>
    ```

    > [!NOTE]
    > **Java Embed Dependency**: Applications should depend on `com.spectrayan:spector-client`. The root coordinate `com.spectrayan:spector` is the multi-module reactor parent POM and does not contain client classes.

    See the [Java Client Guide](../sdk-usage/java-client.md).

---

## 6. Building from Source (Engine Contributors)

### System Requirements
| Requirement | Minimum | Recommended |
|:---|:---|:---|
| **JDK** | OpenJDK 25+ (with Vector API & FFM) | Eclipse Temurin 25 |
| **Maven** | 3.9+ | 3.9.9 |
| **RAM** | 1 GB | 4 GB+ |
| **Architecture** | x86_64 (AVX2/AVX-512) or aarch64 (NEON) | SIMD hardware support |

### Build Instructions

```bash
# 1. Clone the repository
git clone https://github.com/spectrayan/spector.git
cd spector

# 2. Build the reactor and package spector.jar
mvn clean package -pl synapse/spector-cli -am -DskipTests

# 3. Launch the standalone CLI
java --add-modules jdk.incubator.vector \
     --enable-native-access=ALL-UNNAMED --enable-preview \
     -jar synapse/spector-cli/target/spector.jar doctor
```