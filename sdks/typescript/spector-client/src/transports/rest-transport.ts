/*
 * Copyright 2026 Spectrayan — Apache 2.0
 */

import { ClientOptions, SseEvent } from '../models';
import {
  MemoryNotFoundError,
  SpectorAuthError,
  SpectorClientError,
  SpectorServerError,
  SpectorValidationError,
  TransportError,
} from '../errors';
import { RequestOptions, Transport } from './transport';

export class RestTransport implements Transport {
  private readonly baseUrl: string;
  private readonly options: ClientOptions;

  constructor(options: ClientOptions = {}) {
    this.options = options;
    this.baseUrl = (options.baseUrl || 'http://localhost:7070').replace(/\/+$/, '');
  }

  private buildHeaders(): Record<string, string> {
    const headers: Record<string, string> = {
      Accept: 'application/json',
      ...this.options.headers,
    };

    if (this.options.apiKey) {
      headers['X-API-Key'] = this.options.apiKey;
    }
    if (this.options.bearerToken) {
      headers['Authorization'] = `Bearer ${this.options.bearerToken}`;
    }
    if (this.options.userId) {
      headers['X-User-Id'] = this.options.userId;
    }
    if (this.options.agentId) {
      headers['X-Agent-Id'] = this.options.agentId;
    }
    if (this.options.namespace) {
      headers['X-Namespace'] = this.options.namespace;
    }

    return headers;
  }

  async request<T>(method: string, path: string, options?: RequestOptions): Promise<T> {
    let url = `${this.baseUrl}/${path.replace(/^\/+/, '')}`;

    if (options?.queryParams) {
      const params = new URLSearchParams();
      for (const [key, value] of Object.entries(options.queryParams)) {
        if (value !== undefined && value !== null) {
          params.append(key, String(value));
        }
      }
      const qs = params.toString();
      if (qs) {
        url += `?${qs}`;
      }
    }

    const headers = this.buildHeaders();
    let body: string | undefined;

    if (options?.body !== undefined && options?.body !== null) {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(options.body);
    }

    const controller = typeof AbortController !== 'undefined' ? new AbortController() : undefined;
    const timeoutMs = this.options.timeout || 30000;
    const timeoutId = controller ? setTimeout(() => controller.abort(), timeoutMs) : undefined;

    try {
      const res = await fetch(url, {
        method: method.toUpperCase(),
        headers,
        body,
        signal: controller?.signal,
      });

      if (!res.ok) {
        let errorData: any = {};
        try {
          errorData = await res.json();
        } catch {
          errorData = { message: await res.text() };
        }

        const msg = errorData.message || errorData.detail || res.statusText || `HTTP ${res.status}`;

        if (res.status === 404) {
          const id = options?.queryParams?.id ? String(options.queryParams.id) : path.split('/').pop() || 'unknown';
          throw new MemoryNotFoundError(id, msg);
        } else if (res.status === 401 || res.status === 403) {
          throw new SpectorAuthError(msg, res.status);
        } else if (res.status === 400) {
          throw new SpectorValidationError(msg, errorData);
        } else if (res.status >= 500) {
          throw new SpectorServerError(msg, res.status, errorData);
        } else {
          throw new SpectorClientError(msg, res.status, errorData);
        }
      }

      if (res.status === 204) {
        return undefined as unknown as T;
      }

      const contentType = res.headers.get('content-type') || '';
      if (contentType.includes('application/json')) {
        return (await res.json()) as T;
      }
      return (await res.text()) as unknown as T;
    } catch (err: any) {
      if (err instanceof SpectorClientError) {
        throw err;
      }
      if (err.name === 'AbortError') {
        throw new TransportError(`Request timed out after ${timeoutMs}ms: ${url}`);
      }
      throw new TransportError(`Failed to connect to Spector at ${url}: ${err.message}`, err);
    } finally {
      if (timeoutId) {
        clearTimeout(timeoutId);
      }
    }
  }

  async *streamEvents(path: string, options?: RequestOptions): AsyncIterable<SseEvent> {
    let url = `${this.baseUrl}/${path.replace(/^\/+/, '')}`;

    if (options?.queryParams) {
      const params = new URLSearchParams();
      for (const [key, value] of Object.entries(options.queryParams)) {
        if (value !== undefined && value !== null) {
          params.append(key, String(value));
        }
      }
      const qs = params.toString();
      if (qs) {
        url += `?${qs}`;
      }
    }

    const headers = this.buildHeaders();
    headers['Accept'] = 'text/event-stream';
    headers['Cache-Control'] = 'no-cache';

    const res = await fetch(url, {
      method: 'GET',
      headers,
    });

    if (!res.ok || !res.body) {
      throw new TransportError(`Failed to open event stream at ${url}: ${res.statusText}`);
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';

    let eventName = 'message';
    let dataLines: string[] = [];
    let eventId: string | undefined;
    let retryVal: number | undefined;

    try {
      while (true) {
        const { value, done } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split(/\r\n|\r|\n/);
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (!line.trim()) {
            if (dataLines.length > 0) {
              const fullData = dataLines.join('\n');
              let parsedData: unknown = fullData;
              try {
                parsedData = JSON.parse(fullData);
              } catch {
                // leave as string
              }

              yield {
                event: eventName,
                data: parsedData,
                id: eventId,
                retry: retryVal,
              };
            }
            eventName = 'message';
            dataLines = [];
            eventId = undefined;
            retryVal = undefined;
            continue;
          }

          if (line.startsWith(':')) {
            continue;
          }

          const colonIndex = line.indexOf(':');
          if (colonIndex !== -1) {
            const field = line.slice(0, colonIndex);
            let val = line.slice(colonIndex + 1);
            if (val.startsWith(' ')) {
              val = val.slice(1);
            }

            if (field === 'event') {
              eventName = val;
            } else if (field === 'data') {
              dataLines.push(val);
            } else if (field === 'id') {
              eventId = val;
            } else if (field === 'retry') {
              const num = parseInt(val, 10);
              if (!isNaN(num)) retryVal = num;
            }
          }
        }
      }
    } finally {
      reader.releaseLock();
    }
  }

  close(): void {}
}
