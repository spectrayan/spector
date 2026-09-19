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

import {
  Component,
  ChangeDetectionStrategy,
  input,
  signal,
  computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ToolExecutionView } from '../../models/chat-turn.model';

export type { ToolExecutionView };

/**
 * ToolCardComponent
 *
 * Real-time tool badge and execution viewer for Spector Cortex (Requirement R4).
 * Displays tool identity, arguments, real-time status chip (running spinner,
 * success green badge, failure red alert), and an expandable 2 KiB preview with copy action.
 */
@Component({
  selector: 'cortex-tool-card, tool-card',
  standalone: true,
  imports: [
    CommonModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
  templateUrl: './tool-card.component.html',
  styleUrl: './tool-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'tool-card trace-step tool-badge',
    'data-testid': 'tool-card',
    '[class.running]': 'tool().status === "running"',
    '[class.success]': 'tool().status === "success"',
    '[class.failure]': 'tool().status === "failure"',
    '[class.expanded]': 'isExpanded()',
    role: 'article',
    'aria-label': 'Tool execution card',
  },
})
export class ToolCardComponent {
  // ── Inputs (Signal-based) ──
  readonly tool = input.required<ToolExecutionView>();

  // ── Local Interaction State ──
  readonly isExpanded = signal<boolean>(false);
  readonly copied = signal<boolean>(false);
  readonly activeTab = signal<'result' | 'arguments'>('result');

  // ── Tool Icon Based on Name ──
  readonly toolIcon = computed(() => {
    const name = this.tool().name;
    if (name.includes('recall') || name.includes('search')) return 'psychology';
    if (name.includes('remember') || name.includes('store')) return 'bookmark_add';
    if (name.includes('reinforce') || name.includes('graph')) return 'hub';
    if (name.includes('web') || name.includes('http')) return 'travel_explore';
    if (name.includes('file') || name.includes('disk')) return 'description';
    return 'construction';
  });

  // ── Quick Arguments Summary ──
  readonly argumentsSummary = computed(() => {
    const args = this.tool().arguments;
    if (!args || Object.keys(args).length === 0) return '';
    if ('query' in args && typeof args['query'] === 'string') {
      return `query: "${args['query']}"`;
    }
    if ('content' in args && typeof args['content'] === 'string') {
      const c = args['content'] as string;
      return `content: "${c.slice(0, 30)}${c.length > 30 ? '…' : ''}"`;
    }
    const firstKey = Object.keys(args)[0];
    return `${firstKey}: ${JSON.stringify(args[firstKey])}`;
  });

  // ── Formatted Payloads for Expanded Viewer ──
  readonly formattedArguments = computed(() => {
    return JSON.stringify(this.tool().arguments || {}, null, 2);
  });

  readonly formattedPreview = computed(() => {
    const p = this.tool().preview;
    if (!p) return '(empty output)';
    try {
      const parsed = JSON.parse(p);
      return JSON.stringify(parsed, null, 2);
    } catch {
      return p;
    }
  });

  // ── Actions ──

  toggleExpanded(): void {
    this.isExpanded.update((v) => !v);
  }

  setTab(tab: 'result' | 'arguments', event: Event): void {
    event.stopPropagation();
    this.activeTab.set(tab);
  }

  async copyContent(event: Event): Promise<void> {
    event.stopPropagation();
    const contentToCopy =
      this.activeTab() === 'result' ? this.formattedPreview() : this.formattedArguments();

    try {
      await navigator.clipboard.writeText(contentToCopy);
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    } catch {
      // Fallback if clipboard API not available
    }
  }
}
