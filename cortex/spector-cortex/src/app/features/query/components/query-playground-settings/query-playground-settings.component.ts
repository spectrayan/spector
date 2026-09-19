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

import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSliderModule } from '@angular/material/slider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';
import { CortexStateService } from '@core/services/cortex-state.service';
import { CognitiveProfile, PROFILE_PARAMS } from '@core/models/memory-types';

@Component({
  selector: 'cortex-query-playground-settings',
  standalone: true,
  imports: [
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatSelectModule,
    MatFormFieldModule,
    MatSliderModule,
    MatTooltipModule,
    MatDividerModule,
  ],
  templateUrl: './query-playground-settings.component.html',
  styleUrl: './query-playground-settings.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QueryPlaygroundSettingsComponent {
  protected readonly state = inject(CortexStateService);

  protected readonly CognitiveProfile = CognitiveProfile;
  protected readonly profileParams = PROFILE_PARAMS;
  protected readonly profiles = Object.values(CognitiveProfile);

  protected readonly namespaces = [
    { value: 'default', label: 'Default Core Namespace' },
    { value: 'finance', label: 'Finance & Projection' },
    { value: 'engineering-team-a', label: 'Engineering Team A' },
    { value: 'compliance-vault', label: 'Compliance & Governance' },
    { value: 'wealth-management', label: 'Wealth Management Archives' },
  ];

  protected getActiveProfileDescription(): string {
    const profile = this.state.activeProfile();
    return PROFILE_PARAMS[profile]?.description ?? 'Custom recall parameters';
  }

  protected getActiveProfileParams() {
    const profile = this.state.activeProfile();
    return PROFILE_PARAMS[profile];
  }
}
