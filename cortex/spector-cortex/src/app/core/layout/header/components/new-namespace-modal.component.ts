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

import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { SynapseApiService } from '@core/services/synapse-api.service';
import { CortexStateService } from '@core/services/cortex-state.service';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';

export interface NewNamespaceResult {
  created: boolean;
  slug: string;
  namespace?: any;
}

@Component({
  selector: 'cortex-new-namespace-modal',
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
    MatProgressBarModule,
  ],
  templateUrl: './new-namespace-modal.component.html',
  styleUrl: './new-namespace-modal.component.scss',
})
export class NewNamespaceModalComponent {
  private readonly dialogRef = inject(MatDialogRef<NewNamespaceModalComponent>);
  private readonly api = inject(SynapseApiService);
  private readonly state = inject(CortexStateService);
  private readonly snackBar = inject(MatSnackBar);

  readonly slug = signal('');
  readonly displayName = signal('');
  readonly type = signal<'PERSONAL' | 'ORGANIZATION'>('PERSONAL');
  readonly description = signal('');

  readonly isSubmitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  /** Format or validate slug on user input */
  onSlugInput(val: string): void {
    const sanitized = val
      .toLowerCase()
      .replace(/[^a-z0-9-_]/g, '-')
      .replace(/-+/g, '-');
    this.slug.set(sanitized);
    this.errorMessage.set(null);
  }

  canSubmit(): boolean {
    const s = this.slug().trim();
    return s.length >= 2 && !this.isSubmitting();
  }

  onSubmit(): void {
    const slugVal = this.slug().trim();
    if (!slugVal || this.isSubmitting()) return;

    this.isSubmitting.set(true);
    this.errorMessage.set(null);

    const payload = {
      slug: slugVal,
      displayName: this.displayName().trim() || undefined,
      type: this.type(),
      description: this.description().trim() || undefined,
    };

    this.api
      .createNamespace(payload)
      .pipe(
        catchError((err) => {
          const msg =
            err?.error?.message ||
            err?.message ||
            'Failed to create namespace. Please verify input and try again.';
          this.errorMessage.set(msg);
          this.isSubmitting.set(false);
          return of(null);
        }),
      )
      .subscribe((res) => {
        if (!res) return;

        this.snackBar.open(`Namespace "${slugVal}" created successfully`, 'Dismiss', {
          duration: 3500,
        });

        // Set as active namespace
        this.state.setCurrentNamespace(slugVal);
        this.api
          .setDefaultNamespace(slugVal)
          .pipe(catchError(() => of(null)))
          .subscribe();

        this.dialogRef.close({
          created: true,
          slug: slugVal,
          namespace: res,
        } as NewNamespaceResult);
      });
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }
}
