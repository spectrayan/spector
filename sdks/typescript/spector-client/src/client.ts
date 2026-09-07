/*
 * Copyright 2026 Spectrayan — Apache 2.0
 */

import { ClientOptions } from './models';
import { MemoryClient } from './memory';
import { EventClient } from './events';
import { RestTransport, Transport } from './transports';
import { Configuration, MemoryApi } from './generated';

export class SpectorClient {
  public readonly memory: MemoryClient;
  public readonly events: EventClient;
  public readonly raw: MemoryApi;

  constructor(public readonly transport: Transport, options?: ClientOptions) {
    this.memory = new MemoryClient(transport);
    this.events = new EventClient(transport);

    const basePath = (options?.baseUrl || 'http://localhost:7070').replace(/\/+$/, '');
    const headers: Record<string, string> = { ...options?.headers };
    if (options?.apiKey) {
      headers['X-API-Key'] = options.apiKey;
    }
    if (options?.bearerToken) {
      headers['Authorization'] = `Bearer ${options.bearerToken}`;
    }

    const config = new Configuration({
      basePath,
      headers,
    });
    this.raw = new MemoryApi(config);
  }

  static builder(): SpectorClientBuilder {
    return new SpectorClientBuilder();
  }

  static createDefault(baseUrl: string = 'http://localhost:7070'): SpectorClient {
    return this.builder().withRest({ baseUrl }).build();
  }

  async close(): Promise<void> {
    await this.transport.close();
  }
}

export class SpectorClientBuilder {
  private options: ClientOptions = {
    baseUrl: 'http://localhost:7070',
  };
  private customTransport?: Transport;

  withRest(options: ClientOptions): this {
    this.options = { ...this.options, ...options };
    return this;
  }

  withTransport(transport: Transport): this {
    this.customTransport = transport;
    return this;
  }

  build(): SpectorClient {
    const transport = this.customTransport ?? new RestTransport(this.options);
    return new SpectorClient(transport, this.options);
  }
}
