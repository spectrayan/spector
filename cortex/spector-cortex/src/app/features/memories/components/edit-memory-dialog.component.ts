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
  templateUrl: './edit-memory-dialog.component.html',
  styleUrl: './edit-memory-dialog.component.scss',
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
