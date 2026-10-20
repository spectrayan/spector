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

import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';

@Component({
  selector: 'cortex-memory-tier-summary',
  standalone: true,
  imports: [
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
  ],
  templateUrl: './memory-tier-summary.component.html',
  styleUrl: './memory-tier-summary.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MemoryTierSummaryComponent {
  readonly tiers = input<string[]>(['WORKING', 'EPISODIC', 'SEMANTIC', 'PROCEDURAL']);
  readonly tierColors = input<Record<string, string>>({});
  readonly tierCounts = input<Record<string, number>>({});
  readonly tombstoneRatios = input<Record<string, number>>({});

  readonly vacuum = output<string>();

  formatRatio(ratio: number): string {
    return (ratio * 100).toFixed(1) + '%';
  }
}
