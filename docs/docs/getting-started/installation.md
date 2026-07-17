---
title: "Installation — System Requirements & Setup"
description: "Install Spector: Java 25+, Maven 3.9+, system requirements, build from source, Docker, and platform-specific SIMD configuration."
---

# Installation

## System Requirements

| Requirement | Minimum |
|-------------|---------|
| JDK | 25+ (OpenJDK with Vector API) |
| Maven | 3.9+ |
| RAM | 512 MB (scales with dataset) |
| Disk | 100 MB + index data |

## Building from Source

```bash
git clone https://github.com/spectrayan/spector.git
cd spector
mvn clean install -DskipTests
```

## Running with JVM Flags

Spector uses incubator modules. The required JVM flags are configured in `pom.xml`, but if running manually:

```bash
java --add-modules jdk.incubator.vector \
     --enable-native-access=ALL-UNNAMED --enable-preview \
     -jar spector-dist/target/spector.jar --config spector.yml
```

## Server Configuration

Start with a custom YAML configuration or launch the default configuration:

```bash
java --add-modules jdk.incubator.vector \
     --enable-native-access=ALL-UNNAMED --enable-preview \
     -jar spector-dist/target/spector.jar --config custom-config.yml
```

## GPU Support

GPU acceleration requires:

- NVIDIA GPU with CUDA support

- CUDA toolkit installed

- Set `gpuEnabled=true` in configuration

The system falls back to CPU SIMD automatically when GPU is unavailable.

## Embedding Provider

Spector ships with an Ollama embedding provider. To enable auto-embedding:

1. Install [Ollama](https://ollama.ai)
2. Pull an embedding model: `ollama pull nomic-embed-text`
3. Configure the embedding endpoint in your application