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
  output,
  computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ThinkingView } from '../../models/chat-turn.model';

export type { ThinkingView };

/**
 * ThinkingAccordionComponent
 *
 * Collapsible accordion for agentic reasoning trace (<think> CoT) in Spector Cortex (Requirement R4).
 * Features live elapsed timer during active thought generation, auto-collapses on first visible token,
 * supports manual user expansion override, and complies with WCAG AA accessibility standards.
 */
@Component({
  selector: 'cortex-thinking-accordion, thinking-accordion',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatTooltipModule],
  templateUrl: './thinking-accordion.component.html',
  styleUrl: './thinking-accordion.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'thinking-indicator thinking-accordion',
    'data-testid': 'thinking-trace',
    '[class.is-expanded]': 'isExpanded()',
    '[class.is-active]': 'isActivelyThinking()',
    role: 'region',
    'aria-label': 'Agent reasoning process',
  },
})
export class ThinkingAccordionComponent {
  // ── Inputs (Signal-based) ──
  readonly thinking = input.required<ThinkingView>();
  readonly isStreaming = input<boolean>(false);
  readonly hasVisibleTokens = input<boolean>(false);

  // ── Outputs (Signal-based) ──
  readonly toggle = output<boolean>();

  // ── Computed Signals ──
  readonly isExpanded = computed(() => !this.thinking().isCollapsed);

  readonly isActivelyThinking = computed(() => {
    return this.isStreaming() && !this.hasVisibleTokens() && this.thinking().text.length > 0;
  });

  readonly elapsedFormatted = computed(() => {
    const ms = this.thinking().elapsedMs || 0;
    if (ms < 1000) {
      return `${ms}ms`;
    }
    return `${(ms / 1000).toFixed(1)}s`;
  });

  // ── User Interaction ──
  onToggle(): void {
    const nextCollapsedState = !this.thinking().isCollapsed;
    this.toggle.emit(nextCollapsedState);
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.onToggle();
    }
  }
}
