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

import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { SynapseApiService } from '@core/services/synapse-api.service';
import { AuthService } from '@core/services/auth.service';
import {
  AdminKeysComponent,
  maskKekReference,
} from './pages/admin-keys.component';
import { AdminBreakGlassComponent } from './pages/admin-break-glass.component';

describe('Admin Keys & Encryption Page (#1073)', () => {
  let isSuperAdminSignal = signal(true);
  let isAdminSignal = signal(true);
  let currentUserSignal = signal<any>({
    userId: 'admin-alice',
    username: 'alice',
    displayName: 'Alice Admin',
    profile: 'TEAM',
    roles: ['admin', 'super-admin'],
    scopes: ['admin'],
    tenantId: 'acme',
  });
  let snackBarMock: { open: ReturnType<typeof vi.fn> };
  let apiMock: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    isSuperAdminSignal = signal(true);
    isAdminSignal = signal(true);
    currentUserSignal = signal<any>({
      userId: 'admin-alice',
      username: 'alice',
      displayName: 'Alice Admin',
      profile: 'TEAM',
      roles: ['admin', 'super-admin'],
      scopes: ['admin'],
      tenantId: 'acme',
    });
    snackBarMock = { open: vi.fn() };

    apiMock = {
      listAdminTenants: vi.fn().mockReturnValue(
        of([
          { tenantId: 'acme', name: 'Acme Corp', plan: 'ENTERPRISE' },
          { tenantId: 'beta', name: 'Beta Labs', plan: 'TEAM' },
        ])
      ),
      getTenantKeyStatus: vi.fn().mockReturnValue(
        of({
          tenantId: 'acme',
          byokEnabled: true,
          provider: 'aws-kms',
          kekKeyId: 'arn:aws:kms:us-east-1:123456789012:key/mrk-acme-kek-998877',
          credentialName: 'aws-kms-prod',
          region: 'us-east-1',
          endpoint: null,
          keyVersion: 'v2',
          status: 'ACTIVE',
          healthStatus: 'HEALTHY',
          activeNamespaceDekCount: 4,
          lastVerifiedAt: '2026-10-19T12:00:00Z',
          lastError: null,
          createdAt: '2026-10-01T00:00:00Z',
          updatedAt: '2026-10-19T10:30:00Z',
          supportedProviders: [
            'aws-kms',
            'gcp-kms',
            'azure-key-vault',
            'hashicorp-vault',
            'local-file',
            'platform-hkdf',
          ],
        })
      ),
      configureTenantKey: vi.fn().mockReturnValue(
        of({
          tenantId: 'acme',
          byokEnabled: true,
          provider: 'gcp-kms',
          kekKeyId: 'projects/acme/locations/global/keyRings/ring/cryptoKeys/kek-1',
          credentialName: 'gcp-kms-cred',
          region: 'global',
          endpoint: null,
          keyVersion: 'v1',
          status: 'ACTIVE',
          healthStatus: 'HEALTHY',
          activeNamespaceDekCount: 4,
          lastVerifiedAt: '2026-10-19T14:00:00Z',
          lastError: null,
          createdAt: '2026-10-01T00:00:00Z',
          updatedAt: '2026-10-19T14:00:00Z',
          supportedProviders: ['aws-kms', 'gcp-kms', 'local-file'],
        })
      ),
      validateTenantKey: vi.fn().mockReturnValue(
        of({
          tenantId: 'acme',
          provider: 'aws-kms',
          kekKeyId: 'arn:aws:kms:us-east-1:123456789012:key/mrk-acme-kek-998877',
          valid: true,
          healthStatus: 'HEALTHY',
          roundTripVerified: true,
          evictedCachedDeks: 0,
          verifiedAt: '2026-10-19T15:00:00Z',
          errorCode: null,
          message: 'KMS KEK health and wrap/unwrap round-trip verified',
        })
      ),
      rotateTenantKek: vi.fn().mockReturnValue(
        of({
          tenantId: 'acme',
          provider: 'aws-kms',
          previousKekKeyId: 'arn:aws:kms:us-east-1:123456789012:key/mrk-acme-kek-998877',
          previousKekVersion: 'v2',
          newKekKeyId: 'arn:aws:kms:us-east-1:123456789012:key/mrk-acme-kek-998877',
          newKekVersion: 'v3',
          rewrappedDekCount: 4,
          refreshedCacheCount: 4,
          dataFilesRewritten: false,
          rotatedAt: '2026-10-19T15:30:00Z',
        })
      ),
      rotateNamespaceDek: vi.fn().mockReturnValue(
        of({
          tenantId: 'acme',
          rotatedNamespaceCount: 2,
          namespaces: [
            {
              namespaceId: 'ns-alpha',
              previousDekVersion: 1,
              newDekVersion: 2,
              partitionBundlesEncrypted: 1,
              recordsReKeyed: 12,
              walEventsEncrypted: 15,
              runtimeBundleEncrypted: true,
            },
            {
              namespaceId: 'ns-beta',
              previousDekVersion: 1,
              newDekVersion: 2,
              partitionBundlesEncrypted: 2,
              recordsReKeyed: 28,
              walEventsEncrypted: 30,
              runtimeBundleEncrypted: true,
            },
          ],
          rotatedAt: '2026-10-19T16:00:00Z',
        })
      ),
      revokeTenantKey: vi.fn().mockReturnValue(
        of({
          tenantId: 'acme',
          status: 'KEY_REVOKED',
          healthStatus: 'REVOKED',
          revokedNamespaceDekCount: 4,
          evictedCachedDeks: 4,
          evictedMemoryInstances: 2,
          reason: 'Emergency administrative crypto-shred via Spector Admin Console',
          revokedAt: '2026-10-19T16:30:00Z',
        })
      ),
      queryAdminAuditEvents: vi.fn().mockReturnValue(
        of([
          {
            id: 'aud-key-1',
            createdAt: '2026-10-19T12:00:00Z',
            actorId: 'admin-alice',
            tenantId: 'acme',
            action: 'CONFIGURE_TENANT_BYOK_KEY',
            targetType: 'TENANT_KEY',
            targetId: 'acme',
            details: { provider: 'aws-kms', keyVersion: 'v2' },
          },
          {
            id: 'aud-key-2',
            createdAt: '2026-10-19T15:30:00Z',
            actorId: 'admin-alice',
            tenantId: 'acme',
            action: 'ROTATE_TENANT_KEK',
            targetType: 'TENANT_KEY',
            targetId: 'acme',
            details: { newKekVersion: 'v3', rewrappedDekCount: 4, dataFilesRewritten: false },
          },
        ])
      ),
      getAdminTenant: vi.fn().mockReturnValue(
        of({
          tenantId: 'acme',
          byokEnabled: true,
          settings: { byokEnabled: true },
        })
      ),
      listBreakGlassRequests: vi.fn().mockReturnValue(of([])),
    };

    TestBed.configureTestingModule({
      imports: [
        NoopAnimationsModule,
        AdminKeysComponent,
        AdminBreakGlassComponent,
      ],
      providers: [
        { provide: SynapseApiService, useValue: apiMock },
        {
          provide: AuthService,
          useValue: {
            isSuperAdmin: isSuperAdminSignal,
            isAdmin: isAdminSignal,
            currentUser: currentUserSignal,
          },
        },
        { provide: MatSnackBar, useValue: snackBarMock },
      ],
    });
  });

  it('masks KEK references by default and reveals full reference ID on toggle (never exposing key bytes)', () => {
    expect(maskKekReference(null)).toBe('Platform Default (HKDF-SHA256)');
    expect(
      maskKekReference('arn:aws:kms:us-east-1:123456789012:key/mrk-acme-kek-998877')
    ).toBe('arn:aws:••••••••998877');

    const fixture = TestBed.createComponent(AdminKeysComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('#keys-byok-enabled-badge')?.textContent).toContain(
      'BYOK Customer-Managed KEK'
    );
    expect(el.querySelector('#keys-kms-provider')?.textContent).toContain('aws-kms');
    expect(el.querySelector('#keys-masked-kek-ref')?.textContent?.trim()).toBe(
      'arn:aws:••••••••998877'
    );
    expect(el.querySelector('#keys-active-dek-count')?.textContent?.trim()).toBe('4');
    expect(el.querySelector('#keys-break-glass-unavailable-banner')?.textContent).toContain(
      'Break-Glass Access: Unavailable (Tenant uses BYOK — SPE-820-004)'
    );

    // Toggle reveal KEK reference
    comp.toggleRevealKekRef();
    fixture.detectChanges();
    expect(el.querySelector('#keys-masked-kek-ref')?.textContent?.trim()).toBe(
      'arn:aws:kms:us-east-1:123456789012:key/mrk-acme-kek-998877'
    );
  });

  it('registers/updates KEK configuration and runs live KMS health validation', () => {
    const fixture = TestBed.createComponent(AdminKeysComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();

    comp.configureDraft = {
      provider: 'gcp-kms',
      kekKeyId: 'projects/acme/locations/global/keyRings/ring/cryptoKeys/kek-1',
      credentialName: 'gcp-kms-cred',
      region: 'global',
      endpoint: '',
    };
    comp.submitConfigureKek();
    fixture.detectChanges();

    expect(apiMock['configureTenantKey']).toHaveBeenCalledWith('acme', {
      provider: 'gcp-kms',
      kekKeyId: 'projects/acme/locations/global/keyRings/ring/cryptoKeys/kek-1',
      credentialName: 'gcp-kms-cred',
      region: 'global',
      endpoint: null,
    });

    comp.runValidateKey();
    fixture.detectChanges();
    expect(apiMock['validateTenantKey']).toHaveBeenCalledWith('acme');
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('#keys-validation-result-box')?.textContent).toContain(
      'KMS Live Self-Test: PASSED'
    );
  });

  it('rotates KEK (envelope re-wrap) and rotates namespace DEKs with re-encryption progress table', () => {
    const fixture = TestBed.createComponent(AdminKeysComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();

    // 1. KEK rotation requires confirmation
    comp.rotateKekDraft.newKekVersion = 'v3';
    comp.requestRotateKek();
    fixture.detectChanges();
    expect(apiMock['rotateTenantKek']).not.toHaveBeenCalled();

    comp.executeConfirmedAction();
    fixture.detectChanges();
    expect(apiMock['rotateTenantKek']).toHaveBeenCalledWith('acme', {
      newKekKeyId: null,
      newKekVersion: 'v3',
    });
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('#keys-kek-rotation-summary')?.textContent).toContain(
      'No (0 bytes)'
    );

    // 2. Namespace DEK rotation requires confirmation and displays re-encryption progress
    comp.rotateDekDraft.namespaceId = '';
    comp.requestRotateDek();
    fixture.detectChanges();
    expect(apiMock['rotateNamespaceDek']).not.toHaveBeenCalled();

    comp.executeConfirmedAction();
    fixture.detectChanges();
    expect(apiMock['rotateNamespaceDek']).toHaveBeenCalledWith('acme', {
      namespaceId: null,
    });
    expect(comp.dekRotationItems().length).toBe(2);
    const progressEl = el.querySelector('#keys-dek-reencryption-progress');
    expect(progressEl?.textContent).toContain('2 / 2 namespaces completed (100%)');
    expect(progressEl?.textContent).toContain('ns-alpha');
    expect(progressEl?.textContent).toContain('ns-beta');
  });

  it('explains KMS revocation consequences, supports emergency crypto-shredding, and renders key audit events', () => {
    const fixture = TestBed.createComponent(AdminKeysComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const guidance = el.querySelector('#keys-revocation-consequences')?.textContent || '';
    expect(guidance).toContain('Customer KMS Revocation');
    expect(guidance).toContain('SPE-820-010');

    const auditTable = el.querySelector('#keys-audit-table')?.textContent || '';
    expect(auditTable).toContain('CONFIGURE_TENANT_BYOK_KEY');
    expect(auditTable).toContain('ROTATE_TENANT_KEK');

    // Trigger emergency crypto-shredding with confirmation
    comp.requestEmergencyCryptoShred();
    fixture.detectChanges();
    expect(apiMock['revokeTenantKey']).not.toHaveBeenCalled();

    comp.executeConfirmedAction();
    fixture.detectChanges();
    expect(apiMock['revokeTenantKey']).toHaveBeenCalledWith('acme', {
      reason: 'Emergency administrative crypto-shred via Spector Admin Console',
    });
  });

  it('shows "Unavailable: tenant uses BYOK" on Break-Glass UI when tenant has BYOK enabled', () => {
    const fixture = TestBed.createComponent(AdminBreakGlassComponent);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const banner = el.querySelector('#break-glass-byok-prohibited-banner');
    expect(banner?.textContent).toContain('Unavailable: tenant uses BYOK');
    expect(banner?.textContent).toContain('SPE-820-004');
  });
});
