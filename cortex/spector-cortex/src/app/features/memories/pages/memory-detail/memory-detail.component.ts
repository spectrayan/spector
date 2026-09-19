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
  inject,
  signal,
  computed,
  OnInit,
  OnDestroy,
  ChangeDetectionStrategy,
} from '@angular/core';
import { Subscription } from 'rxjs';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  MemoryTableService,
  MemoryGraphResponse,
} from '@core/services/memory-table.service';

import { MemoryIdentityCardComponent } from './components/memory-identity-card/memory-identity-card.component';
import { MemoryProfileCardComponent } from './components/memory-profile-card/memory-profile-card.component';
import {
  MemoryContentEditorComponent,
  ReconsolidateEvent,
} from './components/memory-content-editor/memory-content-editor.component';
import { MemoryConsolidationTraceComponent } from './components/memory-consolidation-trace/memory-consolidation-trace.component';
import { MemoryCognitiveCardComponent } from './components/memory-cognitive-card/memory-cognitive-card.component';
import { MemoryRelationshipsCardComponent } from './components/memory-relationships-card/memory-relationships-card.component';

/** Tier color mapping for visual badges. */
const TIER_COLORS: Record<string, string> = {
  WORKING: '#ffb74d',
  EPISODIC: '#66bb6a',
  SEMANTIC: '#42a5f5',
  PROCEDURAL: '#ab47bc',
};

@Component({
  selector: 'cortex-memory-detail',
  standalone: true,
  imports: [
    RouterLink,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatProgressBarModule,
    MatSnackBarModule,
    MemoryIdentityCardComponent,
    MemoryProfileCardComponent,
    MemoryContentEditorComponent,
    MemoryConsolidationTraceComponent,
    MemoryCognitiveCardComponent,
    MemoryRelationshipsCardComponent,
  ],
  templateUrl: './memory-detail.component.html',
  styleUrl: './memory-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MemoryDetailComponent implements OnInit, OnDestroy {
  private routeSub?: Subscription;
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly memoryService = inject(MemoryTableService);
  private readonly snackBar = inject(MatSnackBar);

  readonly memoryId = signal('');
  readonly memory = signal<any>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  // Graph state
  readonly graphData = signal<MemoryGraphResponse | null>(null);
  readonly graphLoading = signal(false);
  readonly graphError = signal<string | null>(null);

  // Vector state
  readonly vectorData = signal<{ memoryId: string; dimension: number; values: number[] } | null>(null);
  readonly vectorLoading = signal(false);
  readonly vectorError = signal<string | null>(null);

  // Inline edit state
  readonly isEditing = signal(false);

  readonly tierColor = computed(() => {
    const mem = this.memory();
    return mem ? (TIER_COLORS[mem.tier] ?? '#9e9e9e') : '#9e9e9e';
  });

  readonly createdDateFormatted = computed(() => {
    const mem = this.memory();
    if (!mem) return '';
    if (mem.createdAt) {
      const d = new Date(mem.createdAt);
      if (!isNaN(d.getTime())) {
        return d.toLocaleDateString(undefined, {
          year: 'numeric',
          month: 'short',
          day: 'numeric',
          hour: '2-digit',
          minute: '2-digit',
        });
      }
    }
    if (mem.timestampMs && mem.timestampMs > 946684800000 && mem.timestampMs < 4102444800000) {
      return new Date(mem.timestampMs).toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    }
    return 'Unknown';
  });

  readonly ageLabel = computed(() => {
    const mem = this.memory();
    if (!mem?.createdAt) return '';
    const ms = Date.now() - new Date(mem.createdAt).getTime();
    if (ms < 0) return '';
    const hours = Math.floor(ms / 3_600_000);
    if (hours < 1) return 'Just now';
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    if (days < 30) return `${days}d ago`;
    const months = Math.floor(days / 30);
    return `${months}mo ago`;
  });

  ngOnInit(): void {
    this.routeSub = this.route.paramMap.subscribe((params) => {
      const id = params.get('id');
      if (id && id !== this.memoryId()) {
        this.memoryId.set(id);
        this.error.set(null);
        this.graphError.set(null);
        this.loadMemory(id);
        this.loadGraph(id);
      }
    });
  }

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
  }

  loadVector(): void {
    const id = this.memoryId();
    if (!id) return;
    this.vectorLoading.set(true);
    this.vectorError.set(null);
    this.memoryService.getMemoryVector(id).subscribe({
      next: (data) => {
        this.vectorData.set(data);
        this.vectorLoading.set(false);
      },
      error: (err) => {
        this.vectorError.set(err.error?.message || err.message || 'Failed to load vector');
        this.vectorLoading.set(false);
      },
    });
  }

  private loadMemory(id: string): void {
    this.loading.set(true);
    this.memoryService.getMemoryById(id).subscribe({
      next: (row) => {
        const validTs =
          row.timestampMs && row.timestampMs > 946684800000 && row.timestampMs < 4102444800000;
        const validIso =
          row.createdAt &&
          !isNaN(new Date(row.createdAt).getTime()) &&
          new Date(row.createdAt).getTime() > 946684800000 &&
          new Date(row.createdAt).getTime() < 4102444800000;
        const recallCount =
          row.agentRecallCount != null &&
          row.agentRecallCount >= 0 &&
          row.agentRecallCount < 1000000
            ? row.agentRecallCount
            : 0;
        const detail = {
          ...row,
          text: row.textPreview,
          recallCount,
          createdAt: validTs
            ? new Date(row.timestampMs).toISOString()
            : validIso
              ? row.createdAt
              : null,
        };
        this.memory.set(detail);
        this.loading.set(false);
      },
      error: (err) => {
        const msg =
          typeof err.error === 'string'
            ? err.error
            : (err.error?.message ?? err.message ?? 'Memory not found');
        this.error.set(msg);
        this.loading.set(false);
      },
    });
  }

  private loadGraph(id: string): void {
    this.graphLoading.set(true);
    this.graphError.set(null);

    this.memoryService.getMemoryGraph(id, 2).subscribe({
      next: (data) => {
        this.graphData.set(data);
        this.graphLoading.set(false);
      },
      error: (err) => {
        const msg =
          typeof err.error === 'string'
            ? err.error
            : (err.error?.message ?? err.message ?? 'Unknown error');
        this.graphError.set(msg);
        this.graphLoading.set(false);
      },
    });
  }

  onReinforce(): void {
    this.memoryService.reinforce(this.memoryId()).subscribe({
      next: () => this.loadMemory(this.memoryId()),
    });
  }

  onSuppress(): void {
    this.memoryService.suppress(this.memoryId()).subscribe({
      next: () => this.loadMemory(this.memoryId()),
    });
  }

  onResolve(): void {
    this.memoryService.resolve(this.memoryId()).subscribe({
      next: () => this.loadMemory(this.memoryId()),
    });
  }

  onForget(): void {
    this.memoryService.forget(this.memoryId()).subscribe({
      next: () => this.router.navigate(['/memories']),
    });
  }

  onReconsolidate(event: ReconsolidateEvent): void {
    if (event.mode === 'update') {
      this.memoryService.updateMemory(this.memoryId(), { text: event.text, tags: event.tags }).subscribe({
        next: () => {
          this.snackBar.open('Memory reconsolidated — new embedding computed', 'OK', { duration: 3000 });
          this.isEditing.set(false);
          this.vectorData.set(null);
          this.loadMemory(this.memoryId());
        },
        error: (err: any) => {
          this.snackBar.open('Reconsolidation failed: ' + (err.error?.message || err.message), 'Dismiss', {
            duration: 4000,
          });
        },
      });
    } else {
      const request = {
        id: '',
        text: event.text,
        tier: this.memory()?.tier || 'SEMANTIC',
        source: this.memory()?.source || 'OBSERVED',
        tags: event.tags.join(','),
      };
      this.memoryService.remember(request).subscribe({
        next: (resp) => {
          this.snackBar
            .open('Forked memory created: ' + (resp.id || resp.taskId), 'View', { duration: 5000 })
            .onAction()
            .subscribe(() => {
              if (resp.id) this.router.navigate(['/memories', resp.id]);
            });
          this.isEditing.set(false);
        },
        error: (err: any) => {
          this.snackBar.open('Fork failed: ' + (err.error?.message || err.message), 'Dismiss', {
            duration: 4000,
          });
        },
      });
    }
  }

  goBack(): void {
    this.router.navigate(['/memories']);
  }
}
