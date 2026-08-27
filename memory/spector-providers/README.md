# spector-providers

`spector-providers` provides out-of-the-box implementations of LLM and embedding providers integrated with various backends (Ollama, OpenAI, Google Gemini, Anthropic, Mistral, and Azure OpenAI) using LangChain4j and native integrations.

## Features

- **In-Process Native ONNX Embedder**: Sub-5ms zero-network dense vector generation running directly inside JVM memory with support for 384, 768, 1024 dimensions, native tokenization, and Java Vector API SIMD mean-pooling.
- **Multi-LLM Support**: Built-in support for OpenAI, Google Gemini, Anthropic, Mistral, Azure, and Ollama.
- **Dynamic Connection & Custom Headers**: Fully configurable custom headers and network proxies for enterprise integration.
- **Flexible Clients**: Reflective lookup for Spring Boot environment-managed `RestClient` / `WebClient` instances, falling back to Java's native JDK `HttpClient` in standalone CLI/stdio deployments.

## In-Process Native ONNX Embedding Configuration

```yaml
spector:
  provider:
    embedding:
      type: onnx                         # Activates in-process native ONNX embedder
      model: all-MiniLM-L6-v2            # Model name (or bge-small-en-v1.5, bge-base-en-v1.5, bge-large-en-v1.5)
      dimensions: 384                    # 384, 768, 1024, or custom model dimensions
      model-path: ~/.spector/models/all-minilm-l6-v2.onnx  # Optional local path
      execution-provider: CPU            # CPU, DIRECTML, or CUDA
      intra-op-threads: 0                # 0 = auto-detect available CPU cores
```
