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

import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';

@Component({
  selector: 'cortex-memory-cognitive-card',
  standalone: true,
  imports: [MatCardModule, MatIconModule, MatChipsModule],
  templateUrl: './memory-cognitive-card.component.html',
  styleUrl: './memory-cognitive-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MemoryCognitiveCardComponent {
  readonly memory = input.required<any>();

  readonly importancePct = computed(() => {
    const mem = this.memory();
    if (!mem) return 0;
    const imp = mem.importance;
    if (!isFinite(imp) || imp < 0 || imp > 10) return 0;
    return Math.round(imp * 10);
  });

  readonly valenceLabel = computed(() => {
    const mem = this.memory();
    if (!mem) return 'Neutral';
    if (mem.valence > 50) return 'Positive';
    if (mem.valence < -50) return 'Negative';
    return 'Neutral';
  });
}
