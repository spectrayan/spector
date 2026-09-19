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

import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
  computed,
  ViewChild,
  ElementRef,
  AfterViewChecked,
  effect,
} from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';

/** Icon mapping for source modality. */
const MODALITY_ICONS: Record<string, string> = {
  TEXT: 'description',
  IMAGE: 'image',
  AUDIO: 'audiotrack',
  VIDEO: 'videocam',
};

@Component({
  selector: 'cortex-memory-profile-card',
  standalone: true,
  imports: [
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './memory-profile-card.component.html',
  styleUrl: './memory-profile-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MemoryProfileCardComponent implements AfterViewChecked {
  readonly memory = input.required<any>();
  readonly memoryId = input.required<string>();
  readonly vectorData = input<{ memoryId: string; dimension: number; values: number[] } | null>(null);
  readonly vectorLoading = input<boolean>(false);
  readonly vectorError = input<string | null>(null);

  readonly loadVector = output<void>();
  readonly reinforce = output<void>();
  readonly suppress = output<void>();
  readonly resolve = output<void>();
  readonly forget = output<void>();

  readonly showFingerprint = signal(false);
  private needsRender = false;

  @ViewChild('fingerprintCanvas') canvasRef?: ElementRef<HTMLCanvasElement>;

  constructor() {
    effect(() => {
      const vec = this.vectorData();
      if (vec && this.showFingerprint()) {
        this.needsRender = true;
      }
    });
  }

  readonly wordCount = computed(() => {
    const mem = this.memory();
    if (!mem?.text) return 0;
    return mem.text.trim().split(/\s+/).length;
  });

  readonly modalityIcon = computed(() => {
    const mem = this.memory();
    return MODALITY_ICONS[mem?.sourceModality] ?? 'description';
  });

  readonly originalFileName = computed(() => {
    const mem = this.memory();
    if (!mem?.metadata?.original_path) return null;
    const path: string = mem.metadata.original_path;
    const parts = path.split(/[/\\]/);
    return parts[parts.length - 1] || null;
  });

  readonly chunkLabel = computed(() => {
    const id = this.memoryId();
    const match = id?.match(/::chunk-(\d+)/);
    return match ? `Chunk ${parseInt(match[1], 10) + 1}` : null;
  });

  readonly fingerprintWidth = computed(() => {
    const vec = this.vectorData();
    if (!vec) return 320;
    const cols = Math.ceil(Math.sqrt(vec.dimension));
    return Math.min(cols * 2, 400);
  });

  readonly fingerprintHeight = computed(() => {
    const vec = this.vectorData();
    if (!vec) return 200;
    const cols = Math.ceil(Math.sqrt(vec.dimension));
    const rows = Math.ceil(vec.dimension / cols);
    return Math.min(rows * 2, 250);
  });

  safeFixed(val: any, digits: number, fallback: number = 0): string {
    const n = Number(val);
    return isFinite(n) ? n.toFixed(digits) : fallback.toFixed(digits);
  }

  toggleFingerprint(): void {
    this.showFingerprint.update((v) => !v);
    if (this.showFingerprint() && !this.vectorData() && !this.vectorLoading()) {
      this.loadVector.emit();
    }
  }

  ngAfterViewChecked(): void {
    if (this.needsRender && this.canvasRef?.nativeElement) {
      this.needsRender = false;
      this.renderFingerprint();
    }
  }

  private renderFingerprint(): void {
    const vec = this.vectorData();
    const canvas = this.canvasRef?.nativeElement;
    if (!vec || !canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const values = vec.values;
    const cols = Math.ceil(Math.sqrt(values.length));
    const cellW = canvas.width / cols;
    const cellH = canvas.height / Math.ceil(values.length / cols);

    for (let i = 0; i < values.length; i++) {
      const col = i % cols;
      const row = Math.floor(i / cols);
      const v = values[i];

      const norm = (v + 128) / 255;
      let r: number, g: number, b: number;
      if (norm < 0.5) {
        const t = norm * 2;
        r = Math.round(30 * t);
        g = Math.round(80 * t + 120 * (1 - t));
        b = Math.round(60 * t + 220 * (1 - t));
      } else {
        const t = (norm - 0.5) * 2;
        r = Math.round(30 + 225 * t);
        g = Math.round(80 + 100 * t * (1 - t * 0.5));
        b = Math.round(60 * (1 - t));
      }

      ctx.fillStyle = `rgb(${r},${g},${b})`;
      ctx.fillRect(col * cellW, row * cellH, cellW, cellH);
    }
  }
}
