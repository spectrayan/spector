# Spector Framework Adapters & Examples

Clean, production-ready examples connecting Spector Cognitive Memory to major AI agent frameworks.

## Python: LangChain / LangGraph Adapter

Located in [`examples/python/langchain_spector.py`](python/langchain_spector.py).

Provides a `SpectorLangChainMemory` class that connects to Spector Synapse, saving episodic conversation history and recalling context using Spector's fused multi-tiered scoring.

```python
from examples.python.langchain_spector import SpectorLangChainMemory

memory = SpectorLangChainMemory(base_url="http://localhost:7070", top_k=5)

# Save context
memory.save_context({"input": "What is Panama Vector API?"}, {"output": "SIMD hardware acceleration in Java."})

# Load context for a query
context = memory.load_memory_variables({"input": "How does Spector accelerate vector search?"})
print(context["history"])
```

## TypeScript: Vercel AI SDK Tools

Located in [`examples/typescript/vercel_ai_tools.ts`](typescript/vercel_ai_tools.ts).

Exports `createSpectorTools(client)` which provides `remember` and `recall` tool definitions ready to pass into `generateText` or `streamText` in the Vercel AI SDK.

```typescript
import { SpectorClient } from '@spectrayan/spector-client';
import { createSpectorTools } from './typescript/vercel_ai_tools';

const client = SpectorClient.createDefault('http://localhost:7070');
const tools = createSpectorTools(client);

// Use in AI agent
// const result = await generateText({ model: ..., tools, prompt: ... });
```

