/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '@angular/compiler';
import { TestBed } from '@angular/core/testing';
import { PLATFORM_ID, signal } from '@angular/core';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { EventStreamService } from './event-stream.service';
import { CortexStateService } from './cortex-state.service';
import { NotificationService } from './notification.service';
import { LoggerService } from './logger.service';
import { AuthService } from './auth.service';
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
  MetricsTickEvent,
  IngestionProgressEvent,
  IngestionCompletedEvent,
} from '../models/cortex-events';

describe('EventStreamService Dual-Topic Dispatching', () => {
  let service: EventStreamService;
  let mockState: any;
  let mockNotification: any;
  let mockLogger: any;
  let mockAuth: any;

  beforeEach(() => {
    mockState = {
      currentNamespace: signal('default'),
      connectionStatus: signal('disconnected'),
      useMockData: signal(false),
      pushQueryTrace: vi.fn(),
      pushSimdEvent: vi.fn(),
      pushMemoryDiag: vi.fn(),
      pushGraphPulse: vi.fn(),
      pushReflect: vi.fn(),
      pushMemorySnapshot: vi.fn(),
      pushGpuKernel: vi.fn(),
      pushClusterTopology: vi.fn(),
      pushEmbeddingProjection: vi.fn(),
      pushMetricsTick: vi.fn(),
    };

    mockNotification = {
      onProgress: vi.fn(),
      onCompleted: vi.fn(),
    };

    mockLogger = {
      debug: vi.fn(),
      info: vi.fn(),
      warn: vi.fn(),
      error: vi.fn(),
    };

    mockAuth = {
      getAccessToken: vi.fn().mockReturnValue('test-token'),
      refreshToken: vi.fn().mockResolvedValue(true),
    };

    TestBed.configureTestingModule({
      providers: [
        EventStreamService,
        { provide: CortexStateService, useValue: mockState },
        { provide: NotificationService, useValue: mockNotification },
        { provide: LoggerService, useValue: mockLogger },
        { provide: AuthService, useValue: mockAuth },
        { provide: PLATFORM_ID, useValue: 'browser' },
      ],
    });

    service = TestBed.inject(EventStreamService);
  });

  it('uses default filter with spector, cortex, and ingestion topics', () => {
    expect((service as any).config.filter).toBe('spector,cortex,ingestion');
  });

  describe('Dual-topic dispatching: spector.* and legacy cortex.* pairs', () => {
    it('dispatches both spector.query.trace and cortex.query.trace to pushQueryTrace', () => {
      const spectorEvent: QueryTraceEvent = {
        eventType: 'spector.query.trace',
        timestamp: Date.now(),
        nodeId: 'spector-0',
        queryText: 'test cue',
        cognitiveProfile: 'DEFAULT',
        synapticTagMask: 0,
        totalRecords: 100,
        afterTombstone: 90,
        afterTagGate: 80,
        afterValence: 70,
        afterDecay: 60,
        afterVectorDistance: 50,
        finalTopK: 10,
        hebbianActivated: 5,
        temporalLinked: 2,
        entityDiscovered: 3,
        latencyMicros: 1200,
      };
      (service as any).dispatchEvent(spectorEvent);
      expect(mockState.pushQueryTrace).toHaveBeenCalledWith(spectorEvent);

      const cortexEvent = { ...spectorEvent, eventType: 'cortex.query.trace' as const };
      (service as any).dispatchEvent(cortexEvent);
      expect(mockState.pushQueryTrace).toHaveBeenCalledWith(cortexEvent);
      expect(mockState.pushQueryTrace).toHaveBeenCalledTimes(2);
    });

    it('dispatches both spector.simd.lane and cortex.simd.lane to pushSimdEvent', () => {
      const spectorEvent: SimdLaneEvent = {
        eventType: 'spector.simd.lane',
        timestamp: Date.now(),
        nodeId: 'spector-0',
        kernelName: 'AVX2_COSINE',
        laneWidth: 8,
        vectorsProcessed: 1024,
        durationMicros: 450,
        fallbackNanos: 0,
      };
      (service as any).dispatchEvent(spectorEvent);
      expect(mockState.pushSimdEvent).toHaveBeenCalledWith(spectorEvent);

      const cortexEvent = { ...spectorEvent, eventType: 'cortex.simd.lane' as const };
      (service as any).dispatchEvent(cortexEvent);
      expect(mockState.pushSimdEvent).toHaveBeenCalledWith(cortexEvent);
      expect(mockState.pushSimdEvent).toHaveBeenCalledTimes(2);
    });

    it('dispatches both spector.memory.diagnostic and cortex.memory.diagnostic to pushMemoryDiag', () => {
      const spectorEvent: MemoryDiagnosticEvent = {
        eventType: 'spector.memory.diagnostic',
        timestamp: Date.now(),
        nodeId: 'spector-0',
        offHeapBytes: 10240,
        pinnedBytes: 512,
        jvmHeapUsed: 2048,
        jvmHeapMax: 8192,
        gpuAllocated: 0,
        gpuFree: 0,
        softPageFaults: 0,
        hardPageFaults: 0,
        workingCount: 10,
        episodicCount: 20,
        semanticCount: 30,
        proceduralCount: 5,
        hebbianEdges: 15,
        temporalLinks: 8,
        entityNodes: 12,
        entityEdges: 10,
        coActivationPairs: 6,
        stdpEdges: 4,
      };
      (service as any).dispatchEvent(spectorEvent);
      expect(mockState.pushMemoryDiag).toHaveBeenCalledWith(spectorEvent);

      const cortexEvent = { ...spectorEvent, eventType: 'cortex.memory.diagnostic' as const };
      (service as any).dispatchEvent(cortexEvent);
      expect(mockState.pushMemoryDiag).toHaveBeenCalledWith(cortexEvent);
      expect(mockState.pushMemoryDiag).toHaveBeenCalledTimes(2);
    });

    it('dispatches both spector.graph.pulse and cortex.graph.pulse to pushGraphPulse', () => {
      const spectorEvent: GraphPulseEvent = {
        eventType: 'spector.graph.pulse',
        timestamp: Date.now(),
        nodeId: 'spector-0',
        nodesVisited: 42,
        edgesTraversed: 64,
        maxDepth: 3,
        durationMicros: 850,
      };
      (service as any).dispatchEvent(spectorEvent);
      expect(mockState.pushGraphPulse).toHaveBeenCalledWith(spectorEvent);

      const cortexEvent = { ...spectorEvent, eventType: 'cortex.graph.pulse' as const };
      (service as any).dispatchEvent(cortexEvent);
      expect(mockState.pushGraphPulse).toHaveBeenCalledWith(cortexEvent);
      expect(mockState.pushGraphPulse).toHaveBeenCalledTimes(2);
    });

    it('dispatches both spector.reflect.cycle and cortex.reflect.cycle to pushReflect', () => {
      const spectorEvent: ReflectCycleEvent = {
        eventType: 'spector.reflect.cycle',
        timestamp: Date.now(),
        nodeId: 'spector-0',
        hebbianEdgesDecayed: 12,
        hebbianEdgesRemoved: 4,
        decayFactor: 0.95,
        durationMs: 120,
      };
      (service as any).dispatchEvent(spectorEvent);
      expect(mockState.pushReflect).toHaveBeenCalledWith(spectorEvent);

      const cortexEvent = { ...spectorEvent, eventType: 'cortex.reflect.cycle' as const };
      (service as any).dispatchEvent(cortexEvent);
      expect(mockState.pushReflect).toHaveBeenCalledWith(cortexEvent);
      expect(mockState.pushReflect).toHaveBeenCalledTimes(2);
    });

    it('dispatches both spector.memory.snapshot and cortex.memory.snapshot to pushMemorySnapshot', () => {
      const spectorEvent: MemorySnapshotEvent = {
        eventType: 'spector.memory.snapshot',
        timestamp: Date.now(),
        nodeId: 'spector-0',
        phase: 'pre-reflect',
        reflectCycleId: 'snap-1',
        hebbianEdgeCount: 10,
        temporalLinkCount: 5,
        entityNodeCount: 3,
        entityEdgeCount: 4,
        offHeapBytes: 100,
        tombstoneCount: 0,
        coActivationPairs: 2,
        stdpEdges: 1,
      };
      (service as any).dispatchEvent(spectorEvent);
      expect(mockState.pushMemorySnapshot).toHaveBeenCalledWith(spectorEvent);

      const cortexEvent = { ...spectorEvent, eventType: 'cortex.memory.snapshot' as const };
      (service as any).dispatchEvent(cortexEvent);
      expect(mockState.pushMemorySnapshot).toHaveBeenCalledWith(cortexEvent);
      expect(mockState.pushMemorySnapshot).toHaveBeenCalledTimes(2);
    });

    it('dispatches both spector.gpu.kernel and cortex.gpu.kernel to pushGpuKernel', () => {
      const spectorEvent: GpuKernelEvent = {
        eventType: 'spector.gpu.kernel',
        timestamp: Date.now(),
        nodeId: 'spector-0',
        streamIndex: 0,
        kernelName: 'CUDA_COSINE',
        durationMicros: 45,
        gridDim: [16, 1, 1],
        blockDim: [256, 1, 1],
        memoryTransferBytes: 1024,
      };
      (service as any).dispatchEvent(spectorEvent);
      expect(mockState.pushGpuKernel).toHaveBeenCalledWith(spectorEvent);

      const cortexEvent = { ...spectorEvent, eventType: 'cortex.gpu.kernel' as const };
      (service as any).dispatchEvent(cortexEvent);
      expect(mockState.pushGpuKernel).toHaveBeenCalledWith(cortexEvent);
      expect(mockState.pushGpuKernel).toHaveBeenCalledTimes(2);
    });

    it('dispatches both spector.cluster.topology and cortex.cluster.topology to pushClusterTopology', () => {
      const spectorEvent: ClusterTopologyEvent = {
        eventType: 'spector.cluster.topology',
        timestamp: Date.now(),
        nodeId: 'spector-0',
        nodes: [
          {
            nodeId: 'spector-0',
            status: 'active',
            shardCount: 2,
            memoryUsedBytes: 1024,
            queryRate: 5,
          },
        ],
        replicationLinks: [],
      };
      (service as any).dispatchEvent(spectorEvent);
      expect(mockState.pushClusterTopology).toHaveBeenCalledWith(spectorEvent);

      const cortexEvent = { ...spectorEvent, eventType: 'cortex.cluster.topology' as const };
      (service as any).dispatchEvent(cortexEvent);
      expect(mockState.pushClusterTopology).toHaveBeenCalledWith(cortexEvent);
      expect(mockState.pushClusterTopology).toHaveBeenCalledTimes(2);
    });

    it('dispatches both spector.embedding.projection and cortex.embedding.projection to pushEmbeddingProjection', () => {
      const spectorEvent: EmbeddingProjectionEvent = {
        eventType: 'spector.embedding.projection',
        timestamp: Date.now(),
        nodeId: 'spector-0',
        points: [{ id: 'm1', x: 0.1, y: 0.2, z: 0.3, tier: 'EPISODIC', importance: 0.8 }],
        queryProjection: null,
      };
      (service as any).dispatchEvent(spectorEvent);
      expect(mockState.pushEmbeddingProjection).toHaveBeenCalledWith(spectorEvent);

      const cortexEvent = { ...spectorEvent, eventType: 'cortex.embedding.projection' as const };
      (service as any).dispatchEvent(cortexEvent);
      expect(mockState.pushEmbeddingProjection).toHaveBeenCalledWith(cortexEvent);
      expect(mockState.pushEmbeddingProjection).toHaveBeenCalledTimes(2);
    });

    it('dispatches both spector.metrics.tick and cortex.metrics.tick to pushMetricsTick', () => {
      const spectorEvent: MetricsTickEvent = {
        eventType: 'spector.metrics.tick',
        timestamp: Date.now(),
        nodeId: 'spector-0',
        recallRate: 10,
        rememberRate: 5,
        reinforceRate: 2,
        forgetRate: 1,
      };
      (service as any).dispatchEvent(spectorEvent);
      expect(mockState.pushMetricsTick).toHaveBeenCalledWith(spectorEvent);

      const cortexEvent = { ...spectorEvent, eventType: 'cortex.metrics.tick' as const };
      (service as any).dispatchEvent(cortexEvent);
      expect(mockState.pushMetricsTick).toHaveBeenCalledWith(cortexEvent);
      expect(mockState.pushMetricsTick).toHaveBeenCalledTimes(2);
    });
  });

  describe('Ingestion events dispatching', () => {
    it('dispatches ingestion.progress to NotificationService.onProgress', () => {
      const progressEvent: IngestionProgressEvent = {
        eventType: 'ingestion.progress',
        timestamp: Date.now(),
        nodeId: 'spector-0',
        taskId: 'task-123',
        description: 'Ingesting corpus chunk',
        chunksStored: 10,
        totalChunks: 100,
        failures: 0,
        progressPercent: 10,
      };
      (service as any).dispatchEvent(progressEvent);
      expect(mockNotification.onProgress).toHaveBeenCalledWith(progressEvent);
    });

    it('dispatches ingestion.completed to NotificationService.onCompleted', () => {
      const completedEvent: IngestionCompletedEvent = {
        eventType: 'ingestion.completed',
        timestamp: Date.now(),
        nodeId: 'spector-0',
        taskId: 'task-123',
        description: 'Corpus ingestion finished',
        chunksStored: 100,
        failures: 0,
        durationMs: 4500,
        success: true,
      };
      (service as any).dispatchEvent(completedEvent);
      expect(mockNotification.onCompleted).toHaveBeenCalledWith(completedEvent);
    });
  });

  describe('Robustness and edge cases', () => {
    it('gracefully ignores unknown event types without throwing', () => {
      expect(() => {
        (service as any).dispatchEvent({
          eventType: 'unknown.synthetic.event',
          timestamp: Date.now(),
          nodeId: 'spector-0',
        } as any);
      }).not.toThrow();
    });
  });

  describe('SSE stream parsing', () => {
    const streamOf = (text: string) => {
      const bytes = new TextEncoder().encode(text);
      return new ReadableStream<Uint8Array>({
        start(controller) {
          controller.enqueue(bytes);
          controller.close();
        },
      });
    };

    const connectWith = async (text: string) => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(streamOf(text), { status: 200 })));
      vi.spyOn(service as any, 'scheduleReconnect').mockImplementation(() => undefined);
      (service as any).doConnect();
      await vi.waitFor(() => expect(mockState.connectionStatus()).toBe('disconnected'));
      vi.unstubAllGlobals();
    };

    it('dispatches data-only broadcast frames by their payload eventType, once per fan-out', async () => {
      const trace = JSON.stringify({ eventType: 'cortex.query.trace', queryText: 'q', finalTopK: 3 });
      await connectWith(
        'event:connected\ndata:connected\n\n' +
        // EventPublisher.broadcast() emits without an `event:` line, once per subscribed topic
        `data:${trace}\n\ndata:${trace}\n\ndata:${trace}\n\n` +
        'event:cortex.metrics.tick\ndata:{"recallRate":1}\n\n',
      );
      expect(mockState.pushQueryTrace).toHaveBeenCalledTimes(1);
      expect(mockState.pushQueryTrace.mock.calls[0][0].queryText).toBe('q');
      expect(mockState.pushMetricsTick).toHaveBeenCalledTimes(1);
      expect(mockLogger.warn).not.toHaveBeenCalled();
    });

    it('ignores data-only frames that carry no eventType', async () => {
      await connectWith('data:not-json\n\ndata:{"foo":1}\n\n');
      expect(mockState.pushQueryTrace).not.toHaveBeenCalled();
      expect(mockLogger.warn).not.toHaveBeenCalled();
    });
  });
});
