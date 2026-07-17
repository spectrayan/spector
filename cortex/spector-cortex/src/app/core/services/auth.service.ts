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

/** Login response from POST /api/v1/auth/login */
export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;        // TSID (opaque identifier)
  username: string;      // login handle (display)
  roles: string[];
  tenantId: string;      // TSID (opaque identifier)
  mustChangePassword: boolean;
}

/** Token refresh response from POST /api/v1/auth/refresh */
export interface RefreshResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
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
  readonly isAuthenticated = computed(() => {
    const t = this.token();
    if (!t) return false;
    const payload = this.decodeToken(t);
    if (!payload) return false;
    return payload.exp * 1000 > Date.now();
  });

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

  /** Whether the current user has the 'admin' role. */
  readonly isAdmin = computed(() =>
    this.currentUser()?.roles?.includes('admin') ?? false
  );

  /** Check if the current user has a specific role. */
  hasRole(role: string): boolean {
    return this.currentUser()?.roles?.includes(role) ?? false;
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

    if (isPlatformBrowser(this.platformId)) {
      // Restore token from localStorage on startup
      const storedAccess = localStorage.getItem(ACCESS_TOKEN_KEY);
      if (storedAccess) {
        const payload = this.decodeToken(storedAccess);
        if (payload && payload.exp * 1000 > Date.now()) {
          this.token.set(storedAccess);
          this.mustChangePassword.set(payload.mcp === true);
          this.scheduleRefresh(storedAccess);
          this.resolveReady();
          return;
        } else {
          // Access token expired — try to refresh
          const storedRefresh = localStorage.getItem(REFRESH_TOKEN_KEY);
          if (storedRefresh) {
            this.refreshToken(storedRefresh).then(() => {
              this.resolveReady();
            }).catch(() => {
              this.clearTokens();
              this.resolveReady();
            });
            return;
          }
          this.clearTokens();
        }
      }

      // No valid token — resolve ready (will redirect to login via guard)
      this.resolveReady();
    } else {
      // SSR — resolve immediately
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

    this.token.set(response.accessToken);
    this.mustChangePassword.set(response.mustChangePassword);

    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem(ACCESS_TOKEN_KEY, response.accessToken);
      localStorage.setItem(REFRESH_TOKEN_KEY, response.refreshToken);
    }

    this.scheduleRefresh(response.accessToken);
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

    this.token.set(response.accessToken);
    this.mustChangePassword.set(false);

    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem(ACCESS_TOKEN_KEY, response.accessToken);
    }

    this.scheduleRefresh(response.accessToken);
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
          refreshToken: refreshTk,
        })
      );

      this.token.set(response.accessToken);

      if (isPlatformBrowser(this.platformId)) {
        localStorage.setItem(ACCESS_TOKEN_KEY, response.accessToken);
        localStorage.setItem(REFRESH_TOKEN_KEY, response.refreshToken);
      }

      this.scheduleRefresh(response.accessToken);
      this.log.info('AuthService', 'Token refreshed successfully');
    } catch (err) {
      this.log.warn('AuthService', 'Token refresh failed — logging out');
      this.handleUnauthorized();
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
          this.http.post(`${AUTH_API}/logout`, { refreshToken: refreshTk })
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
