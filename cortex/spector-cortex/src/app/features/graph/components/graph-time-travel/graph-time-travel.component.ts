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

import { Component, ChangeDetectionStrategy, input, model, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatSliderModule } from '@angular/material/slider';
import { MatTooltipModule } from '@angular/material/tooltip';

@Component({
  selector: 'cortex-graph-time-travel',
  standalone: true,
  imports: [
    FormsModule,
    MatIconModule,
    MatSliderModule,
    MatTooltipModule,
  ],
  templateUrl: './graph-time-travel.component.html',
  styleUrl: './graph-time-travel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GraphTimeTravelComponent {
  readonly timeTravelPlaying = input<boolean>(false);
  readonly timeTravelTimestamp = input<number>(0);
  readonly oldestTimestamp = input<number>(0);
  readonly newestTimestamp = input<number>(Date.now());

  readonly timeTravelSpeed = model<number>(1);

  readonly togglePlay = output<void>();
  readonly scrub = output<number>();
  readonly exit = output<void>();

  formatTimestamp(ms: number): string {
    if (ms <= 0) return '—';
    const d = new Date(ms);
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
  }
}
