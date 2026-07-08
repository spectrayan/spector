// ═══════════════════════════════════════════════════════════════════════
// Spector Cortex — Environment Configuration (Production)
// ═══════════════════════════════════════════════════════════════════════

export const environment = {
  production: true,

  /** Backend API base URL */
  apiUrl: '/api/v1',

  /** API version prefix */
  apiVersion: 'v1',

  /** SSE via nginx reverse proxy (has proper buffering disabled) */
  sseBaseUrl: '/api/v1',

  /** Logging configuration */
  logging: {
    /** Master switch — disables all logging when false */
    enabled: true,
    /** Minimum log level: 'debug' | 'info' | 'warn' | 'error' | 'off' */
    level: 'warn' as 'debug' | 'info' | 'warn' | 'error' | 'off',
  },
};
