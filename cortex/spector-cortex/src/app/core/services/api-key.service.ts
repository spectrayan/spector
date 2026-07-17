// ═══════════════════════════════════════════════════════════════════════
// Spector Cortex — API Key Management Service
// ═══════════════════════════════════════════════════════════════════════
// Angular service for GitHub/Bitbucket-style API key lifecycle:
// create, list, revoke. Used by the Settings > API Keys tab.

import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Request to create a new API key. */
export interface CreateApiKeyRequest {
  name: string;
  expiresInDays: number | null; // null or 0 = never expires
  scopes: string[];
}

/** Response from creating an API key — includes the full key (shown once). */
export interface ApiKeyCreatedResponse {
  id: string;
  name: string;
  key: string;           // full key — shown once, never again
  keyPrefix: string;     // display prefix (e.g. "spk_abc1...6789")
  scopes: string[];
  createdAt: string;
  expiresAt: string | null;
}

/** API key info returned from listing (key is never shown). */
export interface ApiKeyInfo {
  id: string;
  name: string;
  keyPrefix: string;
  scopes: string[];
  createdAt: string;
  expiresAt: string | null;
  lastUsedAt: string | null;
  expired: boolean;
}

/**
 * Service for managing API keys via the REST API.
 *
 * All endpoints require authentication (JWT or existing API key).
 * Users can only manage their own keys.
 */
@Injectable({ providedIn: 'root' })
export class ApiKeyService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/auth/api-keys`;

  /** Creates a new API key. Returns the full key (shown once). */
  createApiKey(request: CreateApiKeyRequest): Observable<ApiKeyCreatedResponse> {
    return this.http.post<ApiKeyCreatedResponse>(this.baseUrl, request);
  }

  /** Lists all active API keys for the current user (masked). */
  listApiKeys(): Observable<ApiKeyInfo[]> {
    return this.http.get<ApiKeyInfo[]>(this.baseUrl);
  }

  /** Revokes an API key by ID. */
  revokeApiKey(keyId: string): Observable<{ status: string; id: string }> {
    return this.http.delete<{ status: string; id: string }>(`${this.baseUrl}/${keyId}`);
  }
}
