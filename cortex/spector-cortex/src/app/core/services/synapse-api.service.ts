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

import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Shared HTTP client for Synapse management API endpoints.
 */
@Injectable({ providedIn: 'root' })
export class SynapseApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;

  // ─── Connectors ───

  listConnectors(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/connectors/`);
  }

  getConnector(id: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/connectors/${id}`);
  }

  createConnector(config: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/connectors/`, config);
  }

  startConnector(id: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/connectors/${id}/start`, {});
  }

  stopConnector(id: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/connectors/${id}/stop`, {});
  }

  reloadConnector(id: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/connectors/${id}/reload`, {});
  }

  removeConnector(id: string): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/connectors/${id}`);
  }

  testConnection(config: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/connectors/test`, config);
  }

  getActiveRoutes(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/connectors/active`);
  }

  // ─── Templates ───

  listTemplates(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/templates/`);
  }

  getTemplate(id: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/templates/${id}`);
  }

  getTemplateCategories(): Observable<Record<string, any[]>> {
    return this.http.get<Record<string, any[]>>(`${this.baseUrl}/templates/categories`);
  }

  // ─── Providers ───

  listEmbeddingProviders(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/providers/embedding`);
  }

  listGenerationProviders(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/providers/generation`);
  }

  activateEmbeddingProvider(name: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/providers/embedding/${name}/activate`, {});
  }

  activateGenerationProvider(name: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/providers/generation/${name}/activate`, {});
  }

  getProviderHealth(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/providers/health`);
  }

  // ─── System ───

  getSystemHealth(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/system/health`);
  }

  getSystemInfo(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/system/info`);
  }

  // ─── Dead Letter Queue (DLQ) ───

  listDlq(tenantId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/system/dlq/${tenantId}`);
  }

  deleteDlq(tenantId: string, fileName: string): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/system/dlq/${tenantId}/${fileName}`);
  }

  redriveDlq(tenantId: string, fileName: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/system/dlq/${tenantId}/${fileName}/redrive`, {});
  }

  // ─── Salience Profiles ───

  getSalienceProfile(scope: string, id: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/salience/${scope}/${id}`);
  }

  saveSalienceProfile(scope: string, id: string, profile: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/salience/${scope}/${id}`, profile);
  }

  deleteSalienceProfile(scope: string, id: string): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/salience/${scope}/${id}`);
  }

  resolveEffectiveProfile(tenantId: string, agentId?: string, userId?: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/salience/resolve`, { tenantId, agentId, userId });
  }

  triggerRescore(strategy?: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/salience/rescore`, strategy ? { strategy } : {});
  }

  getRescoreStatus(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/salience/rescore/status`);
  }

  // ─── Hierarchical Configuration ───

  /** Lists all config categories with override policy metadata. */
  listConfigCategories(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/config/categories`);
  }

  /** Gets the effective (merged) config for a category. */
  getConfig(category: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/config/${category}`);
  }

  /** Gets effective config with source annotations (system/tenant/user per field). */
  getAnnotatedConfig(category: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/config/${category}/annotated`);
  }

  /** Gets raw override values at current scope (no merging). */
  getRawConfig(category: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/config/${category}/raw`);
  }

  /** Saves a config override (scope: 'tenant' | 'user'). */
  saveConfig(category: string, scope: string, values: Record<string, any>): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/config/${category}`, { scope, values });
  }

  /** Removes a config override, falling back to parent scope. */
  deleteConfig(category: string, scope: string): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/config/${category}`, {
      params: { scope }
    });
  }

  /** Gets the JSON schema for a config category (field names, types, constraints). */
  getConfigSchema(category: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/config/schema/${category}`);
  }

  /** Gets the current override policy. */
  getConfigPolicy(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/config/policy`);
  }

  /** Lists all discovered provider factories and capabilities. */
  listAvailableProviders(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/config/providers/available`);
  }

  // ─── Observability (Sprint 1 — F-001, F-003) ───

  tracedRecall(query: string, topK = 5): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/observability/traced-recall`, { query, topK });
  }

  memoryStats(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/observability/stats`);
  }

  timeline(from?: string, to?: string, limit = 50): Observable<any> {
    const params: any = { limit };
    if (from) params.from = from;
    if (to) params.to = to;
    return this.http.get<any>(`${this.baseUrl}/observability/timeline`, { params });
  }

  temporalRecall(query: string, at: string, topK = 10, detectSupersession = true): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/observability/recall/temporal`, {
      query, at, topK, detectSupersession,
    });
  }

  ageDistribution(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/observability/age-distribution`);
  }

  // ─── Portability (Sprint 1 — F-002) ───

  exportMemories(format = 'json'): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/portability/export`, { format });
  }

  importMemories(data: any, conflictResolution = 'skip'): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/portability/import`, { ...data, conflictResolution });
  }

  exportFormats(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/portability/formats`);
  }

  // ─── Governance (Sprint 2 — F-004) ───

  queryAudit(from?: string, to?: string, limit = 100): Observable<any> {
    const params: any = { limit };
    if (from) params.from = from;
    if (to) params.to = to;
    return this.http.get<any>(`${this.baseUrl}/governance/audit`, { params });
  }

  getRetention(namespace = 'default'): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/governance/retention`, { params: { namespace } });
  }

  setRetention(namespace: string, retentionDays: number): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/governance/retention`, { namespace, retentionDays });
  }

  entityErasure(entity: string): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/governance/erasure`, { params: { entity } });
  }

  complianceSummary(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/governance/compliance/summary`);
  }

  listRetentionPolicies(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/governance/retention/all`);
  }

  enforceRetention(): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/governance/retention/enforce`, {});
  }

  erasureByTopic(topic: string): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/governance/erasure/topic`, { params: { topic } });
  }

  erasurePreview(searchTerm: string, type: 'entity' | 'topic' = 'entity'): Observable<any> {
    const params: any = {};
    params[type] = searchTerm;
    return this.http.get<any>(`${this.baseUrl}/governance/erasure/preview`, { params });
  }

  erasureHistory(limit = 20): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/governance/erasure/history`, { params: { limit } });
  }

  // ─── Agent Soul Management ───

  /** Get the current agent soul (identity). */
  getAgentSoul(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/agent/soul`);
  }

  /** Full replace of mutable soul fields. */
  updateAgentSoul(soul: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/agent/soul`, soul);
  }

  /** Partial update of individual soul fields. */
  patchAgentSoul(field: string, value: string, action: string = 'add'): Observable<any> {
    return this.http.patch<any>(`${this.baseUrl}/agent/soul`, { field, value, action });
  }

  /** Reset the agent soul to blank. */
  resetAgentSoul(): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/agent/soul`);
  }

  /** Get all available tools registered in Synapse. */
  getAvailableTools(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/agent/tools`);
  }

  /** List available Ollama models. */
  listOllamaModels(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/system/ollama-models`);
  }

  // ─── Benchmarks (Sprint 3 — F-008) ───

  listBenchmarkSuites(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/benchmarks/suites`);
  }

  runBenchmark(suiteId: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/benchmarks/run/${suiteId}`, {});
  }

  benchmarkResults(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/benchmarks/results`);
  }

  // ─── Namespaces & Grants (ADR-0029 §8.1, Issue #1067) ───

  listNamespaces(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/namespaces`);
  }

  getNamespace(slugOrId: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/namespaces/${encodeURIComponent(slugOrId)}`);
  }

  createNamespace(request: {
    slug: string;
    type?: string;
    displayName?: string;
    description?: string;
  }): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/namespaces`, request);
  }

  introspectAccount(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/account/introspect`);
  }

  setDefaultNamespace(namespace: string): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/account/default-namespace`, { namespace });
  }

  listNamespaceGrants(slugOrId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/namespaces/${encodeURIComponent(slugOrId)}/grants`);
  }

  createNamespaceGrant(
    slugOrId: string,
    request: { granteeAccountId: string; role: string; expiresAt?: string; constraints?: any },
  ): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/namespaces/${encodeURIComponent(slugOrId)}/grants`, request);
  }

  revokeNamespaceGrant(slugOrId: string, grantId: string): Observable<void> {
    return this.http.delete<void>(
      `${this.baseUrl}/namespaces/${encodeURIComponent(slugOrId)}/grants/${encodeURIComponent(grantId)}`,
    );
  }

  listNamespaceAuditEvents(slugOrId: string, limit = 50): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/namespaces/${encodeURIComponent(slugOrId)}/audit`, {
      params: { limit: String(limit) },
    });
  }

  // ─── Admin Tenant Lifecycle (ADR-0087, Issue #1055) ───

  listAdminTenants(filters: {
    status?: string;
    includeDeleted?: boolean;
  } = {}): Observable<any[]> {
    const params: Record<string, string> = {};
    if (filters.status) params['status'] = filters.status;
    if (filters.includeDeleted !== undefined) params['includeDeleted'] = String(filters.includeDeleted);
    return this.http.get<any[]>(`${this.baseUrl}/admin/tenants`, { params });
  }

  getAdminTenant(tenantId: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/admin/tenants/${tenantId}`);
  }

  createAdminTenant(request: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/tenants`, request);
  }

  updateAdminTenant(tenantId: string, request: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/admin/tenants/${tenantId}`, request);
  }

  deleteAdminTenant(tenantId: string): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/admin/tenants/${tenantId}`);
  }

  listAdminTenantAuditLogs(tenantId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/admin/tenants/${tenantId}/audit`);
  }

  // ─── Admin Account & Agent-Principal Registry (ADR-0087, Issue #1056) ───

  listAdminAccounts(filters: {
    tenantId?: string;
    kind?: string;
    profile?: string;
    active?: boolean;
    ownerAccountId?: string;
  } = {}): Observable<any[]> {
    const params: Record<string, string> = {};
    if (filters.tenantId) params['tenantId'] = filters.tenantId;
    if (filters.kind) params['kind'] = filters.kind;
    if (filters.profile) params['profile'] = filters.profile;
    if (filters.active !== undefined) params['active'] = String(filters.active);
    if (filters.ownerAccountId) params['ownerAccountId'] = filters.ownerAccountId;
    return this.http.get<any[]>(`${this.baseUrl}/admin/accounts`, { params });
  }

  getAdminAccount(accountId: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/admin/accounts/${accountId}`);
  }

  createAdminAccount(request: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/accounts`, request);
  }

  updateAdminAccount(accountId: string, request: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/admin/accounts/${accountId}`, request);
  }

  reassignAccountTenant(accountId: string, tenantId: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/accounts/${accountId}/tenant`, { tenantId });
  }

  resetAccountCredentials(accountId: string, request: {
    newPassword?: string;
    mustChangePassword?: boolean;
    revokeRefreshTokens?: boolean;
    revokeApiKeys?: boolean;
  } = {}): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/accounts/${accountId}/reset-credentials`, request);
  }

  listOwnedAgents(accountId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/admin/accounts/${accountId}/agents`);
  }

  listAccountAuditLogs(accountId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/admin/accounts/${accountId}/audit`);
  }

  listAdminCredentials(filters: {
    tenantId?: string;
    userId?: string;
  } = {}): Observable<any[]> {
    const params: Record<string, string> = {};
    if (filters.tenantId) params['tenantId'] = filters.tenantId;
    if (filters.userId) params['userId'] = filters.userId;
    return this.http.get<any[]>(`${this.baseUrl}/credentials`, { params });
  }

  deleteAdminCredential(name: string, tenantId?: string): Observable<void> {
    const params: Record<string, string> = {};
    if (tenantId) params['tenantId'] = tenantId;
    return this.http.delete<void>(`${this.baseUrl}/credentials/${encodeURIComponent(name)}`, { params });
  }

  // ─── Admin Namespace Lifecycle (ADR-0087, Issue #1057) ───

  listAdminNamespaces(filters: {
    tenantId?: string;
    ownerAccountId?: string;
    status?: string;
    type?: string;
    legalHold?: boolean;
    includeTombstoned?: boolean;
  } = {}): Observable<any[]> {
    const params: Record<string, string> = {};
    if (filters.tenantId) params['tenantId'] = filters.tenantId;
    if (filters.ownerAccountId) params['ownerAccountId'] = filters.ownerAccountId;
    if (filters.status) params['status'] = filters.status;
    if (filters.type) params['type'] = filters.type;
    if (filters.legalHold !== undefined) params['legalHold'] = String(filters.legalHold);
    if (filters.includeTombstoned !== undefined) params['includeTombstoned'] = String(filters.includeTombstoned);
    return this.http.get<any[]>(`${this.baseUrl}/admin/namespaces`, { params });
  }

  getAdminNamespace(namespaceId: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/admin/namespaces/${namespaceId}`);
  }

  updateAdminNamespaceQuotas(namespaceId: string, request: {
    maxMemories?: number | null;
    maxStorageBytes?: number | null;
    maxOpsPerMinute?: number | null;
  }): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/admin/namespaces/${namespaceId}/quotas`, request);
  }

  archiveAdminNamespace(namespaceId: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/namespaces/${namespaceId}/archive`, {});
  }

  restoreAdminNamespace(namespaceId: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/namespaces/${namespaceId}/restore`, {});
  }

  tombstoneAdminNamespace(namespaceId: string): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/admin/namespaces/${namespaceId}`);
  }

  setAdminNamespaceLegalHold(namespaceId: string, request: {
    legalHold: boolean;
    reason?: string;
  }): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/namespaces/${namespaceId}/legal-hold`, request);
  }

  transferAdminNamespaceOwnership(namespaceId: string, request: {
    newOwnerAccountId: string;
    previousOwnerGrantRole?: string;
    reason?: string;
  }): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/namespaces/${namespaceId}/transfer-ownership`, request);
  }

  eraseAdminNamespace(namespaceId: string, request: {
    confirmationSlug?: string;
    propagateReplicas?: boolean;
    reason?: string;
    includeSearchIndex?: boolean;
  } = {}): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/namespaces/${namespaceId}/erase`, request);
  }

  listAdminNamespaceGrants(namespaceId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/admin/namespaces/${namespaceId}/grants`);
  }

  revokeAdminNamespaceGrant(namespaceId: string, grantId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/admin/namespaces/${namespaceId}/grants/${grantId}`);
  }

  listAdminNamespaceAuditLogs(namespaceId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/admin/namespaces/${namespaceId}/audit`);
  }

  // ─── Admin Analytics Rollups (ADR-0083 §5.8, ADR-0087, Issue #1058) ───

  getFleetAnalytics(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/admin/analytics/fleet`);
  }

  getCallerTenantAnalytics(tenantId?: string): Observable<any> {
    const params: Record<string, string> = {};
    if (tenantId) params['tenantId'] = tenantId;
    return this.http.get<any>(`${this.baseUrl}/admin/analytics/tenant`, { params });
  }

  getTenantAnalytics(tenantId: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/admin/analytics/tenants/${tenantId}`);
  }

  getAccountAnalytics(accountId: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/admin/analytics/accounts/${accountId}`);
  }

  getNamespaceAnalytics(namespaceId: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/admin/analytics/namespaces/${namespaceId}`);
  }

  getAnalyticsTimeseries(filters: {
    scope?: 'FLEET' | 'TENANT' | 'ACCOUNT' | 'NAMESPACE';
    tenantId?: string;
    accountId?: string;
    namespaceId?: string;
    from?: string;
    to?: string;
    limit?: number;
  } = {}): Observable<any> {
    const params: Record<string, string> = {};
    if (filters.scope) params['scope'] = filters.scope;
    if (filters.tenantId) params['tenantId'] = filters.tenantId;
    if (filters.accountId) params['accountId'] = filters.accountId;
    if (filters.namespaceId) params['namespaceId'] = filters.namespaceId;
    if (filters.from) params['from'] = filters.from;
    if (filters.to) params['to'] = filters.to;
    if (filters.limit !== undefined) params['limit'] = String(filters.limit);
    return this.http.get<any>(`${this.baseUrl}/admin/analytics/timeseries`, { params });
  }

  // ─── Append-Only Audit Log (Issue #1059) ───

  queryAdminAuditEvents(filters: {
    tenantId?: string;
    actorId?: string;
    action?: string;
    targetType?: string;
    targetId?: string;
    namespaceId?: string;
    outcome?: 'SUCCESS' | 'DENIED' | 'FAILURE';
    from?: string;
    to?: string;
    limit?: number;
  } = {}): Observable<any[]> {
    const params: Record<string, string> = {};
    if (filters.tenantId) params['tenantId'] = filters.tenantId;
    if (filters.actorId) params['actorId'] = filters.actorId;
    if (filters.action) params['action'] = filters.action;
    if (filters.targetType) params['targetType'] = filters.targetType;
    if (filters.targetId) params['targetId'] = filters.targetId;
    if (filters.namespaceId) params['namespaceId'] = filters.namespaceId;
    if (filters.outcome) params['outcome'] = filters.outcome;
    if (filters.from) params['from'] = filters.from;
    if (filters.to) params['to'] = filters.to;
    if (filters.limit !== undefined) params['limit'] = String(filters.limit);
    return this.http.get<any[]>(`${this.baseUrl}/admin/audit`, { params });
  }

  verifyAdminAuditChain(tenantId?: string): Observable<any> {
    const params: Record<string, string> = {};
    if (tenantId) params['tenantId'] = tenantId;
    return this.http.get<any>(`${this.baseUrl}/admin/audit/verify`, { params });
  }

  getNamespaceAuditEvents(slugOrId: string, limit = 100): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/namespaces/${slugOrId}/audit`, {
      params: { limit: String(limit) }
    });
  }

  // ─── Break-Glass Namespace Access (Issue #1060, #1066) ───

  requestBreakGlass(payload: {
    namespaceId: string;
    justification: string;
    ticketRef?: string;
    durationMinutes?: number;
  }): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/break-glass`, payload);
  }

  approveBreakGlass(id: string, comment?: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/break-glass/${id}/approve`, comment ? { comment } : {});
  }

  rejectBreakGlass(id: string, reason?: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/break-glass/${id}/reject`, reason ? { reason } : {});
  }

  revokeBreakGlass(id: string, reason?: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/break-glass/${id}/revoke`, reason ? { reason } : {});
  }

  listBreakGlassRequests(filters: {
    tenantId?: string;
    namespaceId?: string;
    status?: 'PENDING_APPROVAL' | 'ACTIVE' | 'EXPIRED' | 'REVOKED' | 'REJECTED';
  } = {}): Observable<any[]> {
    const params: Record<string, string> = {};
    if (filters.tenantId) params['tenantId'] = filters.tenantId;
    if (filters.namespaceId) params['namespaceId'] = filters.namespaceId;
    if (filters.status) params['status'] = filters.status;
    return this.http.get<any[]>(`${this.baseUrl}/admin/break-glass`, { params });
  }

  getBreakGlassRequest(id: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/admin/break-glass/${id}`);
  }

  /**
   * Read-only emergency memory inspection for an active break-glass session.
   */
  inspectBreakGlassNamespaceMemories(namespaceId: string, limit = 25): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/memory/table`, {
      params: { limit: String(limit) },
      headers: { 'X-Spector-Namespace': namespaceId },
    });
  }

  // ─── Infrastructure, Tasks & Caches (Issue #1066) ───

  getSystemStatus(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/system/status`);
  }

  getSystemConfig(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/system/config`);
  }

  listTasks(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/tasks`);
  }

  triggerTask(id: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/tasks/${id}/trigger`, {});
  }

  pauseTask(id: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/tasks/${id}/pause`, {});
  }

  resumeTask(id: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/tasks/${id}/resume`, {});
  }

  getRecentTaskAuditHistory(limit = 50): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/tasks/audit`, {
      params: { limit: String(limit) },
    });
  }

  listAdminCaches(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/admin/cache`);
  }

  clearAdminCache(cacheName: string): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/admin/cache/${cacheName}`);
  }

  clearAllAdminCaches(): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/admin/cache`);
  }
}



