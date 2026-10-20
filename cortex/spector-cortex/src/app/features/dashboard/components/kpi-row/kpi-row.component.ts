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

import { Component, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

export interface KpiCardData {
  title: string;
  value: string | number;
  description: string;
  icon: string;
  colorVar: string;
  bgRgba: string;
  testId: string;
}

@Component({
  selector: 'cortex-kpi-row',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatTooltipModule],
  templateUrl: './kpi-row.component.html',
  styleUrl: './kpi-row.component.scss',
})
export class KpiRowComponent {
  readonly totalCount = input<number>(0);
  readonly workingCount = input<number>(0);
  readonly episodicCount = input<number>(0);
  readonly semanticCount = input<number>(0);
  readonly proceduralCount = input<number>(0);
  readonly hebbianCount = input<string | number>(0);

  readonly cards = computed<KpiCardData[]>(() => [
    {
      title: 'Total Engrams',
      value: this.totalCount(),
      description: 'Across all 4 cognitive memory tiers',
      icon: 'psychology',
      colorVar: 'var(--primary)',
      bgRgba: 'rgba(10, 133, 255, 0.12)',
      testId: 'kpi-total-engrams',
    },
    {
      title: 'Volatile Working',
      value: this.workingCount(),
      description: 'Active context working buffer',
      icon: 'bolt',
      colorVar: 'var(--tier-working)',
      bgRgba: 'rgba(245, 158, 11, 0.12)',
      testId: 'kpi-volatile-working',
    },
    {
      title: 'Episodic Turns',
      value: this.episodicCount(),
      description: 'Session dialogues & historical turns',
      icon: 'auto_stories',
      colorVar: 'var(--tier-episodic)',
      bgRgba: 'rgba(167, 139, 250, 0.12)',
      testId: 'kpi-episodic-turns',
    },
    {
      title: 'Semantic Concepts',
      value: this.semanticCount(),
      description: 'Consolidated long-term knowledge',
      icon: 'category',
      colorVar: 'var(--tier-semantic)',
      bgRgba: 'rgba(139, 92, 246, 0.12)',
      testId: 'kpi-semantic-concepts',
    },
    {
      title: 'Procedural Rules',
      value: this.proceduralCount(),
      description: 'Action-condition behavioral engrams',
      icon: 'schema',
      colorVar: 'var(--tier-procedural)',
      bgRgba: 'rgba(59, 130, 246, 0.12)',
      testId: 'kpi-procedural-rules',
    },
    {
      title: 'Hebbian Network',
      value: this.hebbianCount(),
      description: 'Active associative nodes & synapses',
      icon: 'hub',
      colorVar: 'var(--accent-cyan)',
      bgRgba: 'rgba(34, 211, 238, 0.12)',
      testId: 'kpi-hebbian-network',
    },
  ]);
}
