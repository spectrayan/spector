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

// ═══════════════════════════════════════════════════════════════════════
// Spector Cortex — API Key Management Service (Universal Credentials Vault)
// ═══════════════════════════════════════════════════════════════════════
// Manages user-scoped inbound API keys backed by `/api/v1/credentials`
// (`CredentialCategory.AUTH`, `CredentialType.API_KEY`, Issue #1050 & #1067).

import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
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
  keyPrefix: string;     // display prefix (e.g. "sk_spec_****6789")
  scopes: string[];
  createdAt: string;
  expiresAt: string | null;
}

/** API key info returned from listing (raw secret is never shown). */
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
 * Service for managing user-scoped API keys via the Universal Credentials Vault (`/api/v1/credentials`).
 *
 * All endpoints require authentication (JWT or existing API key).
 * Regular users can only list, create, and revoke their own credentials (IDOR-hardened in #1050).
 */
@Injectable({ providedIn: 'root' })
export class ApiKeyService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/credentials`;

  /** Creates a new AUTH API_KEY credential. Returns the full secret once. */
  createApiKey(request: CreateApiKeyRequest): Observable<ApiKeyCreatedResponse> {
    const expiresAt =
      request.expiresInDays && request.expiresInDays > 0
        ? new Date(Date.now() + request.expiresInDays * 86400 * 1000).toISOString()
        : null;

    const payload = {
      name: request.name,
      category: 'AUTH',
      provider: 'spector',
      credentialType: 'API_KEY',
      properties: { scopes: request.scopes },
      expiresAt,
      description: 'User workspace API key',
    };

    return this.http.post<any>(this.baseUrl, payload).pipe(
      map((res) => ({
        id: res?.name || res?.id || res?.credentialId || request.name,
        name: res?.name || request.name,
        key: res?.rawSecret || res?.key || '',
        keyPrefix: res?.maskedPreview || res?.keyPrefix || 'sk_spec_****',
        scopes: Array.isArray(res?.properties?.['scopes'])
          ? res.properties['scopes']
          : Array.isArray(res?.scopes)
            ? res.scopes
            : request.scopes,
        createdAt: res?.createdAt || new Date().toISOString(),
        expiresAt: res?.expiresAt ?? expiresAt,
      })),
    );
  }

  /** Lists all active API keys for the current user (masked metadata only). */
  listApiKeys(): Observable<ApiKeyInfo[]> {
    return this.http.get<any[]>(this.baseUrl).pipe(
      map((items) => {
        const list = Array.isArray(items) ? items : [];
        return list
          .filter(
            (item) =>
              !item.category ||
              item.category === 'AUTH' ||
              item.keyPrefix !== undefined,
          )
          .map((item) => {
            const expiresAt: string | null = item.expiresAt ?? null;
            const expired =
              item.expired !== undefined
                ? Boolean(item.expired)
                : Boolean(expiresAt && new Date(expiresAt).getTime() < Date.now());
            return {
              id: item.name || item.id || item.credentialId,
              name: item.name,
              keyPrefix: item.maskedPreview || item.keyPrefix || 'sk_spec_****',
              scopes: Array.isArray(item.properties?.['scopes'])
                ? item.properties['scopes']
                : Array.isArray(item.scopes)
                  ? item.scopes
                  : [],
              createdAt: item.createdAt,
              expiresAt,
              lastUsedAt: item.lastUsedAt ?? null,
              expired,
            };
          });
      }),
    );
  }

  /** Revokes an API key by credential name/ID. */
  revokeApiKey(keyId: string): Observable<{ status: string; id: string }> {
    return this.http
      .delete<any>(`${this.baseUrl}/${encodeURIComponent(keyId)}`)
      .pipe(map((res) => ({ status: res?.status || 'revoked', id: keyId })));
  }
}
