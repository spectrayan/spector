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

  // ─── Multi-Agent Workspaces (OPP-006) ───

  registerAgent(name: string, description = '', scopes: string[] = ['*']): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/agent-workspaces/agents`, { name, description, scopes });
  }

  listAgents(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/agent-workspaces/agents`);
  }

  createWorkspace(name: string, description = '', conflictPolicy = 'APPEND'): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/agent-workspaces/workspaces`, { name, description, conflictPolicy });
  }

  listWorkspaces(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/agent-workspaces/workspaces`);
  }

  addWorkspaceMember(workspaceId: string, agentId: string, role = 'READ_WRITE'): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/agent-workspaces/workspaces/${workspaceId}/members`, { agentId, role });
  }

  workspaceActivity(workspaceId: string, limit = 50): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/agent-workspaces/workspaces/${workspaceId}/activity`, { params: { limit } });
  }

  workspaceActivityFeed(workspaceId: string, limit = 20): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/agent-workspaces/workspaces/${workspaceId}/activity/feed`, { params: { limit } });
  }

  workspaceRemember(workspaceId: string, agentId: string, text: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/agent-workspaces/workspaces/${workspaceId}/remember`, { agentId, text });
  }

  workspaceRecall(workspaceId: string, agentId: string, query: string, topK = 5): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/agent-workspaces/workspaces/${workspaceId}/recall`, { agentId, query, topK });
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

  // ─── Chat (formerly Cognitive RAG) ───

  chatQuery(query: string, options: {
    maxMemories?: number;
    generateResponse?: boolean;
    conversationId?: string;
    memoryTier?: string;
    model?: string;
    fileContent?: string;
    systemPrompt?: string;
    scoringBias?: { relevance: number; recency: number; importance: number };
  } = {}): Observable<any> {
    const body: any = {
      query,
      maxMemories: options.maxMemories ?? 5,
      generateResponse: options.generateResponse ?? true,
    };
    if (options.conversationId) body.conversationId = options.conversationId;
    if (options.memoryTier) body.memoryTier = options.memoryTier;
    if (options.model) body.model = options.model;
    if (options.fileContent) body.fileContent = options.fileContent;
    if (options.systemPrompt) body.systemPrompt = options.systemPrompt;
    if (options.scoringBias) body.bias = options.scoringBias;
    return this.http.post<any>(`${this.baseUrl}/chat/query`, body);
  }

  chatConfig(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/chat/config`);
  }

  chatModels(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/chat/models`);
  }

  createConversation(title?: string, model?: string, systemPrompt?: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/chat/conversations`, { title, model, systemPrompt });
  }

  listConversations(limit = 50): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/chat/conversations`, { params: { limit } });
  }

  loadConversation(conversationId: string, page = 0, pageSize = 50): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/chat/conversations/${conversationId}`, {
      params: { page, pageSize }
    });
  }

  deleteConversation(conversationId: string): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/chat/conversations/${conversationId}`);
  }

  // ─── Agent Chat ───

  /**
   * Send an agent chat message with cognitive memory priming.
   * The backend auto-manages sessions — no conversation management needed.
   * System prompt is managed server-side (companion-system.txt + soul identity).
   * Returns JSON with response, trace (tool steps), primedMemories count, and metadata.
   */
  agentChat(message: string, options: {
    sessionId?: string;
    model?: string;
    contextDepth?: number;
    enableGraph?: boolean;
    enableTextSearch?: boolean;
    enableTrace?: boolean;
  } = {}): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/chat/agent`, {
      message,
      ...options,
    });
  }

  /** List available agent tools. */
  agentTools(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/chat/tools`);
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

  /** List recent chat sessions (for "Load More" past messages). */
  chatSessions(limit: number = 10): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/chat/sessions`, { params: { limit } });
  }

  /** Load all messages for a specific past session. */
  chatSessionMessages(sessionId: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/chat/sessions/${sessionId}/messages`);
  }

  /** List available Ollama models. */
  listOllamaModels(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/system/ollama-models`);
  }


  // (Sprint 3 agent/workspace stubs superseded by OPP-006 section above)


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
}
