/*
 * Copyright 2026 Spectrayan — Apache 2.0
 */

import { SseEvent } from '../models';

export interface RequestOptions {
  queryParams?: Record<string, unknown>;
  body?: unknown;
}

export interface Transport {
  request<T>(method: string, path: string, options?: RequestOptions): Promise<T>;
  streamEvents(path: string, options?: RequestOptions): AsyncIterable<SseEvent>;
  close(): void | Promise<void>;
}
