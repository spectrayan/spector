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

import { Component, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { VisualRecallAssistState, DEFAULT_VISUAL_ASSIST_STATE } from '../../models/mel-templates';

@Component({
  selector: 'cortex-mel-visual-assist',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, MatButtonModule, MatTooltipModule],
  templateUrl: './mel-visual-assist.component.html',
  styleUrl: './mel-visual-assist.component.scss',
})
export class MelVisualAssistComponent {
  readonly state = input<VisualRecallAssistState>(DEFAULT_VISUAL_ASSIST_STATE);
  readonly isOpen = input<boolean>(true);

  readonly toggleOpen = output<void>();
  readonly stateChange = output<VisualRecallAssistState>();

  readonly availableTiers = ['WORKING', 'EPISODIC', 'SEMANTIC', 'PROCEDURAL'];

  onCueChange(newCue: string): void {
    this.stateChange.emit({
      ...this.state(),
      cue: newCue,
    });
  }

  onExplainToggle(): void {
    const current = this.state();
    this.stateChange.emit({
      ...current,
      explain: !current.explain,
    });
  }

  onTierToggle(tier: string): void {
    const current = this.state();
    const exists = current.tiers.includes(tier);
    const nextTiers = exists
      ? current.tiers.filter((t) => t !== tier)
      : [...current.tiers, tier];
    this.stateChange.emit({
      ...current,
      tiers: nextTiers.length > 0 ? nextTiers : ['EPISODIC'],
    });
  }

  onTagsChange(newTags: string): void {
    this.stateChange.emit({
      ...this.state(),
      tags: newTags,
    });
  }

  onImportanceChange(newImp: number): void {
    this.stateChange.emit({
      ...this.state(),
      minImportance: Math.max(0, Math.min(1, newImp)),
    });
  }

  onTopKChange(newTopK: number): void {
    this.stateChange.emit({
      ...this.state(),
      topK: Math.max(1, Math.min(100, Math.floor(newTopK))),
    });
  }

  onToggle(): void {
    this.toggleOpen.emit();
  }
}
