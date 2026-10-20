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

import { Component, input, signal, computed, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MarkdownPreviewComponent } from '@shared/components/markdown-preview/markdown-preview.component';

export type ContentFormatType = 'auto' | 'json' | 'markdown' | 'raw';

const MARKDOWN_PATTERNS: RegExp[] = [
  /^---\r?\n[\s\S]+?\r?\n---(?:\r?\n|$)/,
  /(?:^|\n)#{1,6}\s+\S+/,
  /(?:^|\n)```[\s\S]*?```/,
  /(?:^|\n)\s*[-*+]\s+\S+/,
  /(?:^|\n)\s*\d+\.\s+\S+/,
  /(?:^|\n)\s*>\s+\S+/,
  /(?:^|\n)\|[^\n]+\|[ \t]*\n[ \t]*\|[ \t]*:?[-]+:?[ \t]*\|/,
  /!?\[[^\]\n]+\]\((?:https?:\/\/|\/|#)[^)\n]+\)/,
  /\*\*[^*\n]+?\*\*|__[^_\n]+?__|`[^`\n]+`/,
];

@Component({
  selector: 'cortex-smart-content-viewer',
  standalone: true,
  imports: [
    CommonModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    MarkdownPreviewComponent,
  ],
  templateUrl: './smart-content-viewer.component.html',
  styleUrl: './smart-content-viewer.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SmartContentViewerComponent {
  readonly content = input<string>('');
  readonly defaultFormat = input<ContentFormatType>('auto');
  readonly maxHeight = input<string>('500px');

  readonly selectedTab = signal<ContentFormatType>('auto');
  readonly copied = signal<boolean>(false);

  constructor() {
    // Sync default format if set
    if (this.defaultFormat() !== 'auto') {
      this.selectedTab.set(this.defaultFormat());
    }
  }

  readonly detectedFormat = computed<'json' | 'markdown' | 'raw'>(() => {
    const raw = this.content();
    if (!raw) return 'raw';
    const trimmed = raw.trim();
    if (!trimmed) return 'raw';

    if (
      (trimmed.startsWith('{') && trimmed.endsWith('}')) ||
      (trimmed.startsWith('[') && trimmed.endsWith(']'))
    ) {
      try {
        JSON.parse(trimmed);
        return 'json';
      } catch {
        // Not valid JSON
      }
    }

    if (MARKDOWN_PATTERNS.some((pat) => pat.test(trimmed))) {
      return 'markdown';
    }

    return 'raw';
  });

  readonly effectiveFormat = computed<'json' | 'markdown' | 'raw'>(() => {
    const tab = this.selectedTab();
    if (tab === 'auto') {
      return this.detectedFormat();
    }
    return tab;
  });

  readonly wordCount = computed<number>(() => {
    const text = this.content().trim();
    return text ? text.split(/\s+/).filter(Boolean).length : 0;
  });

  readonly charCount = computed<number>(() => this.content().length);

  readonly parsedJsonResult = computed<{
    success: boolean;
    formatted?: string;
    error?: string;
  }>(() => {
    if (this.effectiveFormat() !== 'json') {
      return { success: false };
    }
    const raw = this.content().trim();
    try {
      const parsed = JSON.parse(raw);
      return { success: true, formatted: JSON.stringify(parsed, null, 2) };
    } catch (e: any) {
      return { success: false, error: e?.message || 'Invalid JSON syntax' };
    }
  });

  selectTab(tab: ContentFormatType): void {
    this.selectedTab.set(tab);
  }

  async copyContent(): Promise<void> {
    try {
      if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(this.content());
        this.copied.set(true);
        setTimeout(() => this.copied.set(false), 2000);
      }
    } catch {
      // Ignore clipboard write permission error
    }
  }
}
