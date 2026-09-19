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

import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { SettingsStateService } from './services/settings-state.service';
import { ProfileSettingsComponent } from './components/profile-settings/profile-settings.component';
import { PersonalitySettingsComponent } from './components/personality-settings/personality-settings.component';
import { ValuesSettingsComponent } from './components/values-settings/values-settings.component';
import { ScoringSettingsComponent } from './components/scoring-settings/scoring-settings.component';
import { AiConfigSettingsComponent } from './components/ai-config-settings/ai-config-settings.component';
import { PrivacySettingsComponent } from './components/privacy-settings/privacy-settings.component';
import { ApiKeysSettingsComponent } from './components/api-keys-settings/api-keys-settings.component';

@Component({
  selector: 'cortex-settings',
  standalone: true,
  providers: [SettingsStateService],
  imports: [
    CommonModule,
    MatIconModule,
    MatButtonModule,
    MatTabsModule,
    MatTooltipModule,
    MatProgressBarModule,
    ProfileSettingsComponent,
    PersonalitySettingsComponent,
    ValuesSettingsComponent,
    ScoringSettingsComponent,
    AiConfigSettingsComponent,
    PrivacySettingsComponent,
    ApiKeysSettingsComponent,
  ],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent implements OnInit {
  readonly state = inject(SettingsStateService);

  ngOnInit(): void {
    this.state.init();
  }
}
