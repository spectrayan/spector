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

@Component({
  selector: 'cortex-memory-consolidation-trace',
  standalone: true,
  imports: [MatCardModule, MatIconModule],
  templateUrl: './memory-consolidation-trace.component.html',
  styleUrl: './memory-consolidation-trace.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MemoryConsolidationTraceComponent {
  readonly memory = input.required<any>();

  readonly preConsolidatedText = computed(() => {
    const mem = this.memory();
    if (!mem || !mem.consolidated) return '';
    const text = mem.text;
    const dateStr =
      mem.timestampMs && mem.timestampMs > 946684800000 && mem.timestampMs < 4102444800000
        ? new Date(mem.timestampMs).toLocaleDateString()
        : 'Unknown date';
    return `[EPISODIC TRACE RAW INGEST - ${dateStr}]\nMemory payload ingested via system input stream. Text content reads: "${text}" with auxiliary context metadata.\nDuring sleep reflection, sensory details were consolidated, noise was pruned, and Hebbian associations were linked into the Semantic core cluster.`;
  });
}
