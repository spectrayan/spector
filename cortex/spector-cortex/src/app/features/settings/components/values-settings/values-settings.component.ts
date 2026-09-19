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
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { SettingsStateService } from '../../services/settings-state.service';

interface ChipSectionDef {
  key: 'values' | 'fears' | 'aspirations';
  label: string;
  icon: string;
  placeholder: string;
}

@Component({
  selector: 'cortex-values-settings',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './values-settings.component.html',
  styleUrl: './values-settings.component.scss',
})
export class ValuesSettingsComponent {
  readonly state = inject(SettingsStateService);

  readonly sections: ChipSectionDef[] = [
    { key: 'values', label: 'Core Values', icon: 'diamond', placeholder: 'e.g. integrity, innovation' },
    { key: 'fears', label: 'Fears & Concerns', icon: 'warning', placeholder: 'e.g. public speaking, failure' },
    { key: 'aspirations', label: 'Aspirations', icon: 'rocket_launch', placeholder: 'e.g. start a company' },
  ];

  getList(key: 'values' | 'fears' | 'aspirations'): string[] {
    switch (key) {
      case 'values': return this.state.personaValues();
      case 'fears': return this.state.personaFears();
      case 'aspirations': return this.state.personaAspirations();
    }
  }

  getNewValue(key: 'values' | 'fears' | 'aspirations'): string {
    switch (key) {
      case 'values': return this.state.newValue();
      case 'fears': return this.state.newFear();
      case 'aspirations': return this.state.newAspiration();
    }
  }

  setNewValue(key: 'values' | 'fears' | 'aspirations', val: string): void {
    switch (key) {
      case 'values': this.state.newValue.set(val); break;
      case 'fears': this.state.newFear.set(val); break;
      case 'aspirations': this.state.newAspiration.set(val); break;
    }
  }
}
