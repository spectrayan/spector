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
  model,
  signal,
  computed,
} from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MarkdownPreviewComponent } from '@shared/components/markdown-preview/markdown-preview.component';

export interface ReconsolidateEvent {
  mode: 'update' | 'fork';
  text: string;
  tags: string[];
}

@Component({
  selector: 'cortex-memory-content-editor',
  standalone: true,
  imports: [
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatChipsModule,
    MatTooltipModule,
    MarkdownPreviewComponent,
  ],
  templateUrl: './memory-content-editor.component.html',
  styleUrl: './memory-content-editor.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MemoryContentEditorComponent {
  readonly memory = input.required<any>();

  readonly isEditing = model(false);
  readonly reconsolidate = output<ReconsolidateEvent>();

  readonly editText = signal('');
  readonly editTags = signal<string[]>([]);
  readonly reconMode = signal<'update' | 'fork'>('update');

  readonly editWordCount = computed(() => {
    const t = this.editText().trim();
    return t ? t.split(/\s+/).length : 0;
  });

  startEdit(): void {
    const mem = this.memory();
    if (!mem) return;
    this.editText.set(mem.text || '');
    this.editTags.set([...(mem.tags || [])]);
    this.reconMode.set('update');
    this.isEditing.set(true);
  }

  cancelEdit(): void {
    this.isEditing.set(false);
  }

  hasEditChanges(): boolean {
    const mem = this.memory();
    if (!mem) return false;
    const textChanged = this.editText() !== (mem.text || '');
    const tagsChanged = JSON.stringify(this.editTags()) !== JSON.stringify(mem.tags || []);
    return textChanged || tagsChanged;
  }

  onEditTextInput(event: Event): void {
    this.editText.set((event.target as HTMLTextAreaElement).value);
  }

  addEditTag(event: Event): void {
    event.preventDefault();
    const input = event.target as HTMLInputElement;
    const tag = input.value.trim();
    if (tag && !this.editTags().includes(tag)) {
      this.editTags.update((tags) => [...tags, tag]);
    }
    input.value = '';
  }

  removeEditTag(index: number): void {
    this.editTags.update((tags) => tags.filter((_, i) => i !== index));
  }

  saveEdit(): void {
    this.reconsolidate.emit({
      mode: this.reconMode(),
      text: this.editText(),
      tags: this.editTags(),
    });
  }
}
