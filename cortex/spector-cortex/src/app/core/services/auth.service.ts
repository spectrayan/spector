// ═══════════════════════════════════════════════════════════════════════
// Spector Cortex — Authentication Service
// ═══════════════════════════════════════════════════════════════════════
// Manages JWT-based authentication with access + refresh token rotation.
// Stores tokens in localStorage, provides reactive signals for auth state.
// All mock/dev session code has been removed — auth is always enforced.

import { Injectable, inject, signal, computed, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { LoggerService } from './logger.service';
import { environment } from '../../../environments/environment';

/** Auth configuration from GET /api/v1/auth/config */
export interface AuthConfig {
  authEnabled: boolean;
  auth_enabled?: boolean;
  oidcEnabled: boolean;
  oidc_enabled?: boolean;
  oidcIssuerUri?: string | null;
  oidc_issuer_uri?: string | null;
}

/** Login response from POST /api/v1/auth/login */
export interface LoginResponse {
  accessToken?: string;
  access_token?: string;
  refreshToken?: string;
  refresh_token?: string;
  tokenType?: string;
  token_type?: string;
  expiresIn?: number;
  expires_in?: number;
  userId?: string;        // TSID (opaque identifier)
  username?: string;      // login handle (display)
  roles?: string[];
  tenantId?: string;      // TSID (opaque identifier)
  mustChangePassword?: boolean;
}

/** Token refresh response from POST /api/v1/auth/refresh */
export interface RefreshResponse {
  accessToken?: string;
  access_token?: string;
  refreshToken?: string;
  refresh_token?: string;
  tokenType?: string;
  token_type?: string;
  expiresIn?: number;
  expires_in?: number;
}

/** Change password response from POST /api/v1/auth/change-password */
export interface ChangePasswordResponse {
  status: string;
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;        // TSID
  username: string;
}

/** Register response from POST /api/v1/auth/register */
export interface RegisterResponse {
  status: string;
  username: string;
  tenantId: string;
  roles: string;
}

/** Decoded JWT payload */
export interface JwtPayload {
  sub: string;          // user TSID (routing, isolation)
  tenant_id: string;    // tenant TSID
  username: string;     // login handle (display only)
  scope: string;
  roles: string;
  jti: string;
  iat: number;
  exp: number;
  mcp: boolean; // must change password
}

const ACCESS_TOKEN_KEY = 'spector_access_token';
const REFRESH_TOKEN_KEY = 'spector_refresh_token';
const AUTH_API = `${environment.apiUrl}/auth`;

/** Buffer before actual expiry to trigger refresh (60 seconds). */
const REFRESH_BUFFER_MS = 60_000;

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly log = inject(LoggerService);

  // ── Reactive state ──
  readonly token = signal<string | null>(null);
  readonly authConfig = signal<AuthConfig | null>(null);

  readonly isAuthenticated = computed(() => {
    const t = this.token();
    if (!t) return false;
    const payload = this.decodeToken(t);
    if (!payload) return false;
    return payload.exp * 1000 > Date.now();
  });

  /** Whether authentication is enabled on the backend. */
  isAuthEnabled(): boolean {
    const cfg = this.authConfig();
    if (!cfg) return true;
    return cfg.authEnabled ?? cfg.auth_enabled ?? true;
  }

  readonly currentUser = computed(() => {
    const t = this.token();
    if (!t) return null;
    const payload = this.decodeToken(t);
    if (!payload) return null;
    return {
      userId: payload.sub,             // TSID (routing)
      username: payload.username,       // login handle (display)
      tenantId: payload.tenant_id,     // TSID
      roles: payload.roles?.split(',') ?? [],
      scopes: payload.scope?.split(' ') ?? [],
    };
  });

  /** Whether the current user has the 'admin' or 'super-admin' role. */
  readonly isAdmin = computed(() => {
    const roles = this.currentUser()?.roles ?? [];
    return roles.some(r => {
      const norm = r.toLowerCase().replace(/_/g, '-');
      return norm === 'admin' || norm === 'super-admin';
    });
  });

  /** Check if the current user has a specific role (case-insensitive, normalized). */
  hasRole(role: string): boolean {
    const roles = this.currentUser()?.roles ?? [];
    const target = role.toLowerCase().replace(/_/g, '-');
    return roles.some(r => {
      const norm = r.toLowerCase().replace(/_/g, '-');
      return norm === target || (target === 'admin' && norm === 'super-admin');
    });
  }

  readonly mustChangePassword = signal(false);

  /**
   * Promise that resolves once auth initialization is complete.
   * The auth guard awaits this before checking isAuthenticated().
   */
  readonly whenReady: Promise<void>;
  private resolveReady!: () => void;

  /** Timer for auto-refresh before expiry. */
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;

  /** Flag to prevent concurrent refresh attempts. */
  private refreshInProgress = false;

  constructor() {
    this.whenReady = new Promise<void>((resolve) => {
      this.resolveReady = resolve;
    });

    this.initialize();
  }

  /**
   * Loads public authentication configuration and restores existing sessions.
   */
  async loadAuthConfig(): Promise<AuthConfig> {
    try {
      const config = await firstValueFrom(
        this.http.get<AuthConfig>(`${AUTH_API}/config`)
      );
      const normalized: AuthConfig = {
        authEnabled: config.authEnabled ?? config.auth_enabled ?? true,
        oidcEnabled: config.oidcEnabled ?? config.oidc_enabled ?? false,
        oidcIssuerUri: config.oidcIssuerUri ?? config.oidc_issuer_uri ?? null,
      };
      this.authConfig.set(normalized);
      return normalized;
    } catch {
      this.log.debug('AuthService', 'Using default auth configuration');
      const fallback: AuthConfig = {
        authEnabled: true,
        oidcEnabled: false,
        oidcIssuerUri: null,
      };
      this.authConfig.set(fallback);
      return fallback;
    }
  }

  private async initialize(): Promise<void> {
    if (!isPlatformBrowser(this.platformId)) {
      this.resolveReady();
      return;
    }

    try {
      await this.loadAuthConfig();

      // Restore token from localStorage on startup
      const storedAccess = localStorage.getItem(ACCESS_TOKEN_KEY);
      if (storedAccess) {
        const payload = this.decodeToken(storedAccess);
        if (payload && payload.exp * 1000 > Date.now()) {
          this.token.set(storedAccess);
          this.mustChangePassword.set(payload.mcp === true);
          this.scheduleRefresh(storedAccess);
          return;
        } else {
          // Access token expired — try to refresh
          const storedRefresh = localStorage.getItem(REFRESH_TOKEN_KEY);
          if (storedRefresh) {
            try {
              await this.refreshToken(storedRefresh);
              return;
            } catch {
              this.clearTokens();
            }
          } else {
            this.clearTokens();
          }
        }
      }
    } finally {
      this.resolveReady();
    }
  }

  /**
   * Login with username and password.
   * Returns the login response on success, throws on failure.
   */
  async login(username: string, password: string): Promise<LoginResponse> {
    const response = await firstValueFrom(
      this.http.post<LoginResponse>(`${AUTH_API}/login`, { username, password })
    );

    const accessToken = response.accessToken || response.access_token || '';
    const refreshToken = response.refreshToken || response.refresh_token || '';

    this.token.set(accessToken);
    this.mustChangePassword.set(response.mustChangePassword === true);

    if (isPlatformBrowser(this.platformId)) {
      if (accessToken) localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
      if (refreshToken) localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    }

    if (accessToken) {
      this.scheduleRefresh(accessToken);
    }
    return response;
  }

  /**
   * Register a new user account.
   */
  async register(username: string, password: string, email?: string,
                  displayName?: string): Promise<RegisterResponse> {
    return firstValueFrom(
      this.http.post<RegisterResponse>(`${AUTH_API}/register`, {
        username, password, email, displayName,
      })
    );
  }

  /**
   * Request a password reset token.
   */
  async forgotPassword(username: string): Promise<any> {
    return firstValueFrom(
      this.http.post(`${AUTH_API}/forgot-password`, { username })
    );
  }

  /**
   * Reset password using a reset token.
   */
  async resetPassword(token: string, username: string, newPassword: string): Promise<any> {
    return firstValueFrom(
      this.http.post(`${AUTH_API}/reset-password`, { token, username, newPassword })
    );
  }

  /**
   * Change password for the current user.
   * Returns the new token on success.
   */
  async changePassword(oldPassword: string, newPassword: string): Promise<ChangePasswordResponse> {
    const response = await firstValueFrom(
      this.http.post<ChangePasswordResponse>(`${AUTH_API}/change-password`, {
        oldPassword,
        newPassword,
      })
    );

    const accessToken = response.accessToken || (response as any).access_token || '';
    this.token.set(accessToken);
    this.mustChangePassword.set(false);

    if (isPlatformBrowser(this.platformId) && accessToken) {
      localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    }

    if (accessToken) {
      this.scheduleRefresh(accessToken);
    }
    return response;
  }

  /**
   * Refresh the access token using the stored refresh token.
   * Implements token rotation — old refresh token is revoked, new one issued.
   */
  async refreshToken(refreshTokenOverride?: string): Promise<void> {
    if (this.refreshInProgress) return;
    this.refreshInProgress = true;

    try {
      const refreshTk = refreshTokenOverride ??
        (isPlatformBrowser(this.platformId) ? localStorage.getItem(REFRESH_TOKEN_KEY) : null);

      if (!refreshTk) {
        this.log.warn('AuthService', 'No refresh token available');
        this.handleUnauthorized();
        return;
      }

      const response = await firstValueFrom(
        this.http.post<RefreshResponse>(`${AUTH_API}/refresh`, {
          refresh_token: refreshTk,
        })
      );

      const accessToken = response.accessToken || response.access_token || '';
      const newRefreshToken = response.refreshToken || response.refresh_token;

      this.token.set(accessToken);

      if (isPlatformBrowser(this.platformId)) {
        if (accessToken) localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
        if (newRefreshToken) localStorage.setItem(REFRESH_TOKEN_KEY, newRefreshToken);
      }

      if (accessToken) {
        this.scheduleRefresh(accessToken);
      }
      this.log.info('AuthService', 'Token refreshed successfully');
    } catch (err) {
      this.log.warn('AuthService', 'Token refresh failed — logging out');
      this.handleUnauthorized();
      throw err;
    } finally {
      this.refreshInProgress = false;
    }
  }

  /**
   * Logout — revoke refresh token server-side and clear local state.
   */
  async logout(): Promise<void> {
    const refreshTk = isPlatformBrowser(this.platformId)
      ? localStorage.getItem(REFRESH_TOKEN_KEY) : null;

    // Best-effort server-side logout (don't block on failure)
    if (refreshTk) {
      try {
        await firstValueFrom(
          this.http.post(`${AUTH_API}/logout`, { refresh_token: refreshTk })
        );
      } catch {
        // Ignore — we're logging out anyway
      }
    }

    this.clearTokens();
    this.router.navigate(['/login']);
  }

  /**
   * Handle 401 response — called by the auth interceptor.
   */
  handleUnauthorized(): void {
    this.clearTokens();
    if (!this.router.url.startsWith('/login') &&
        !this.router.url.startsWith('/register') &&
        !this.router.url.startsWith('/forgot-password') &&
        !this.router.url.startsWith('/reset-password')) {
      this.router.navigate(['/login']);
    }
  }

  /**
   * Returns the current access token for use in HTTP headers.
   */
  getAccessToken(): string | null {
    return this.token();
  }

  // ── Internal ──

  /**
   * Schedules an automatic token refresh before the access token expires.
   */
  private scheduleRefresh(accessToken: string): void {
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
    }

    const payload = this.decodeToken(accessToken);
    if (!payload) return;

    const expiresAtMs = payload.exp * 1000;
    const refreshAtMs = expiresAtMs - REFRESH_BUFFER_MS;
    const delayMs = refreshAtMs - Date.now();

    if (delayMs > 0) {
      this.refreshTimer = setTimeout(() => {
        this.log.info('AuthService', 'Auto-refreshing token before expiry');
        this.refreshToken();
      }, delayMs);
    }
  }

  private clearTokens(): void {
    this.token.set(null);
    this.mustChangePassword.set(false);
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
    }
    if (isPlatformBrowser(this.platformId)) {
      localStorage.removeItem(ACCESS_TOKEN_KEY);
      localStorage.removeItem(REFRESH_TOKEN_KEY);
    }
  }

  private decodeToken(token: string): JwtPayload | null {
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return null;
      const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
      return payload as JwtPayload;
    } catch {
      return null;
    }
  }
}
