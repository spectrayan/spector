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

import { Component, ChangeDetectionStrategy, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'cortex-graph-hud',
  standalone: true,
  imports: [MatIconModule],
  templateUrl: './graph-hud.component.html',
  styleUrl: './graph-hud.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GraphHudComponent {
  readonly nodeCount = input<number>(0);
  readonly edgeCount = input<number>(0);
  readonly avgImportance = input<number>(0);
  readonly densityRatio = input<number>(0);
  readonly hebbianCount = input<number>(0);
  readonly temporalCount = input<number>(0);
  readonly entityCount = input<number>(0);
}
