# spector-rag 🤖

> **Zero-dependency Retrieval-Augmented Generation (RAG) context assembly and prompt pipelines.**

`spector-rag` implements zero-dependency RAG context orchestration. It executes semantic/hybrid searches on `SpectorEngine`, ranks and filters the top retrieved passages using listwise re-rankers, assembles context packages, and templates LLM prompts.

---

## 🏗️ Core Architecture & Roles

1. **Context Assembler (`ContextAssembler`):** Aggregates text passages from hybrid search, deduplicates entries, and trims contexts based on maximum LLM token budgets.
2. **Prompt Template Controller (`PromptTemplate`):** Maps search results to custom markdown templates:
   ```
   Answer the query based ONLY on the following context:
   ---
   [Context Passages]
   ---
   Query: [User Query]
   ```
3. **RAG Client (`RagClient`):** Handles remote connection streams with Ollama or external OpenAI-compatible services.

---

## 🚀 Key APIs

### Generating a Prompt Template
```java
// Retrieve context using SpectorEngine
SearchResponse response = engine.hybridSearch(query, queryVector, 5);

// Format prompt based on Markdown template
String contextPrompt = PromptTemplate.DEFAULT
    .withQuery(query)
    .withResults(response.results())
    .assemble();

// Send prompt directly to your LLM provider
String llmResponse = RagClient.generate(contextPrompt);
```
