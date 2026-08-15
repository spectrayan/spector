# spector-commons 📄

> **Ingestion utilities, text tokenizers, semantic chunkers, and document content extractors for Spector.**

`spector-commons` handles the preprocessing phase of document ingestion. It parses raw file formats (HTML, PDF, plain text), extracts core text content, and chunks it using character, token-level, or streaming boundaries to fit model context windows before embedding generation.

---

## 🏗️ Core Architecture & Roles

1. **Semantic Chunkers (`TextChunker` / `TokenChunker`):** Segments large text blocks into overlapping passages to maintain query context and respect model token limits.
2. **Streaming Chunkers (`StreamingChunker`):** High-throughput chunking controller designed to ingest streams of tokens/characters with sliding context windows.
3. **Content Extraction (`ContentExtractor` / `PdfDocumentReader`):** Pure Java, zero-dependency HTML parser and PDF decoder designed to extract structured text without heavy external libraries.
4. **Template Engine (`TemplateEngine` / `HandlebarsTemplateEngine`):** Universal, thread-safe Handlebars templating subsystem for dynamic Markdown formatting, LLM prompt engineering, and MCP tool responses with AST caching and custom format helpers.

---

## 🚀 Key APIs

### Template Engine Rendering
```java
// Standalone default engine (loads from classpath /templates/*.hbs)
TemplateEngine engine = TemplateEngine.createDefault();

// 1. Render classpath template
String output = engine.render("mcp/memory-status", Map.of(
    "totalMemories", 1024,
    "score", 9.856
));

// 2. Render inline template string with custom helpers
String prompt = engine.renderInline("""
    == SYSTEM PROMPT ==
    Name: {{default soul.name "Assistant"}}
    Score: {{formatDecimal score "%.2f"}}
    Boost: {{formatMult multiplier}}
    Tags: {{join tags ", "}}
    """, Map.of(
    "soul", Map.of("name", "Jarvis"),
    "score", 4.1234,
    "multiplier", 1.5,
    "tags", List.of("memory", "cognitive")
));
```

### Token-level Overlapping Chunking
```java
String text = "Large document content...";
int maxTokens = 256;
int overlap = 32;

List<Chunk> chunks = TokenChunker.chunk(text, maxTokens, overlap);
for (Chunk chunk : chunks) {
    System.out.printf("Chunk %d (%d tokens) -> %s%n", chunk.index(), chunk.tokenCount(), chunk.text());
}
```

### Pure Java PDF Reading
```java
byte[] pdfBytes = ...;
String extractedText = PdfDocumentReader.readText(pdfBytes);
```
