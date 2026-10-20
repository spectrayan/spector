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

import { Component, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CortexStateService } from '@core/services/cortex-state.service';
import { AnyCortexEvent } from '@core/models/cortex-events';

export interface EventBadgeInfo {
  label: string;
  className: string;
}

@Component({
  selector: 'cortex-event-ticker',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
  ],
  templateUrl: './event-ticker.component.html',
  styleUrl: './event-ticker.component.scss',
})
export class EventTickerComponent {
  protected readonly state = inject(CortexStateService);
  readonly selectedEvent = signal<AnyCortexEvent | null>(null);

  readonly events = computed(() => this.state.recentEvents());
  readonly status = computed(() => this.state.connectionStatus());

  getBadge(eventType: string): EventBadgeInfo {
    const norm = eventType.replace(/^(spector|cortex)\./, '');
    switch (norm) {
      case 'query.trace':
        return { label: 'QUERY', className: 'badge-query' };
      case 'simd.lane':
        return { label: 'SIMD', className: 'badge-simd' };
      case 'memory.diagnostic':
        return { label: 'DIAG', className: 'badge-diag' };
      case 'graph.pulse':
        return { label: 'PULSE', className: 'badge-pulse' };
      case 'reflect.cycle':
        return { label: 'REFLECT', className: 'badge-reflect' };
      case 'memory.snapshot':
        return { label: 'SNAPSHOT', className: 'badge-snapshot' };
      case 'gpu.kernel':
        return { label: 'GPU', className: 'badge-gpu' };
      case 'cluster.topology':
        return { label: 'CLUSTER', className: 'badge-cluster' };
      case 'embedding.projection':
        return { label: 'EMBED', className: 'badge-embed' };
      case 'metrics.tick':
        return { label: 'METRIC', className: 'badge-metric' };
      default:
        return { label: norm.toUpperCase(), className: 'badge-default' };
    }
  }

  formatTimestamp(ts: number): string {
    if (!ts) return '';
    try {
      return new Date(ts).toLocaleTimeString();
    } catch {
      return '';
    }
  }

  formatPayloadPreview(event: AnyCortexEvent): string {
    const copy: any = { ...event };
    delete copy.eventType;
    delete copy.timestamp;
    delete copy.nodeId;
    const str = JSON.stringify(copy);
    return str.length > 80 ? str.slice(0, 77) + '...' : str;
  }

  selectEvent(event: AnyCortexEvent): void {
    if (this.selectedEvent() === event) {
      this.selectedEvent.set(null);
    } else {
      this.selectedEvent.set(event);
    }
  }

  closePayload(): void {
    this.selectedEvent.set(null);
  }

  clearEvents(): void {
    this.state.clearRecentEvents();
    this.selectedEvent.set(null);
  }
}
