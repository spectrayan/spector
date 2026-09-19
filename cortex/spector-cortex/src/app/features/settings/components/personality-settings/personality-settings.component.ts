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

import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatSliderModule } from '@angular/material/slider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatDividerModule } from '@angular/material/divider';
import { SettingsStateService } from '../../services/settings-state.service';
import {
  STRESS_RESPONSE_OPTIONS,
  COMMUNICATION_STYLE_OPTIONS,
} from '../../models/settings.models';

@Component({
  selector: 'cortex-personality-settings',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatIconModule,
    MatSliderModule,
    MatFormFieldModule,
    MatSelectModule,
    MatDividerModule,
  ],
  templateUrl: './personality-settings.component.html',
  styleUrl: './personality-settings.component.scss',
})
export class PersonalitySettingsComponent {
  readonly state = inject(SettingsStateService);
  readonly stressResponseOptions = STRESS_RESPONSE_OPTIONS;
  readonly communicationStyleOptions = COMMUNICATION_STYLE_OPTIONS;
}
