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
import { DebugLine } from '../../models/graph-explorer.models';

@Component({
  selector: 'cortex-graph-debug-console',
  standalone: true,
  imports: [MatIconModule],
  templateUrl: './graph-debug-console.component.html',
  styleUrl: './graph-debug-console.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GraphDebugConsoleComponent {
  readonly debugLines = input<DebugLine[]>([]);

  readonly close = output<void>();
}
