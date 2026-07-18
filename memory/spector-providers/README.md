# spector-providers

`spector-providers` provides out-of-the-box implementations of LLM and embedding providers integrated with various backends (Ollama, OpenAI, Google Gemini, Anthropic, Mistral, and Azure OpenAI) using LangChain4j and native integrations.

## Features

- **Multi-LLM Support**: Built-in support for OpenAI, Google Gemini, Anthropic, Mistral, Azure, and Ollama.
- **Dynamic Connection & Custom Headers**: Fully configurable custom headers and network proxies for enterprise integration.
- **Flexible Clients**: Reflective lookup for Spring Boot environment-managed `RestClient` / `WebClient` instances, falling back to Java's native JDK `HttpClient` in standalone CLI/stdio deployments.
