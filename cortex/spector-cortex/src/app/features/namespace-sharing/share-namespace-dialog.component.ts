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
import { SynapseApiService } from '../../core/services/synapse-api.service';

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
  template: `
    <h2 mat-dialog-title class="dialog-title">
      <mat-icon class="title-icon">share</mat-icon>
      Share Namespace: <span class="namespace-slug">{{ data.slug }}</span>
    </h2>

    <mat-dialog-content class="dialog-content">
      @if (loading()) {
        <mat-progress-bar mode="indeterminate" class="loading-bar"></mat-progress-bar>
      }

      <!-- Add Grant Section -->
      <div class="add-grant-card">
        <h3>Grant Access</h3>
        <div class="form-row">
          <mat-form-field appearance="outline" class="grantee-field">
            <mat-label>Grantee Account TSID</mat-label>
            <input matInput [(ngModel)]="newGranteeId" placeholder="e.g. 0HD7XJ9A2B001" required />
            <mat-icon matPrefix>person_add</mat-icon>
          </mat-form-field>

          <mat-form-field appearance="outline" class="role-field">
            <mat-label>Role</mat-label>
            <mat-select [(ngModel)]="newRole">
              <mat-option value="READER">READER (Recall only)</mat-option>
              <mat-option value="WRITER">WRITER (Recall + Remember)</mat-option>
              <mat-option value="ADMIN">ADMIN (Full + Manage Grants)</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="expiry-field">
            <mat-label>Expiry</mat-label>
            <mat-select [(ngModel)]="expiryOption">
              <mat-option value="never">Never</mat-option>
              <mat-option value="1h">1 Hour</mat-option>
              <mat-option value="1d">1 Day</mat-option>
              <mat-option value="7d">7 Days</mat-option>
              <mat-option value="30d">30 Days</mat-option>
            </mat-select>
          </mat-form-field>

          <button mat-flat-button color="primary" class="grant-btn"
                  [disabled]="!newGranteeId || submitting()"
                  (click)="addGrant()">
            <mat-icon>check</mat-icon>
            Grant
          </button>
        </div>
      </div>

      <!-- Active Grants List -->
      <div class="grants-section">
        <h3>Active Grants</h3>
        @if (grants().length === 0 && !loading()) {
          <div class="empty-state">
            <mat-icon>lock</mat-icon>
            <p>No external grants. Only you have access to this namespace.</p>
          </div>
        } @else {
          <table mat-table [dataSource]="grants()" class="grants-table">
            <!-- Grantee Column -->
            <ng-container matColumnDef="grantee">
              <th mat-header-cell *matHeaderCellDef>Account ID</th>
              <td mat-cell *matCellDef="let grant">
                <code class="account-badge">{{ grant.granteeAccountId }}</code>
              </td>
            </ng-container>

            <!-- Role Column -->
            <ng-container matColumnDef="role">
              <th mat-header-cell *matHeaderCellDef>Role</th>
              <td mat-cell *matCellDef="let grant">
                <span class="role-chip" [attr.data-role]="grant.role">{{ grant.role }}</span>
              </td>
            </ng-container>

            <!-- Expiry Column -->
            <ng-container matColumnDef="expires">
              <th mat-header-cell *matHeaderCellDef>Expires</th>
              <td mat-cell *matCellDef="let grant">
                {{ grant.expiresAt ? (grant.expiresAt | date:'medium') : 'Never' }}
              </td>
            </ng-container>

            <!-- Actions Column -->
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let grant">
                @if (grant.role !== 'OWNER') {
                  <button mat-icon-button color="warn" matTooltip="Revoke Grant"
                          [disabled]="submitting()"
                          (click)="revokeGrant(grant.grantId)">
                    <mat-icon>delete_outline</mat-icon>
                  </button>
                }
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
          </table>
        }
      </div>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .dialog-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 18px;
      margin: 0;
      padding: 16px 24px;
    }
    .title-icon {
      color: var(--mat-sys-primary, #6750a4);
    }
    .namespace-slug {
      font-family: monospace;
      color: var(--mat-sys-primary, #6750a4);
      font-weight: 600;
    }
    .dialog-content {
      min-width: 580px;
      max-width: 700px;
      padding: 0 24px 16px;
    }
    .loading-bar {
      margin-bottom: 12px;
    }
    .add-grant-card {
      background: var(--mat-sys-surface-variant, #f4eff4);
      padding: 16px;
      border-radius: 8px;
      margin-bottom: 20px;
    }
    .add-grant-card h3, .grants-section h3 {
      margin-top: 0;
      margin-bottom: 12px;
      font-size: 14px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      color: var(--mat-sys-on-surface-variant, #49454f);
    }
    .form-row {
      display: flex;
      gap: 12px;
      align-items: center;
    }
    .grantee-field {
      flex: 2;
    }
    .role-field {
      flex: 1.5;
    }
    .expiry-field {
      flex: 1;
    }
    .grant-btn {
      height: 52px;
      margin-bottom: 20px;
    }
    .grants-table {
      width: 100%;
      background: transparent;
    }
    .account-badge {
      background: var(--mat-sys-surface-container-high, #ece6f0);
      padding: 2px 6px;
      border-radius: 4px;
      font-size: 12px;
    }
    .role-chip {
      display: inline-block;
      padding: 2px 8px;
      border-radius: 12px;
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
    }
    .role-chip[data-role="READER"] {
      background: #e3f2fd;
      color: #1565c0;
    }
    .role-chip[data-role="WRITER"] {
      background: #e8f5e9;
      color: #2e7d32;
    }
    .role-chip[data-role="ADMIN"] {
      background: #fff3e0;
      color: #e65100;
    }
    .role-chip[data-role="OWNER"] {
      background: #f3e5f5;
      color: #7b1fa2;
    }
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 24px;
      color: var(--mat-sys-outline, #79747e);
      gap: 8px;
    }
  `]
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
