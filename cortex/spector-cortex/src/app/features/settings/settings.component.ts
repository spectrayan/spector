/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
