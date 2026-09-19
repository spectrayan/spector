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

import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
  computed,
  ElementRef,
  viewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { SessionSummary } from '../../models/chat-turn.model';

export type { SessionSummary };

/**
 * ConversationDrawerComponent
 *
 * Operational session drawer component for Spector Cortex (Requirement R4).
 * Supports session listing, active session highlight, "+ New Chat",
 * inline title editing with Enter/Escape handlers, and safe delete confirmation.
 * Fully compliant with Angular 22 zoneless signals and WCAG AA accessibility.
 */
@Component({
  selector: 'cortex-conversation-drawer, conversation-drawer',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatDividerModule,
  ],
  templateUrl: './conversation-drawer.component.html',
  styleUrl: './conversation-drawer.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'conversation-drawer history-sidebar',
    '[class.drawer-open]': 'isOpen()',
    '[class.drawer-closed]': '!isOpen()',
    role: 'navigation',
    'aria-label': 'Conversation history drawer',
    'data-testid': 'conversation-drawer',
  },
})
export class ConversationDrawerComponent {
  // ── Inputs (Signal-based) ──
  readonly sessions = input<readonly SessionSummary[]>([]);
  readonly activeSessionId = input<string | null>(null);
  readonly isOpen = input<boolean>(true);
  readonly isLoading = input<boolean>(false);
  readonly hasMore = input<boolean>(false);

  // ── Outputs (Signal-based) ──
  readonly selectSession = output<string>();
  readonly newChat = output<void>();
  readonly renameSession = output<{ sessionId: string; newTitle: string }>();
  readonly deleteSession = output<string>();
  readonly loadMore = output<void>();
  readonly toggleOpen = output<boolean>();

  // ── Local Interaction Signals ──
  readonly searchQuery = signal<string>('');
  readonly editingSessionId = signal<string | null>(null);
  readonly editTitleValue = signal<string>('');
  readonly confirmingDeleteSessionId = signal<string | null>(null);

  // ── View Child for auto-focusing edit input ──
  private readonly editInputRef = viewChild<ElementRef<HTMLInputElement>>('editInput');

  // ── Filtered Sessions Computed Signal ──
  readonly filteredSessions = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const list = this.sessions();
    if (!query) return list;
    return list.filter(
      (s) =>
        s.title?.toLowerCase().includes(query) ||
        s.preview?.toLowerCase().includes(query),
    );
  });

  // ── Navigation & Actions ──

  onSessionClick(sessionId: string): void {
    if (this.editingSessionId() !== null) return;
    if (this.confirmingDeleteSessionId() !== null) return;
    this.selectSession.emit(sessionId);
  }

  onNewChat(): void {
    this.newChat.emit();
  }

  onToggleDrawer(): void {
    this.toggleOpen.emit(!this.isOpen());
  }

  // ── Inline Edit Mechanics ──

  startEdit(session: SessionSummary, event: Event): void {
    event.stopPropagation();
    this.editingSessionId.set(session.sessionId);
    this.editTitleValue.set(session.title || 'Untitled Chat');
    setTimeout(() => {
      this.editInputRef()?.nativeElement?.focus();
      this.editInputRef()?.nativeElement?.select();
    }, 50);
  }

  saveEdit(sessionId: string): void {
    const trimmed = this.editTitleValue().trim();
    if (trimmed) {
      this.renameSession.emit({ sessionId, newTitle: trimmed });
    }
    this.editingSessionId.set(null);
  }

  cancelEdit(): void {
    this.editingSessionId.set(null);
  }

  onEditKeydown(event: KeyboardEvent, sessionId: string): void {
    if (event.key === 'Enter') {
      event.preventDefault();
      this.saveEdit(sessionId);
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.cancelEdit();
    }
  }

  // ── Delete Confirmation Mechanics ──

  promptDelete(sessionId: string, event: Event): void {
    event.stopPropagation();
    this.confirmingDeleteSessionId.set(sessionId);
  }

  confirmDelete(sessionId: string, event: Event): void {
    event.stopPropagation();
    this.deleteSession.emit(sessionId);
    this.confirmingDeleteSessionId.set(null);
  }

  cancelDelete(event: Event): void {
    event.stopPropagation();
    this.confirmingDeleteSessionId.set(null);
  }

  // ── Date Formatting Utility ──

  formatSessionDate(timestamp?: number | string): string {
    if (!timestamp) return '';
    const date = typeof timestamp === 'string' ? new Date(timestamp) : new Date(timestamp);
    if (isNaN(date.getTime())) return '';

    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMinutes = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMinutes / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffMinutes < 1) return 'Just now';
    if (diffMinutes < 60) return `${diffMinutes}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays === 1) return 'Yesterday';
    if (diffDays < 7) return `${diffDays}d ago`;

    return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  }
}
