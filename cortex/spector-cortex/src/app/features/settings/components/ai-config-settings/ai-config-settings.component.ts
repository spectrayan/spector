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

import { Component, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSliderModule } from '@angular/material/slider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { SettingsStateService } from '../../services/settings-state.service';
import { AiConfigField, ConfigCategoryMeta } from '../../models/settings.models';

@Component({
  selector: 'cortex-ai-config-settings',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
    MatSlideToggleModule,
    MatSliderModule,
    MatTooltipModule,
    MatChipsModule,
  ],
  templateUrl: './ai-config-settings.component.html',
  styleUrl: './ai-config-settings.component.scss',
})
export class AiConfigSettingsComponent {
  readonly state = inject(SettingsStateService);

  readonly revealedSecrets = signal<Record<string, boolean>>({});

  toggleRevealSecret(fieldKey: string): void {
    this.revealedSecrets.update((m) => ({ ...m, [fieldKey]: !m[fieldKey] }));
  }

  isSecretRevealed(fieldKey: string): boolean {
    return !!this.revealedSecrets()[fieldKey];
  }

  readonly currentCategoryMeta = computed<ConfigCategoryMeta | undefined>(() => {
    const active = this.state.activeAiCategory();
    return this.state.aiConfigCategories().find((c) => c.key === active);
  });

  readonly currentFields = computed<AiConfigField[]>(() => {
    const active = this.state.activeAiCategory();
    const filter = this.state.aiConfigFilter().trim().toLowerCase();
    const fields = this.state.aiConfigFields()[active] || [];
    if (!filter) return fields;
    return fields.filter(
      (f) =>
        f.key.toLowerCase().includes(filter) ||
        (f.description && f.description.toLowerCase().includes(filter))
    );
  });

  selectCategory(categoryKey: string): void {
    this.state.activeAiCategory.set(categoryKey);
  }

  onToggleChange(category: string, key: string, checked: boolean): void {
    this.state.onAiFieldChange(category, key, checked, true);
  }

  onSelectChange(category: string, key: string, value: any): void {
    this.state.onAiFieldChange(category, key, value, true);
  }

  onInputChange(category: string, key: string, value: any): void {
    this.state.onAiFieldChange(category, key, value, false);
  }

  onNumberInputChange(category: string, key: string, event: Event): void {
    const input = event.target as HTMLInputElement;
    const num = input.value === '' ? '' : Number(input.value);
    this.state.onAiFieldChange(category, key, num, false);
  }

  onSliderInput(category: string, key: string, value: number): void {
    this.state.onAiFieldChange(category, key, value, true);
  }
}
