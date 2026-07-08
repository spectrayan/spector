import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatChipsModule } from '@angular/material/chips';
import { MatSliderModule } from '@angular/material/slider';

export interface EditMemoryData {
  id: string;
  text: string;
  tier: string;
  tags: string[];
  importance: number;
  valence: number;
}

export interface EditMemoryResult {
  text?: string;
  tags?: string[];
}

@Component({
  selector: 'cortex-edit-memory-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatChipsModule,
    MatSliderModule,
  ],
  template: `
    <h2 mat-dialog-title class="dialog-title">
      <mat-icon>edit</mat-icon>
      Edit Memory
    </h2>

    <mat-dialog-content class="dialog-content">
      <div class="memory-id">
        <span class="id-label">ID:</span>
        <code class="id-value">{{ data.id }}</code>
      </div>

      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Memory Text</mat-label>
        <textarea matInput
                  [(ngModel)]="editText"
                  rows="12"
                  placeholder="Memory content..."
                  class="memory-textarea"></textarea>
        <mat-hint>{{ editText.length }} characters · ~{{ wordCount() }} words</mat-hint>
      </mat-form-field>

      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Tags (comma-separated)</mat-label>
        <input matInput
               [(ngModel)]="editTagsStr"
               placeholder="tag1, tag2, tag3" />
        <mat-hint>{{ parsedTags().length }} tags</mat-hint>
      </mat-form-field>

      <div class="tier-display">
        <span class="tier-label">Tier:</span>
        <span class="tier-badge">{{ data.tier }}</span>
        <span class="tier-note">(tier changes require re-ingestion)</span>
      </div>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button
              color="primary"
              [disabled]="!hasChanges()"
              (click)="save()">
        <mat-icon>save</mat-icon>
        Save Changes
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .dialog-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 18px;
      mat-icon { color: var(--mat-sys-primary); }
    }
    .dialog-content {
      min-width: 500px;
      max-width: 700px;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .memory-id {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 12px;
      background: rgba(255,255,255,0.05);
      border-radius: 6px;
      border: 1px solid rgba(255,255,255,0.1);
    }
    .id-label { opacity: 0.6; font-size: 12px; }
    .id-value { font-family: 'JetBrains Mono', monospace; font-size: 12px; opacity: 0.8; }
    .full-width { width: 100%; }
    .memory-textarea {
      font-family: 'JetBrains Mono', monospace;
      font-size: 13px;
      line-height: 1.6;
    }
    .tier-display {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 12px;
      background: rgba(255,255,255,0.05);
      border-radius: 6px;
    }
    .tier-label { opacity: 0.6; font-size: 13px; }
    .tier-badge {
      font-size: 11px;
      font-weight: 600;
      padding: 2px 8px;
      border-radius: 4px;
      background: var(--mat-sys-primary);
      color: var(--mat-sys-on-primary);
    }
    .tier-note { opacity: 0.4; font-size: 11px; font-style: italic; }
  `],
})
export class EditMemoryDialogComponent {
  readonly data: EditMemoryData = inject(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<EditMemoryDialogComponent>);

  editText = this.data.text || '';
  editTagsStr = (this.data.tags || []).join(', ');

  readonly wordCount = signal(0);

  constructor() {
    this.updateWordCount();
  }

  parsedTags(): string[] {
    return this.editTagsStr
      .split(',')
      .map(t => t.trim())
      .filter(t => t.length > 0);
  }

  hasChanges(): boolean {
    const textChanged = this.editText !== (this.data.text || '');
    const tagsChanged = this.parsedTags().join(',') !== (this.data.tags || []).join(',');
    return textChanged || tagsChanged;
  }

  updateWordCount(): void {
    const count = this.editText.trim() ? this.editText.trim().split(/\s+/).length : 0;
    this.wordCount.set(count);
  }

  save(): void {
    const result: EditMemoryResult = {};
    if (this.editText !== (this.data.text || '')) {
      result.text = this.editText;
    }
    if (this.parsedTags().join(',') !== (this.data.tags || []).join(',')) {
      result.tags = this.parsedTags();
    }
    this.dialogRef.close(result);
  }
}
