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

import { Component, input, output, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';

export interface GatingAuditPhase {
  phase: string;
  name: string;
  passed: boolean;
  reason: string;
  detail: string;
}

@Component({
  selector: 'cortex-why-not-drawer',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule, MatTooltipModule],
  templateUrl: './mel-why-not-drawer.component.html',
  styleUrl: './mel-why-not-drawer.component.scss',
})
export class MelWhyNotDrawerComponent {
  readonly memoryId = input.required<string>();
  readonly queryCue = input<string>('');

  readonly close = output<void>();
  readonly applySuggestion = output<string>();

  readonly phases: GatingAuditPhase[] = [
    {
      phase: 'P1',
      name: 'Tombstone & Contradiction Filter',
      passed: true,
      reason: 'Trace active',
      detail: 'Tombstone bit 0x00 and contradiction bitmask clean.',
    },
    {
      phase: 'P2',
      name: '128-bit Bloom Synaptic Tag Filter',
      passed: true,
      reason: 'Hash match',
      detail: 'Query synaptic tag Bloom filter AND-mask satisfied.',
    },
    {
      phase: 'P3',
      name: 'Affective Valence Range Check',
      passed: true,
      reason: 'Within bounds',
      detail: 'Trace valence falls within Bower mood-congruent filter window.',
    },
    {
      phase: 'P4',
      name: 'Stale Pre-Screen & Cognitive Mass (Mi)',
      passed: true,
      reason: 'Exempt by mass',
      detail: 'Decay R(Δt) mitigated by active cognitive mass Mi >= 0.20.',
    },
    {
      phase: 'P5',
      name: 'SIMD Vector Distance Calculation',
      passed: true,
      reason: 'Computed',
      detail: 'Cosine similarity calculated via SIMD AVX-512 intrinsic kernel.',
    },
    {
      phase: 'P6',
      name: 'Min-Heap Top-K Cutoff',
      passed: false,
      reason: 'Ranked below cutoff',
      detail: 'Fused score was 0.042 below the 5th heap candidate threshold.',
    },
  ];

  readonly counterfactualSuggestions = computed(() => [
    {
      title: 'Increase Top-K parameter',
      action: 'TOP 10',
      description: 'Candidate rank is #7; expanding TOP 5 to TOP 10 includes this candidate.',
    },
    {
      title: 'Reinforce trace engram',
      action: "REHEARSE '" + this.memoryId() + "';",
      description: 'Strengthen storage strength S and increment recall counter to boost fused score.',
    },
    {
      title: 'Lower Min Importance barrier',
      action: 'IMPORTANCE >= 0.25',
      description: 'Allows lower-priority episodic traces to compete on raw semantic similarity.',
    },
  ]);

  onClose(): void {
    this.close.emit();
  }

  onSelectSuggestion(action: string): void {
    this.applySuggestion.emit(action);
  }
}
