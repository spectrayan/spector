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
import { provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { SynapseApiService } from '@core/services/synapse-api.service';
import { AuthService } from '@core/services/auth.service';
import { AdminOverviewComponent } from './pages/admin-overview.component';
import { AdminTenantsComponent } from './pages/admin-tenants.component';
import { AdminAccountsComponent } from './pages/admin-accounts.component';
import { AdminNamespacesComponent } from './pages/admin-namespaces.component';

describe('Admin Console Core Pages (#1064)', () => {
  let isSuperAdminSignal = signal(true);
  let isAdminSignal = signal(true);
  let tenantIdSignal = signal('acme');
  let snackBarMock: { open: ReturnType<typeof vi.fn> };

  let apiMock: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    isSuperAdminSignal = signal(true);
    isAdminSignal = signal(true);
    tenantIdSignal = signal('acme');
    snackBarMock = { open: vi.fn() };

    apiMock = {
      getFleetAnalytics: vi.fn().mockReturnValue(
        of({
          tenantCount: 2,
          activeTenantCount: 2,
          accountCount: 6,
          humanCount: 3,
          agentCount: 3,
          namespaceCount: 12,
          storageBytes: 10485760,
          tiers: { working: 200, episodic: 1800, semantic: 2000, procedural: 800, total: 4800 },
          throughput: {
            recallOpsPerSec: 12.5,
            rememberOpsPerSec: 4.2,
            latencyP95Ms: 8.4,
            errorRate: 0.005,
            errorCount: 2,
          },
          quotas: {
            quotaBreachCount: 1,
            storageUtilizationPct: 88.5,
            memoryUtilizationPct: 45.0,
            namespaceUtilizationPct: 60.0,
          },
          tenants: [
            {
              tenantId: 'acme',
              displayName: 'Acme Corp',
              plan: 'ENTERPRISE',
              status: 'ACTIVE',
              accountCount: 4,
              namespaceCount: 8,
              storageBytes: 8388608,
            },
          ],
          generatedAt: '2026-10-17T12:00:00Z',
        }),
      ),
      getCallerTenantAnalytics: vi.fn().mockReturnValue(
        of({
          tenantId: 'acme',
          status: 'ACTIVE',
          accountCount: 4,
          humanCount: 2,
          agentCount: 2,
          namespaceCount: 8,
          storageBytes: 8388608,
          tiers: { working: 100, episodic: 1200, semantic: 1400, procedural: 500, total: 3200 },
          throughput: {
            recallOpsPerSec: 8.0,
            rememberOpsPerSec: 2.5,
            latencyP95Ms: 6.2,
            errorRate: 0.002,
            errorCount: 1,
          },
          quotas: {
            quotaBreachCount: 1,
            storageUtilizationPct: 90.0,
            memoryUtilizationPct: 50.0,
            namespaceUtilizationPct: 40.0,
          },
          generatedAt: '2026-10-17T12:00:00Z',
        }),
      ),
      listAdminTenants: vi.fn().mockReturnValue(
        of([
          {
            tenantId: 'acme',
            name: 'Acme Corp',
            description: 'Enterprise tenant',
            status: 'ACTIVE',
            plan: 'ENTERPRISE',
            createdAt: '2026-10-01T00:00:00Z',
            updatedAt: '2026-10-15T00:00:00Z',
            settings: {
              byokEnabled: true,
              breakGlassApproverRequired: true,
              breakGlassMaxDurationMinutes: 60,
            },
            quotas: {
              maxAccounts: 50,
              maxNamespaces: 200,
              maxStorageBytes: 10737418240,
            },
          },
          {
            tenantId: 'beta',
            name: 'Beta Labs',
            description: 'Team tenant',
            status: 'SUSPENDED',
            plan: 'TEAM',
            createdAt: '2026-10-02T00:00:00Z',
            updatedAt: '2026-10-16T00:00:00Z',
            settings: {
              byokEnabled: false,
              breakGlassApproverRequired: false,
              breakGlassMaxDurationMinutes: 30,
            },
            quotas: {
              maxAccounts: 10,
              maxNamespaces: 40,
              maxStorageBytes: 1073741824,
            },
          },
        ]),
      ),
      createAdminTenant: vi.fn().mockReturnValue(
        of({
          tenantId: 'gamma',
          name: 'Gamma AI',
          status: 'ACTIVE',
          plan: 'TEAM',
          createdAt: '2026-10-17T12:00:00Z',
          updatedAt: '2026-10-17T12:00:00Z',
          settings: {
            byokEnabled: false,
            breakGlassApproverRequired: true,
            breakGlassMaxDurationMinutes: 60,
          },
          quotas: {
            maxAccounts: 25,
            maxNamespaces: 100,
            maxStorageBytes: 10737418240,
          },
        }),
      ),
      updateAdminTenant: vi.fn().mockReturnValue(
        of({
          tenantId: 'acme',
          name: 'Acme Corp',
          status: 'SUSPENDED',
          plan: 'ENTERPRISE',
          createdAt: '2026-10-01T00:00:00Z',
          updatedAt: '2026-10-17T12:05:00Z',
          settings: {
            byokEnabled: true,
            breakGlassApproverRequired: true,
            breakGlassMaxDurationMinutes: 60,
          },
          quotas: {
            maxAccounts: 50,
            maxNamespaces: 200,
            maxStorageBytes: 10737418240,
          },
        }),
      ),
      listAdminTenantAuditLogs: vi.fn().mockReturnValue(
        of([
          {
            auditId: 'aud-tenant-9001',
            tenantId: 'acme',
            actorId: 'root-admin',
            action: 'TENANT_SUSPEND',
            details: { status: 'SUSPENDED' },
            timestamp: '2026-10-17T12:05:00Z',
          },
        ]),
      ),
      listAdminAccounts: vi.fn().mockReturnValue(
        of([
          {
            accountId: 'acc-1',
            username: 'alice',
            displayName: 'Alice Operator',
            tenantId: 'acme',
            kind: 'HUMAN',
            ownerAccountId: null,
            profile: 'TEAM',
            roles: ['admin', 'editor'],
            active: true,
            quotas: {
              maxNamespaces: 20,
              maxStorageBytes: 2147483648,
            },
            namespaceCount: 3,
            totalStorageBytes: 524288,
            apiKeyCount: 2,
            ownedAgents: [
              {
                accountId: 'acc-agent-1',
                username: 'agent-triage',
                displayName: 'Triage Agent',
                active: true,
                createdAt: '2026-10-10T00:00:00Z',
                lastLoginAt: '2026-10-17T11:00:00Z',
              },
            ],
            namespaceGrants: [],
            createdAt: '2026-10-01T00:00:00Z',
            lastLoginAt: '2026-10-17T10:00:00Z',
          },
        ]),
      ),
      createAdminAccount: vi.fn().mockReturnValue(
        of({
          accountId: 'acc-2',
          username: 'bot-runner',
          displayName: 'Bot Runner',
          tenantId: 'acme',
          kind: 'AGENT',
          ownerAccountId: 'acc-1',
          profile: 'AGENT',
          roles: ['agent'],
          active: true,
          namespaceCount: 0,
          totalStorageBytes: 0,
          apiKeyCount: 0,
          ownedAgents: [],
          namespaceGrants: [],
          createdAt: '2026-10-17T12:10:00Z',
          lastLoginAt: null,
        }),
      ),
      updateAdminAccount: vi.fn().mockReturnValue(
        of({
          accountId: 'acc-1',
          username: 'alice',
          displayName: 'Alice Operator',
          tenantId: 'acme',
          kind: 'HUMAN',
          ownerAccountId: null,
          profile: 'TEAM',
          roles: ['admin', 'editor'],
          active: false,
          namespaceCount: 3,
          totalStorageBytes: 524288,
          apiKeyCount: 2,
          ownedAgents: [],
          namespaceGrants: [],
          createdAt: '2026-10-01T00:00:00Z',
          lastLoginAt: '2026-10-17T10:00:00Z',
        }),
      ),
      resetAccountCredentials: vi.fn().mockReturnValue(
        of({
          accountId: 'acc-1',
          revokedCredentialCount: 2,
          passwordCleared: true,
        }),
      ),
      listAccountAuditLogs: vi.fn().mockReturnValue(
        of([
          {
            auditId: 'aud-acc-status-88',
            tenantId: 'acme',
            actorId: 'root-admin',
            targetAccountId: 'acc-1',
            action: 'ACCOUNT_SUSPENDED',
            details: {},
            timestamp: '2026-10-17T12:15:00Z',
          },
        ]),
      ),
      listAdminCredentials: vi.fn().mockReturnValue(
        of([
          {
            name: 'prod-ingest-key',
            category: 'AUTH',
            type: 'API_KEY',
            provider: 'spector',
            description: 'Production agent key',
            maskedPreview: 'sk_spec_****9f2a',
            properties: {},
            active: true,
            createdAt: '2026-10-05T00:00:00Z',
            updatedAt: '2026-10-05T00:00:00Z',
            lastUsedAt: '2026-10-17T11:30:00Z',
          },
        ]),
      ),
      deleteAdminCredential: vi.fn().mockReturnValue(of(undefined)),
      listAdminNamespaces: vi.fn().mockReturnValue(
        of([
          {
            namespaceId: 'ns-1',
            slug: 'research-core',
            displayName: 'Research Core',
            type: 'TEAM',
            ownerAccountId: 'acc-1',
            ownerUsername: 'alice',
            tenantId: 'acme',
            status: 'ACTIVE',
            legalHold: false,
            legalHoldReason: null,
            quotas: {
              maxMemories: 10000,
              maxStorageBytes: 1073741824,
              maxOpsPerMinute: 600,
            },
            memoryCount: 1250,
            storageBytes: 4194304,
            grantCount: 2,
            createdAt: '2026-10-01T00:00:00Z',
            archivedAt: null,
            lastActivityAt: '2026-10-17T11:45:00Z',
          },
        ]),
      ),
      updateAdminNamespaceQuotas: vi.fn().mockReturnValue(
        of({
          namespaceId: 'ns-1',
          slug: 'research-core',
          status: 'ACTIVE',
        }),
      ),
      archiveAdminNamespace: vi.fn().mockReturnValue(
        of({
          namespaceId: 'ns-1',
          slug: 'research-core',
          status: 'ARCHIVED',
        }),
      ),
      eraseAdminNamespace: vi.fn().mockReturnValue(
        of({
          tenantId: 'research-core',
          startedAt: '2026-10-17T12:30:00Z',
          completedAt: '2026-10-17T12:30:01Z',
          purgedCount: 1250,
          zeroedSlabBytes: 4194304,
          droppedRelationalRows: 1250,
          tombstoned: true,
          replicaNotified: true,
          auditId: 'aud-ns-erase-999',
        }),
      ),
      listAdminNamespaceGrants: vi.fn().mockReturnValue(
        of([
          {
            grantId: 'gr-1',
            namespaceId: 'ns-1',
            principalId: 'acc-1',
            principalUsername: 'alice',
            role: 'OWNER',
            grantedBy: 'system',
            createdAt: '2026-10-01T00:00:00Z',
          },
        ]),
      ),
      listAdminNamespaceAuditLogs: vi.fn().mockReturnValue(
        of([
          {
            auditId: 'aud-ns-archive-102',
            namespaceId: 'ns-1',
            action: 'NAMESPACE_ARCHIVE',
            actorId: 'root-admin',
            timestamp: '2026-10-17T12:20:00Z',
          },
        ]),
      ),
    };

    TestBed.configureTestingModule({
      imports: [
        NoopAnimationsModule,
        AdminOverviewComponent,
        AdminTenantsComponent,
        AdminAccountsComponent,
        AdminNamespacesComponent,
      ],
      providers: [
        provideRouter([]),
        { provide: SynapseApiService, useValue: apiMock },
        {
          provide: AuthService,
          useValue: {
            isSuperAdmin: isSuperAdminSignal,
            isAdmin: isAdminSignal,
            tenantId: tenantIdSignal,
          },
        },
        { provide: MatSnackBar, useValue: snackBarMock },
      ],
    });
  });

  describe('AdminOverviewComponent', () => {
    it('loads fleet analytics for super-admin and renders health KPI cards and quota alert banner', () => {
      const fixture = TestBed.createComponent(AdminOverviewComponent);
      fixture.detectChanges();

      expect(apiMock['getFleetAnalytics']).toHaveBeenCalled();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelectorAll('.kpi-card').length).toBe(7);
      expect(el.querySelector('#admin-overview-quota-alert')?.textContent).toContain(
        'Quota Alert:',
      );
      expect(el.querySelector('.admin-table')?.textContent).toContain('Acme Corp');
    });

    it('loads tenant-scoped analytics when operator is a tenant admin (non-super-admin)', () => {
      isSuperAdminSignal.set(false);
      const fixture = TestBed.createComponent(AdminOverviewComponent);
      fixture.detectChanges();

      expect(apiMock['getCallerTenantAnalytics']).toHaveBeenCalled();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelectorAll('.kpi-card').length).toBe(6);
      expect(el.querySelector('#admin-overview-quota-alert')?.textContent).toContain(
        '1 quota breach(es) recorded',
      );
    });
  });

  describe('AdminTenantsComponent', () => {
    it('renders tenant list with BYOK status badge and break-glass policy badge', () => {
      const fixture = TestBed.createComponent(AdminTenantsComponent);
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('Acme Corp');
      expect(el.textContent).toContain('BYOK Active (No Break-Glass)');
      expect(el.textContent).toContain('Blocked by BYOK');
      expect(el.textContent).toContain('Platform KEK');
      expect(el.textContent).toContain('Single-Actor');
    });

    it('requires confirmation before suspending a tenant and surfaces the returned auditId', () => {
      const fixture = TestBed.createComponent(AdminTenantsComponent);
      const comp = fixture.componentInstance;
      fixture.detectChanges();

      const acme = comp.tenants()[0];
      comp.requestToggleStatus(acme, 'SUSPENDED');
      fixture.detectChanges();

      expect(apiMock['updateAdminTenant']).not.toHaveBeenCalled();
      expect(
        fixture.nativeElement.querySelector('.confirm-card')?.textContent,
      ).toContain('Suspend Tenant: acme');

      comp.executeConfirmedAction();
      fixture.detectChanges();

      expect(apiMock['updateAdminTenant']).toHaveBeenCalledWith('acme', {
        status: 'SUSPENDED',
      });
      expect(
        fixture.nativeElement.querySelector('#tenant-last-audit-banner')?.textContent,
      ).toContain('aud-tenant-9001');
    });
  });

  describe('AdminAccountsComponent', () => {
    it('renders human and agent principals with profile, roles, and metadata-only API key count', () => {
      const fixture = TestBed.createComponent(AdminAccountsComponent);
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('Alice Operator');
      expect(el.textContent).toContain('HUMAN');
      expect(el.textContent).toContain('TEAM');
      expect(el.textContent).toContain('3 ns · 2 keys');
      expect(el.textContent).toContain('Keys & Agents (2)');
    });

    it('requires confirmation before suspending an account and surfaces the returned auditId', () => {
      const fixture = TestBed.createComponent(AdminAccountsComponent);
      const comp = fixture.componentInstance;
      fixture.detectChanges();

      const alice = comp.accounts()[0];
      comp.requestToggleActive(alice);
      fixture.detectChanges();

      expect(apiMock['updateAdminAccount']).not.toHaveBeenCalled();
      expect(
        fixture.nativeElement.querySelector('.confirm-card')?.textContent,
      ).toContain('Suspend Principal: alice');

      comp.executeConfirmedAction();
      fixture.detectChanges();

      expect(apiMock['updateAdminAccount']).toHaveBeenCalledWith('acc-1', {
        active: false,
      });
      expect(
        fixture.nativeElement.querySelector('#account-last-audit-banner')?.textContent,
      ).toContain('aud-acc-status-88');
    });

    it('loads metadata-only API keys in the oversight drawer without exposing raw secrets', () => {
      const fixture = TestBed.createComponent(AdminAccountsComponent);
      const comp = fixture.componentInstance;
      fixture.detectChanges();

      comp.inspectAccountOversight(comp.accounts()[0]);
      fixture.detectChanges();

      expect(apiMock['listAdminCredentials']).toHaveBeenCalledWith({
        userId: 'alice',
        tenantId: 'acme',
      });
      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('prod-ingest-key');
      expect(el.textContent).toContain('sk_spec_****9f2a');
    });
  });

  describe('AdminNamespacesComponent', () => {
    it('renders namespaces table with slug, type, owner, status, memories, and storage bytes', () => {
      const fixture = TestBed.createComponent(AdminNamespacesComponent);
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('research-core');
      expect(el.textContent).toContain('TEAM');
      expect(el.textContent).toContain('alice');
      expect(el.textContent).toContain('1,250');
    });

    it('requires confirmation before archiving a namespace and surfaces the returned auditId', () => {
      const fixture = TestBed.createComponent(AdminNamespacesComponent);
      const comp = fixture.componentInstance;
      fixture.detectChanges();

      const ns = comp.namespaces()[0];
      comp.requestArchive(ns);
      fixture.detectChanges();

      expect(apiMock['archiveAdminNamespace']).not.toHaveBeenCalled();
      expect(
        fixture.nativeElement.querySelector('.confirm-card')?.textContent,
      ).toContain('Archive Namespace: research-core');

      comp.executeConfirmedAction();
      fixture.detectChanges();

      expect(apiMock['archiveAdminNamespace']).toHaveBeenCalledWith('ns-1');
      expect(
        fixture.nativeElement.querySelector('#namespace-last-audit-banner')?.textContent,
      ).toContain('aud-ns-archive-102');
    });

    it('gates compliance erasure behind typed slug confirmation and surfaces erasure auditId', () => {
      const fixture = TestBed.createComponent(AdminNamespacesComponent);
      const comp = fixture.componentInstance;
      fixture.detectChanges();

      const ns = comp.namespaces()[0];
      comp.openEraseModal(ns);
      fixture.detectChanges();

      expect(comp.isEraseConfirmed()).toBe(false);
      comp.executeConfirmedErase(ns);
      expect(apiMock['eraseAdminNamespace']).not.toHaveBeenCalled();

      // Wrong slug still blocks erasure
      comp.eraseTypedSlug.set('wrong-slug');
      expect(comp.isEraseConfirmed()).toBe(false);

      // Exact slug unlocks erasure
      comp.eraseTypedSlug.set('research-core');
      comp.eraseReason = 'GDPR Article 17 erasure request';
      expect(comp.isEraseConfirmed()).toBe(true);

      comp.executeConfirmedErase(ns);
      fixture.detectChanges();

      expect(apiMock['eraseAdminNamespace']).toHaveBeenCalledWith('ns-1', {
        confirmationSlug: 'research-core',
        propagateReplicas: true,
        reason: 'GDPR Article 17 erasure request',
      });
      expect(
        fixture.nativeElement.querySelector('#namespace-last-audit-banner')?.textContent,
      ).toContain('aud-ns-erase-999');
    });
  });
});
