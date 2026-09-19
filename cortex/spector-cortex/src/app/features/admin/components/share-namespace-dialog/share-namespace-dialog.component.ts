import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { SynapseApiService } from '@core/services/synapse-api.service';

export interface ShareNamespaceDialogData {
  slug: string;
  namespaceId?: string;
}

export interface GrantItem {
  grantId: string;
  granteeAccountId: string;
  namespaceId: string;
  role: 'READER' | 'WRITER' | 'ADMIN' | 'OWNER';
  grantedBy: string;
  grantedAt: string;
  expiresAt?: string;
}

@Component({
  selector: 'cortex-share-namespace-dialog',
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
    MatChipsModule,
    MatProgressBarModule,
    MatSnackBarModule,
    MatTableModule,
    MatTooltipModule,
  ],
  templateUrl: './share-namespace-dialog.component.html',
  styleUrl: './share-namespace-dialog.component.scss',
})
export class ShareNamespaceDialogComponent implements OnInit {
  readonly data: ShareNamespaceDialogData = inject(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ShareNamespaceDialogComponent>);
  private readonly api = inject(SynapseApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly grants = signal<GrantItem[]>([]);
  readonly loading = signal(false);
  readonly submitting = signal(false);

  newGranteeId = '';
  newRole: 'READER' | 'WRITER' | 'ADMIN' = 'READER';
  expiryOption = 'never';

  displayedColumns = ['grantee', 'role', 'expires', 'actions'];

  ngOnInit(): void {
    this.loadGrants();
  }

  loadGrants(): void {
    this.loading.set(true);
    this.api.listNamespaceGrants(this.data.slug).subscribe({
      next: (grants) => {
        this.grants.set(grants || []);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.snackBar.open(`Failed to load grants: ${err.message || 'Error'}`, 'Dismiss', { duration: 3000 });
      }
    });
  }

  addGrant(): void {
    if (!this.newGranteeId) return;

    this.submitting.set(true);
    let expiresAt: string | undefined = undefined;
    const now = new Date();
    if (this.expiryOption === '1h') {
      expiresAt = new Date(now.getTime() + 3600 * 1000).toISOString();
    } else if (this.expiryOption === '1d') {
      expiresAt = new Date(now.getTime() + 86400 * 1000).toISOString();
    } else if (this.expiryOption === '7d') {
      expiresAt = new Date(now.getTime() + 7 * 86400 * 1000).toISOString();
    } else if (this.expiryOption === '30d') {
      expiresAt = new Date(now.getTime() + 30 * 86400 * 1000).toISOString();
    }

    this.api.createNamespaceGrant(this.data.slug, {
      granteeAccountId: this.newGranteeId.trim(),
      role: this.newRole,
      expiresAt
    }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.newGranteeId = '';
        this.snackBar.open('Grant created successfully', 'Dismiss', { duration: 2500 });
        this.loadGrants();
      },
      error: (err) => {
        this.submitting.set(false);
        this.snackBar.open(`Failed to create grant: ${err.message || 'Error'}`, 'Dismiss', { duration: 3500 });
      }
    });
  }

  revokeGrant(grantId: string): void {
    this.submitting.set(true);
    this.api.revokeNamespaceGrant(this.data.slug, grantId).subscribe({
      next: () => {
        this.submitting.set(false);
        this.snackBar.open('Grant revoked', 'Dismiss', { duration: 2500 });
        this.loadGrants();
      },
      error: (err) => {
        this.submitting.set(false);
        this.snackBar.open(`Failed to revoke grant: ${err.message || 'Error'}`, 'Dismiss', { duration: 3500 });
      }
    });
  }
}
