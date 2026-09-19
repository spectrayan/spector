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

import { Component, ChangeDetectionStrategy, input, output, model, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CdkTextareaAutosize } from '@angular/cdk/text-field';

@Component({
  selector: 'cortex-chat-input-bar',
  standalone: true,
  imports: [
    FormsModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    MatMenuModule,
    CdkTextareaAutosize,
  ],
  templateUrl: './chat-input-bar.component.html',
  styleUrl: './chat-input-bar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatInputBarComponent {
  private readonly snack = inject(MatSnackBar);

  readonly query = model<string>('');
  readonly attachedFile = model<{ name: string; content: string } | null>(null);
  readonly selectedTier = model<string>('EPISODIC');
  readonly tiers = input<string[]>(['EPISODIC', 'SEMANTIC', 'PROCEDURAL', 'WORKING']);
  readonly isStreaming = input<boolean>(false);
  readonly hasTurns = input<boolean>(false);

  readonly sendQuery = output<void>();
  readonly cancelStream = output<void>();

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = () => {
      this.attachedFile.set({
        name: file.name,
        content: reader.result as string,
      });
      this.snack.open(`Attached: ${file.name}`, 'OK', { duration: 2000 });
    };
    reader.readAsText(file);
    input.value = '';
  }

  removeAttachment(): void {
    this.attachedFile.set(null);
  }

  onTextareaKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.onSend();
    }
  }

  onSend(): void {
    if (this.query().trim() && !this.isStreaming()) {
      this.sendQuery.emit();
    }
  }
}
