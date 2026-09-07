/*
 * Copyright 2026 Spectrayan — Apache 2.0
 */

import { ClientOptions } from '../models';
import { TransportError } from '../errors';

export class McpTransport {
  private readonly endpoint: string;
  private readonly options: ClientOptions;
  private requestId = 0;

  constructor(endpoint: string, options: ClientOptions = {}) {
    this.endpoint = endpoint;
    this.options = options;
  }

  async callTool<T = unknown>(toolName: string, args: Record<string, unknown> = {}): Promise<T> {
    this.requestId += 1;
    const payload = {
      jsonrpc: '2.0',
      id: this.requestId,
      method: 'tools/call',
      params: {
        name: toolName,
        arguments: args,
      },
    };

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      ...this.options.headers,
    };
    if (this.options.apiKey) {
      headers['X-API-Key'] = this.options.apiKey;
    }
    if (this.options.bearerToken) {
      headers['Authorization'] = `Bearer ${this.options.bearerToken}`;
    }

    try {
      const res = await fetch(this.endpoint, {
        method: 'POST',
        headers,
        body: JSON.stringify(payload),
      });

      if (!res.ok) {
        throw new TransportError(`MCP HTTP request failed: ${res.statusText}`);
      }

      const json: any = await res.json();
      if (json.error) {
        throw new TransportError(`MCP error [${json.error.code}]: ${json.error.message}`);
      }
      return json.result as T;
    } catch (err: any) {
      if (err instanceof TransportError) throw err;
      throw new TransportError(`Failed to call MCP tool '${toolName}': ${err.message}`, err);
    }
  }

  close(): void {}
}
