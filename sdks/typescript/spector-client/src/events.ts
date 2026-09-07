/*
 * Copyright 2026 Spectrayan — Apache 2.0
 */

import { SseEvent } from './models';
import { Transport } from './transports';

export interface EventStreamOptions {
  filter?: string | string[];
}

export class EventClient {
  constructor(private readonly transport: Transport) {}

  /**
   * Streams real-time Server-Sent Events from the Spector Synapse event stream.
   *
   * @example
   * ```typescript
   * for await (const event of client.events.stream({ filter: ['memory', 'cortex'] })) {
   *   console.log('Event:', event.event, event.data);
   * }
   * ```
   */
  async *stream(options: EventStreamOptions = {}): AsyncIterable<SseEvent> {
    const filterParam = Array.isArray(options.filter)
      ? options.filter.join(',')
      : options.filter;

    const queryParams: Record<string, unknown> = {};
    if (filterParam) {
      queryParams['filter'] = filterParam;
    }

    yield* this.transport.streamEvents('/api/v1/events', { queryParams });
  }
}
