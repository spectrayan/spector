import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSliderModule } from '@angular/material/slider';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MemoryTableService } from '@core/services/memory-table.service';

@Component({
  selector: 'cortex-add-memory-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatSliderModule,
    MatChipsModule,
    MatSnackBarModule,
    MatProgressBarModule,
    MatButtonToggleModule,
    MatTabsModule,
    MatTooltipModule,
  ],
  templateUrl: './add-memory-dialog.component.html',
  styleUrl: './add-memory-dialog.component.scss',
})
export class AddMemoryDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<AddMemoryDialogComponent>);
  private readonly memoryService = inject(MemoryTableService);
  private readonly snackBar = inject(MatSnackBar);

  // ── Mode toggle ──
  mode: 'text' | 'ingest' = 'text';
  ingestMethod: 'file' | 'folder' = 'file';

  // ── Text mode fields ──
  memoryId = '';
  text = '';
  tags = '';
  interest = 50;
  challenge = 30;
  urgency = 20;
  valence = 0;
  arousal = 50;

  // ── File mode fields ──
  selectedFiles: File[] = [];
  isDragOver = false;

  // ── Folder mode fields ──
  folderName = '';

  // ── Shared ──
  tier = 'SEMANTIC';
  source = 'OBSERVED';
  submitting = false;
  ingestResult: { success: boolean; message: string } | null = null;

  readonly tiers = [
    { value: 'WORKING', color: '#f59e0b' },
    { value: 'EPISODIC', color: '#3b82f6' },
    { value: 'SEMANTIC', color: '#8b5cf6' },
    { value: 'PROCEDURAL', color: '#10b981' },
  ];

  readonly sources = ['USER_STATED', 'OBSERVED', 'INFERRED', 'PROCEDURAL'];

  generateId(): void {
    const ts = Date.now().toString(36);
    const rand = Math.random().toString(36).substring(2, 6);
    this.memoryId = `${ts}-${rand}`;
  }

  canSubmit(): boolean {
    if (this.mode === 'text') {
      return !!this.text.trim();
    }
    // ingest mode
    if (this.ingestMethod === 'file') {
      return this.selectedFiles.length > 0;
    }
    return this.selectedFiles.length > 0;
  }

  // ── File Upload Helpers ──

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;

    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      for (let i = 0; i < files.length; i++) {
        if (!this.selectedFiles.some((f) => f.name === files[i].name)) {
          this.selectedFiles.push(files[i]);
        }
      }
    }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      for (let i = 0; i < input.files.length; i++) {
        if (!this.selectedFiles.some((f) => f.name === input.files![i].name)) {
          this.selectedFiles.push(input.files[i]);
        }
      }
    }
    // Reset input so same file can be re-selected
    input.value = '';
  }

  removeFile(event: Event, file: File): void {
    event.stopPropagation();
    this.selectedFiles = this.selectedFiles.filter((f) => f !== file);
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  // ── Submit ──

  onSubmit(): void {
    this.ingestResult = null;
    this.submitting = true;

    if (this.mode === 'text') {
      this.submitText();
    } else {
      // Both 'file' and 'folder' modes use the same file upload path
      this.submitFiles();
    }
  }

  private submitText(): void {
    if (!this.text.trim()) return;

    if (!this.memoryId.trim()) {
      this.generateId();
    }

    this.memoryService
      .remember({
        id: this.memoryId,
        text: this.text,
        tier: this.tier,
        source: this.source,
        tags: this.tags,
        interest: this.interest / 100,
        challenge: this.challenge / 100,
        urgency: this.urgency / 100,
        valence: this.valence,
        arousal: this.arousal,
      })
      .subscribe({
        next: (resp) => {
          this.snackBar.open(
            `Memory "${this.memoryId}" submitted — processing in background`,
            'OK',
            { duration: 3000, panelClass: 'cortex-snackbar' },
          );
          this.dialogRef.close(true);
        },
        error: (err) => {
          this.snackBar.open(`Failed: ${err.message}`, 'Dismiss', {
            duration: 5000,
            panelClass: 'cortex-snackbar-error',
          });
          this.submitting = false;
        },
      });
  }

  private submitFiles(): void {
    if (this.selectedFiles.length === 0) return;

    let completed = 0;
    let failures = 0;
    const total = this.selectedFiles.length;

    for (const file of this.selectedFiles) {
      this.memoryService.ingestFile(file, this.tier, this.source).subscribe({
        next: () => {
          completed++;
          if (completed >= total) {
            this.snackBar.open(
              `${total} file(s) submitted for ingestion — check the bell for progress`,
              'OK',
              {
                duration: 4000,
                panelClass: failures > 0 ? 'cortex-snackbar-error' : 'cortex-snackbar',
              },
            );
            this.dialogRef.close(true);
          }
        },
        error: () => {
          completed++;
          failures++;
          if (completed >= total) {
            this.snackBar.open(
              `${total - failures}/${total} files submitted (${failures} failed)`,
              'Dismiss',
              { duration: 5000, panelClass: 'cortex-snackbar-error' },
            );
            this.submitting = false;
          }
        },
      });
    }
  }

  /** Handles folder selection via webkitdirectory input. */
  onFolderSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) return;

    // Filter to supported text file extensions
    const supportedExts = new Set([
      '.txt', '.md', '.java', '.py', '.js', '.ts', '.json', '.xml',
      '.yaml', '.yml', '.csv', '.html', '.css', '.rs', '.go', '.c',
      '.cpp', '.h', '.hpp', '.kt', '.scala', '.rb', '.sh', '.bat',
      '.ps1', '.sql', '.toml', '.ini', '.cfg', '.log', '.properties',
    ]);

    this.selectedFiles = [];
    for (let i = 0; i < input.files.length; i++) {
      const file = input.files[i];
      const ext = file.name.includes('.') ? '.' + file.name.split('.').pop()!.toLowerCase() : '';
      // Skip hidden files and unsupported extensions
      if (!file.name.startsWith('.') && supportedExts.has(ext)) {
        this.selectedFiles.push(file);
      }
    }

    // Extract folder name from the first file's webkitRelativePath
    if (input.files.length > 0 && (input.files[0] as any).webkitRelativePath) {
      const path = (input.files[0] as any).webkitRelativePath as string;
      this.folderName = path.split('/')[0] || 'Selected folder';
    } else {
      this.folderName = 'Selected folder';
    }

    // Reset input so same folder can be re-selected
    input.value = '';
  }

  /** Formats total size of all selected files. */
  formatTotalSize(): string {
    const total = this.selectedFiles.reduce((sum, f) => sum + f.size, 0);
    return this.formatFileSize(total);
  }
}
