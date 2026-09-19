/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-cortex/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
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
