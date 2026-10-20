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

import { Component, input, output, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MelSnippetTemplate, MelExecutionHistoryItem } from '@core/models/mel.models';
import { MEL_SNIPPET_TEMPLATES } from '../../models/mel-templates';

export interface TierStats {
  name: string;
  count: number;
  colorClass: string;
  tokenVar: string;
}

@Component({
  selector: 'cortex-mel-explorer-rail',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, MatButtonModule, MatTooltipModule],
  templateUrl: './mel-explorer-rail.component.html',
  styleUrl: './mel-explorer-rail.component.scss',
})
export class MelExplorerRailComponent {
  readonly namespace = input<string>('default');
  readonly history = input<MelExecutionHistoryItem[]>([]);
  readonly workingCount = input<number>(0);
  readonly episodicCount = input<number>(0);
  readonly semanticCount = input<number>(0);
  readonly proceduralCount = input<number>(0);

  readonly selectSnippet = output<MelSnippetTemplate>();
  readonly projectTier = output<string>();
  readonly selectHistory = output<MelExecutionHistoryItem>();

  protected readonly snippetFilter = signal<string>('');
  protected readonly selectedCategory = signal<string>('All');

  readonly categories = ['All', 'Retrieval', 'Ingestion & Plasticity', 'Consolidation & Lifecycle'];

  readonly tierStats = computed<TierStats[]>(() => [
    {
      name: 'WORKING',
      count: this.workingCount(),
      colorClass: 'tier-working',
      tokenVar: 'var(--tier-working)',
    },
    {
      name: 'EPISODIC',
      count: this.episodicCount(),
      colorClass: 'tier-episodic',
      tokenVar: 'var(--tier-episodic)',
    },
    {
      name: 'SEMANTIC',
      count: this.semanticCount(),
      colorClass: 'tier-semantic',
      tokenVar: 'var(--tier-semantic)',
    },
    {
      name: 'PROCEDURAL',
      count: this.proceduralCount(),
      colorClass: 'tier-procedural',
      tokenVar: 'var(--tier-procedural)',
    },
  ]);

  readonly filteredSnippets = computed(() => {
    const term = this.snippetFilter().toLowerCase().trim();
    const cat = this.selectedCategory();
    return MEL_SNIPPET_TEMPLATES.filter((s) => {
      const matchCat = cat === 'All' || s.category === cat;
      const matchTerm =
        !term ||
        s.label.toLowerCase().includes(term) ||
        s.verb.toLowerCase().includes(term) ||
        s.description.toLowerCase().includes(term);
      return matchCat && matchTerm;
    });
  });

  onSelectSnippet(snippet: MelSnippetTemplate): void {
    this.selectSnippet.emit(snippet);
  }

  onProjectTier(tierName: string): void {
    this.projectTier.emit(tierName);
  }

  onSelectHistory(item: MelExecutionHistoryItem): void {
    this.selectHistory.emit(item);
  }
}
