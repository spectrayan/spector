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

import { Component, inject, OnInit, signal, computed, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MemoryTableService, MemoryRow } from '@core/services/memory-table.service';
import { AddMemoryDialogComponent } from '../../components/add-memory-dialog.component';
import { CortexSnackbarService } from '@shared/services/cortex-snackbar.service';
import { ERROR_MESSAGES, formatMessage } from '@shared/constants/error-messages';

import { MemoryTableToolbarComponent } from './components/memory-table-toolbar/memory-table-toolbar.component';
import { MemoryAdvancedFiltersComponent } from './components/memory-advanced-filters/memory-advanced-filters.component';
import { MemoryTierSummaryComponent } from './components/memory-tier-summary/memory-tier-summary.component';
import { MemoryBulkActionsComponent } from './components/memory-bulk-actions/memory-bulk-actions.component';

@Component({
  selector: 'cortex-memory-table',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatChipsModule,
    MatCheckboxModule,
    MatTooltipModule,
    MatProgressBarModule,
    MatPaginatorModule,
    MatSnackBarModule,
    MatDialogModule,
    MemoryTableToolbarComponent,
    MemoryAdvancedFiltersComponent,
    MemoryTierSummaryComponent,
    MemoryBulkActionsComponent,
  ],
  templateUrl: './memory-table.component.html',
  styleUrl: './memory-table.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MemoryTableComponent implements OnInit {
  protected readonly table = inject(MemoryTableService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(CortexSnackbarService);

  readonly tiers = ['WORKING', 'EPISODIC', 'SEMANTIC', 'PROCEDURAL'];

  readonly tierColors: Record<string, string> = {
    WORKING: 'var(--cortex-working)',
    EPISODIC: 'var(--cortex-episodic)',
    SEMANTIC: 'var(--cortex-semantic)',
    PROCEDURAL: 'var(--cortex-procedural)',
  };

  readonly sortableColumns = [
    { key: 'timestampMs', label: 'Time' },
    { key: 'importance', label: 'Importance' },
    { key: 'valence', label: 'Valence' },
    { key: 'agentRecallCount', label: 'Recalls' },
  ];

  // ── Advanced Filters state ──
  protected readonly showAdvancedFilters = signal(false);
  protected readonly searchText = signal('');
  protected readonly minImportance = signal(0);
  protected readonly minValence = signal(-128);
  protected readonly maxValence = signal(127);
  protected readonly selectedTagFilter = signal<string | null>(null);
  protected readonly filterDateFrom = signal<Date | null>(null);
  protected readonly filterDateTo = signal<Date | null>(null);

  // ── Bulk Selection state ──
  protected readonly selectedRowIds = signal<Set<string>>(new Set());

  // ── Reactive computed signals ──

  /** Live cascading local filtered rows based on user filters */
  readonly filteredRows = computed(() => {
    let list = this.table.rows();

    const search = this.searchText().toLowerCase().trim();
    if (search) {
      list = list.filter(
        (row) =>
          row.textPreview.toLowerCase().includes(search) ||
          row.id.toLowerCase().includes(search) ||
          row.tags.some((t) => t.toLowerCase().includes(search)),
      );
    }

    const minImp = this.minImportance();
    if (minImp > 0) {
      list = list.filter((row) => row.importance >= minImp);
    }

    const minVal = this.minValence();
    const maxVal = this.maxValence();
    list = list.filter((row) => row.valence >= minVal && row.valence <= maxVal);

    const tag = this.selectedTagFilter();
    if (tag) {
      list = list.filter((row) => row.tags.includes(tag));
    }

    const dateFrom = this.filterDateFrom();
    if (dateFrom) {
      const fromMs = dateFrom.getTime();
      list = list.filter((row) => row.timestampMs >= fromMs);
    }

    const dateTo = this.filterDateTo();
    if (dateTo) {
      const endOfDay = new Date(dateTo);
      endOfDay.setHours(23, 59, 59, 999);
      const toMs = endOfDay.getTime();
      list = list.filter((row) => row.timestampMs <= toMs);
    }

    return list;
  });

  /** Gather all unique tags in current page to populate filter dropdown */
  readonly availableTags = computed(() => {
    const tagsSet = new Set<string>();
    this.table.rows().forEach((row) => {
      if (row.tags) row.tags.forEach((t) => tagsSet.add(t));
    });
    return Array.from(tagsSet).sort();
  });

  resetAdvancedFilters(): void {
    this.searchText.set('');
    this.minImportance.set(0);
    this.minValence.set(-128);
    this.maxValence.set(127);
    this.selectedTagFilter.set(null);
    this.filterDateFrom.set(null);
    this.filterDateTo.set(null);
  }

  // ── Bulk Action Handlers ──

  toggleAllRows(): void {
    const activeRows = this.filteredRows();
    const currentSelected = this.selectedRowIds();
    const allActiveSelected =
      activeRows.length > 0 && activeRows.every((row) => currentSelected.has(row.id));

    this.selectedRowIds.update((set) => {
      const next = new Set(set);
      if (allActiveSelected) {
        activeRows.forEach((row) => next.delete(row.id));
      } else {
        activeRows.forEach((row) => next.add(row.id));
      }
      return next;
    });
  }

  isAllSelected(): boolean {
    const activeRows = this.filteredRows();
    if (activeRows.length === 0) return false;
    const currentSelected = this.selectedRowIds();
    return activeRows.every((row) => currentSelected.has(row.id));
  }

  isRowSelected(row: MemoryRow): boolean {
    return this.selectedRowIds().has(row.id);
  }

  toggleRowSelection(row: MemoryRow, event?: any): void {
    if (event && event.stopPropagation) {
      event.stopPropagation();
    } else if (event && event.originalEvent && event.originalEvent.stopPropagation) {
      event.originalEvent.stopPropagation();
    }
    this.selectedRowIds.update((set) => {
      const next = new Set(set);
      if (next.has(row.id)) {
        next.delete(row.id);
      } else {
        next.add(row.id);
      }
      return next;
    });
  }

  clearSelection(): void {
    this.selectedRowIds.set(new Set());
  }

  bulkReinforce(): void {
    const ids = Array.from(this.selectedRowIds());
    if (ids.length === 0) return;

    let count = 0;
    ids.forEach((id) => {
      this.table.reinforce(id).subscribe({
        next: () => {
          count++;
          if (count === ids.length) {
            this.toast.success(
              formatMessage(ERROR_MESSAGES.MEMORY.BULK_REINFORCE_SUCCESS, { count: ids.length }),
            );
            this.clearSelection();
            this.table.loadPage();
          }
        },
        error: () => {
          count++;
          if (count === ids.length) {
            this.toast.warn(ERROR_MESSAGES.MEMORY.BULK_REINFORCE_PARTIAL);
            this.table.loadPage();
          }
        },
      });
    });
  }

  bulkSuppress(): void {
    const ids = Array.from(this.selectedRowIds());
    if (ids.length === 0) return;

    let count = 0;
    ids.forEach((id) => {
      this.table.suppress(id, 'Bulk suppression via UI').subscribe({
        next: () => {
          count++;
          if (count === ids.length) {
            this.toast.success(
              formatMessage(ERROR_MESSAGES.MEMORY.BULK_SUPPRESS_SUCCESS, { count: ids.length }),
            );
            this.clearSelection();
            this.table.loadPage();
          }
        },
        error: () => {
          count++;
          if (count === ids.length) {
            this.toast.warn(ERROR_MESSAGES.MEMORY.BULK_SUPPRESS_PARTIAL);
            this.table.loadPage();
          }
        },
      });
    });
  }

  bulkForget(): void {
    const ids = Array.from(this.selectedRowIds());
    if (ids.length === 0) return;

    let count = 0;
    ids.forEach((id) => {
      this.table.forget(id).subscribe({
        next: () => {
          count++;
          if (count === ids.length) {
            this.toast.success(
              formatMessage(ERROR_MESSAGES.MEMORY.BULK_FORGET_SUCCESS, { count: ids.length }),
            );
            this.clearSelection();
            this.table.loadPage();
          }
        },
        error: () => {
          count++;
          if (count === ids.length) {
            this.toast.warn(ERROR_MESSAGES.MEMORY.BULK_FORGET_PARTIAL);
            this.table.loadPage();
          }
        },
      });
    });
  }

  ngOnInit(): void {
    this.table.loadPage();
  }

  /** Handle mat-paginator page events. */
  onPageChange(event: PageEvent): void {
    this.table.page.set(event.pageIndex);
    this.table.pageSize.set(event.pageSize);
    this.table.loadPage();
  }

  /** Format epoch ms to relative time string */
  relativeTime(ms: number): string {
    if (!ms || ms < 946684800000 || ms > 4102444800000) return '—';
    const diff = Date.now() - ms;
    if (diff < 0) return 'future';
    if (diff < 60_000) return `${Math.floor(diff / 1000)}s ago`;
    if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}m ago`;
    if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}h ago`;
    return `${Math.floor(diff / 86_400_000)}d ago`;
  }

  /** Copy ID to clipboard */
  copyId(id: string, event: MouseEvent): void {
    event.stopPropagation();
    navigator.clipboard.writeText(id);
  }

  /** Track by for rows */
  trackRow(_: number, row: MemoryRow): string {
    return row.id;
  }

  /** Navigate to memory detail view */
  onRowClick(row: MemoryRow): void {
    this.router.navigate(['/memories', row.id]);
  }

  /** Open add memory dialog */
  openAddDialog(): void {
    const dialogRef = this.dialog.open(AddMemoryDialogComponent, {
      width: '560px',
      maxWidth: '95vw',
      panelClass: 'cortex-dialog',
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.table.loadPage();
      }
    });
  }

  /** Reinforce a memory */
  onReinforce(row: MemoryRow, event: MouseEvent): void {
    event.stopPropagation();
    this.table.reinforce(row.id, 1).subscribe({
      next: () => {
        this.toast.success(formatMessage(ERROR_MESSAGES.MEMORY.REINFORCE_SUCCESS, { id: row.id }));
        this.table.loadPage();
      },
      error: () => this.toast.error(ERROR_MESSAGES.MEMORY.REINFORCE_FAILED),
    });
  }

  /** Suppress a memory */
  onSuppress(row: MemoryRow, event: MouseEvent): void {
    event.stopPropagation();
    this.table.suppress(row.id, 'Manual suppression via Cortex UI').subscribe({
      next: () => {
        this.toast.success(formatMessage(ERROR_MESSAGES.MEMORY.SUPPRESS_SUCCESS, { id: row.id }));
        this.table.loadPage();
      },
      error: () => this.toast.error(ERROR_MESSAGES.MEMORY.SUPPRESS_FAILED),
    });
  }

  /** Toggle resolved status */
  onResolve(row: MemoryRow, event: MouseEvent): void {
    event.stopPropagation();
    const obs = row.resolved ? this.table.unresolve(row.id) : this.table.resolve(row.id);
    obs.subscribe({
      next: () => {
        const action = row.resolved ? 'Unresolved' : 'Resolved';
        this.toast.success(
          formatMessage(ERROR_MESSAGES.MEMORY.RESOLVE_SUCCESS, { action, id: row.id }),
        );
        this.table.loadPage();
      },
      error: () => this.toast.error(ERROR_MESSAGES.MEMORY.RESOLVE_FAILED),
    });
  }

  /** Forget (tombstone) a memory */
  onForget(row: MemoryRow, event: MouseEvent): void {
    event.stopPropagation();
    this.table.forget(row.id).subscribe({
      next: () => {
        this.toast.success(formatMessage(ERROR_MESSAGES.MEMORY.FORGET_SUCCESS, { id: row.id }));
        this.table.loadPage();
      },
      error: () => this.toast.error(ERROR_MESSAGES.MEMORY.FORGET_FAILED),
    });
  }

  /** Trigger reflect consolidation */
  onReflect(): void {
    this.table.reflect().subscribe({
      next: () => {
        this.toast.success(ERROR_MESSAGES.MEMORY.REFLECT_SUCCESS);
        this.table.loadPage();
      },
      error: () => this.toast.error(ERROR_MESSAGES.MEMORY.REFLECT_FAILED),
    });
  }
}
