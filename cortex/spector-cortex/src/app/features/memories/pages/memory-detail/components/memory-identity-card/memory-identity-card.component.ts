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
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';

@Component({
  selector: 'cortex-memory-identity-card',
  standalone: true,
  imports: [
    MatCardModule,
    MatIconModule,
    MatChipsModule,
    MatTooltipModule,
  ],
  templateUrl: './memory-identity-card.component.html',
  styleUrl: './memory-identity-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MemoryIdentityCardComponent {
  readonly memory = input.required<any>();
  readonly tierColor = input.required<string>();
  readonly createdDateFormatted = input.required<string>();
  readonly ageLabel = input<string>('');
}
