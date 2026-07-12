import { Injectable, inject, signal, OnDestroy, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { CortexStateService } from './cortex-state.service';
import { SessionRecorderService } from './session-recorder.service';
import { NotificationService } from './notification.service';
import { LoggerService } from './logger.service';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';
import {
  QueryTraceEvent,
  SimdLaneEvent,
  MemoryDiagnosticEvent,
  GraphPulseEvent,
  ReflectCycleEvent,
  MemorySnapshotEvent,
  GpuKernelEvent,
  ClusterTopologyEvent,
  EmbeddingProjectionEvent,
  IngestionProgressEvent,
  IngestionCompletedEvent,
  AnyCortexEvent,
} from '../models/cortex-events';

/** SSE connection config. */
export interface EventStreamConfig {
  baseUrl: string;       // e.g. 'http://localhost:4200/api/v1'
  filter: string;        // e.g. 'cortex'
  maxRetries: number;
  initialRetryMs: number;
}

const DEFAULT_CONFIG: EventStreamConfig = {
  baseUrl: environment.sseBaseUrl || '/api/v1',
  filter: 'cortex,ingestion',
  maxRetries: 10,
  initialRetryMs: 60000, // 60 seconds between retries
};

@Injectable({ providedIn: 'root' })
export class EventStreamService implements OnDestroy {

  private readonly state = inject(CortexStateService);
  private readonly recorder = inject(SessionRecorderService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly notificationService = inject(NotificationService);
  private readonly log = inject(LoggerService);
  private readonly auth = inject(AuthService);

  readonly isConnected = signal(false);
  readonly retryCount = signal(0);

  private abortController: AbortController | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private config = DEFAULT_CONFIG;

  /** Connect to the SSE endpoint. */
  connect(config?: Partial<EventStreamConfig>): void {
    if (!isPlatformBrowser(this.platformId)) return;
    this.config = { ...DEFAULT_CONFIG, ...config };
    this.doConnect();
  }

  /** Disconnect and stop reconnection attempts. */
  disconnect(): void {
    this.clearRetryTimer();
    if (this.abortController) {
      this.abortController.abort();
      this.abortController = null;
    }
    this.isConnected.set(false);
    this.state.connectionStatus.set('disconnected');
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  // ── Internal ───────────────────────────────────────────────────────

  private doConnect(): void {
    const url = `${this.config.baseUrl}/events?filter=${this.config.filter}`;

    this.state.connectionStatus.set('reconnecting');

    // Use fetch with Authorization header (native EventSource cannot send headers)
    this.abortController = new AbortController();
    const token = this.auth.getAccessToken();
    const headers: Record<string, string> = {
      'Accept': 'text/event-stream',
    };
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    fetch(url, {
      headers,
      signal: this.abortController.signal,
    }).then(response => {
      if (!response.ok) {
        if (response.status === 401) {
          this.log.warn('EventStream', 'SSE connection rejected (401) — token may be expired');
          // Try to refresh token and reconnect
          this.auth.refreshToken().then(() => this.scheduleReconnect());
          return;
        }
        if (response.status === 404) {
          this.log.warn('EventStream', 'SSE events endpoint not found (404) — falling back to mock data simulation.');
          this.state.connectionStatus.set('connected');
          this.state.useMockData.set(true);
          return;
        }
        throw new Error(`SSE connection failed: ${response.status}`);
      }

      this.isConnected.set(true);
      this.retryCount.set(0);
      this.state.connectionStatus.set('connected');

      const reader = response.body?.getReader();
      if (!reader) return;

      const decoder = new TextDecoder();
      let buffer = '';

      const processChunk = (result: ReadableStreamReadResult<Uint8Array>): void => {
        if (result.done) {
          this.isConnected.set(false);
          this.state.connectionStatus.set('disconnected');
          this.scheduleReconnect();
          return;
        }

        buffer += decoder.decode(result.value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() ?? '';

        let currentEventType = '';
        let currentData = '';

        for (const line of lines) {
          if (line.startsWith('event:')) {
            currentEventType = line.substring(6).trim();
          } else if (line.startsWith('data:')) {
            currentData = line.substring(5).trim();
          } else if (line === '' && currentEventType && currentData) {
            // End of event — dispatch
            try {
              const data = JSON.parse(currentData);
              data.eventType = currentEventType;
              this.dispatchEvent(data as AnyCortexEvent);
            } catch (e) {
              this.log.warn('EventStream', `Failed to parse SSE event ${currentEventType}:`, e);
            }
            currentEventType = '';
            currentData = '';
          }
        }

        reader.read().then(processChunk);
      };

      reader.read().then(processChunk);

    }).catch(err => {
      if (err.name === 'AbortError') return; // deliberate disconnect
      this.isConnected.set(false);
      this.state.connectionStatus.set('disconnected');
      this.scheduleReconnect();
    });
  }

  private dispatchEvent(event: AnyCortexEvent): void {
    // Record if recording is active
    this.recorder.recordEvent(event);

    switch (event.eventType) {
      case 'cortex.query.trace':
        this.state.pushQueryTrace(event as QueryTraceEvent);
        break;
      case 'cortex.simd.lane':
        this.state.pushSimdEvent(event as SimdLaneEvent);
        break;
      case 'cortex.memory.diagnostic':
        this.state.pushMemoryDiag(event as MemoryDiagnosticEvent);
        break;
      case 'cortex.graph.pulse':
        this.state.pushGraphPulse(event as GraphPulseEvent);
        break;
      case 'cortex.reflect.cycle':
        this.state.pushReflect(event as ReflectCycleEvent);
        break;
      case 'cortex.memory.snapshot':
        this.state.pushMemorySnapshot(event as MemorySnapshotEvent);
        break;
      case 'cortex.gpu.kernel':
        this.state.pushGpuKernel(event as GpuKernelEvent);
        break;
      case 'cortex.cluster.topology':
        this.state.pushClusterTopology(event as ClusterTopologyEvent);
        break;
      case 'cortex.embedding.projection':
        this.state.pushEmbeddingProjection(event as EmbeddingProjectionEvent);
        break;
      case 'ingestion.progress':
        this.notificationService.onProgress(event as IngestionProgressEvent);
        break;
      case 'ingestion.completed':
        this.notificationService.onCompleted(event as IngestionCompletedEvent);
        break;
    }
  }

  private scheduleReconnect(): void {
    const retry = this.retryCount();
    if (retry >= this.config.maxRetries) {
      this.log.warn('EventStream', `Max retries (${this.config.maxRetries}) reached, giving up`);
      this.state.connectionStatus.set('disconnected');
      return;
    }

    // Flat retry interval (default 60s)
    const delayMs = this.config.initialRetryMs;

    this.log.info('EventStream', `Retry ${retry + 1}/${this.config.maxRetries} in ${delayMs / 1000}s`);
    this.state.connectionStatus.set('reconnecting');
    this.retryCount.update(r => r + 1);

    this.retryTimer = setTimeout(() => {
      this.doConnect();
    }, delayMs);
  }

  private clearRetryTimer(): void {
    if (this.retryTimer !== null) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
  }
}
