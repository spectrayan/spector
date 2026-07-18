# spector-provider-api

`spector-provider-api` is the abstract Service Provider Interface (SPI) for model-agnostic text embedding and text generation (LLM) execution within the Spector ecosystem.

## Core Abstractions

- **`LlmProvider`**: Abstract interface for executing text generation/completion models.
- **`EmbeddingProvider`**: Abstract interface for vectorizing text chunks.
- **`ProviderFactory`**: Factory interface to dynamically discover, configure, and construct LLM and embedding provider instances based on configuration properties.
- **`LlmRequest` / `LlmResponse`**: Standard request and response structures supporting streaming, temperature controls, and system prompts.
- **`EmbeddingResult`**: Vector representations of text inputs.
