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
  input,
  output,
  signal,
  computed,
  ViewChild,
  ElementRef,
  HostListener,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MelDiagnostic } from '@core/models/mel.models';

const MEL_KEYWORDS = [
  'RECALL',
  'EXPLAIN RECALL',
  'REMEMBER',
  'CONSOLIDATE',
  'FORGET',
  'INTROSPECT',
  'DREAM',
  'ASSOCIATE',
  'REHEARSE',
  'RECONSOLIDATE',
  'SIMULATE',
  'TIERS',
  'WORKING',
  'EPISODIC',
  'SEMANTIC',
  'PROCEDURAL',
  'IMPORTANCE',
  'VALENCE',
  'AROUSAL',
  'TAGS CONTAIN',
  'TOP',
  'INTO',
  'SOURCE',
  'EXPERIENCED',
  'DISTILLED',
  'SIMULATED',
];

@Component({
  selector: 'cortex-mel-editor',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, MatButtonModule, MatTooltipModule],
  templateUrl: './mel-editor.component.html',
  styleUrl: './mel-editor.component.scss',
})
export class MelEditorComponent {
  readonly script = input<string>('');
  readonly diagnostics = input<MelDiagnostic[]>([]);
  readonly isRunning = input<boolean>(false);

  readonly scriptChange = output<string>();
  readonly execute = output<void>();
  readonly validate = output<void>();

  @ViewChild('editorTextarea') textareaRef!: ElementRef<HTMLTextAreaElement>;

  protected readonly showAutocomplete = signal<boolean>(false);
  protected readonly autocompleteSuggestions = signal<string[]>([]);
  protected readonly selectedSuggestionIndex = signal<number>(0);

  readonly lineNumbers = computed<number[]>(() => {
    const text = this.script();
    const count = (text.match(/\n/g) || []).length + 1;
    return Array.from({ length: Math.max(1, count) }, (_, i) => i + 1);
  });

  readonly errorDiagnostics = computed(() =>
    this.diagnostics().filter((d) => d.severity === 'ERROR')
  );

  onInput(event: Event): void {
    const target = event.target as HTMLTextAreaElement;
    const value = target.value;
    this.scriptChange.emit(value);
    this.checkAutocomplete(target);
  }

  onKeyDown(event: KeyboardEvent): void {
    // ⌘+Enter or Ctrl+Enter to execute
    if ((event.metaKey || event.ctrlKey) && event.key === 'Enter') {
      event.preventDefault();
      this.execute.emit();
      return;
    }

    // Tab key support
    if (event.key === 'Tab') {
      event.preventDefault();
      const textarea = this.textareaRef.nativeElement;
      const start = textarea.selectionStart;
      const end = textarea.selectionEnd;
      const val = textarea.value;
      const newVal = val.substring(0, start) + '  ' + val.substring(end);
      textarea.value = newVal;
      textarea.selectionStart = textarea.selectionEnd = start + 2;
      this.scriptChange.emit(newVal);
      return;
    }

    // Autocomplete navigation
    if (this.showAutocomplete()) {
      if (event.key === 'ArrowDown') {
        event.preventDefault();
        const next = (this.selectedSuggestionIndex() + 1) % this.autocompleteSuggestions().length;
        this.selectedSuggestionIndex.set(next);
        return;
      }
      if (event.key === 'ArrowUp') {
        event.preventDefault();
        const prev =
          (this.selectedSuggestionIndex() - 1 + this.autocompleteSuggestions().length) %
          this.autocompleteSuggestions().length;
        this.selectedSuggestionIndex.set(prev);
        return;
      }
      if (event.key === 'Enter' || event.key === 'Tab') {
        event.preventDefault();
        this.applySuggestion(this.autocompleteSuggestions()[this.selectedSuggestionIndex()]);
        return;
      }
      if (event.key === 'Escape') {
        this.showAutocomplete.set(false);
        return;
      }
    }
  }

  private checkAutocomplete(textarea: HTMLTextAreaElement): void {
    const pos = textarea.selectionStart;
    const text = textarea.value.slice(0, pos);
    const lastWordMatch = text.match(/([A-Z_]+)$/i);
    if (lastWordMatch && lastWordMatch[1].length >= 2) {
      const prefix = lastWordMatch[1].toUpperCase();
      const matches = MEL_KEYWORDS.filter((k) => k.startsWith(prefix) && k !== prefix);
      if (matches.length > 0) {
        this.autocompleteSuggestions.set(matches.slice(0, 6));
        this.selectedSuggestionIndex.set(0);
        this.showAutocomplete.set(true);
        return;
      }
    }
    this.showAutocomplete.set(false);
  }

  applySuggestion(suggestion: string): void {
    if (!suggestion) return;
    const textarea = this.textareaRef.nativeElement;
    const pos = textarea.selectionStart;
    const text = textarea.value;
    const before = text.slice(0, pos);
    const after = text.slice(pos);
    const match = before.match(/([A-Za-z_]+)$/);
    if (match) {
      const replaceStart = pos - match[1].length;
      const newText = text.slice(0, replaceStart) + suggestion + ' ' + after;
      textarea.value = newText;
      textarea.selectionStart = textarea.selectionEnd = replaceStart + suggestion.length + 1;
      this.scriptChange.emit(newText);
    }
    this.showAutocomplete.set(false);
  }

  onFormat(): void {
    const raw = this.script();
    // Clean trailing spaces and normalize line breaks
    const formatted = raw
      .split('\n')
      .map((l) => l.trimEnd())
      .join('\n');
    this.scriptChange.emit(formatted);
  }
}
