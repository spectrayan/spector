/*
 * Copyright 2026 Spectrayan — Apache 2.0
 */

import { SpectorClient, MemoryTier } from '@spectrayan/spector-client';

/**
 * Returns tool definitions for Spector Cognitive Memory
 * compatible with Vercel AI SDK or similar function calling frameworks.
 */
export function createSpectorTools(client: SpectorClient) {
  return {
    remember: {
      description: 'Store a memory, fact, or conversation takeaway into persistent cognitive memory.',
      parameters: {
        type: 'object',
        properties: {
          text: { type: 'string', description: 'The text content of the memory' },
          tier: {
            type: 'string',
            enum: ['WORKING', 'EPISODIC', 'SEMANTIC', 'PROCEDURAL'],
            description: 'Cognitive tier for the memory',
          },
          tags: {
            type: 'array',
            items: { type: 'string' },
            description: 'Tags for categorization and fast filtering',
          },
        },
        required: ['text'],
      },
      execute: async ({ text, tier, tags }: { text: string; tier?: string; tags?: string[] }) => {
        return await client.memory.remember({
          text,
          tier: tier as MemoryTier,
          tags,
        });
      },
    },

    recall: {
      description: 'Recall relevant memories using fused cognitive scoring (vector similarity + Hebbian graph + decay).',
      parameters: {
        type: 'object',
        properties: {
          query: { type: 'string', description: 'Semantic search or recall prompt' },
          topK: { type: 'number', description: 'Number of results to retrieve (default: 5)' },
        },
        required: ['query'],
      },
      execute: async ({ query, topK }: { query: string; topK?: number }) => {
        return await client.memory.recall(query, { topK: topK ?? 5 });
      },
    },
  };
}

