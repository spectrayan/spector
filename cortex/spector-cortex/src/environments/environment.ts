// ═══════════════════════════════════════════════════════════════════════
// Spector Cortex — Environment Configuration (Development)
// ═══════════════════════════════════════════════════════════════════════

export const environment = {
  production: false,

  /** Backend API base URL */
  apiUrl: 'http://192.168.1.71:7070/api/v1',

  /** API version prefix */
  apiVersion: 'v1',

  /** SSE event stream connects directly to backend (dev proxy buffers SSE) */
  sseBaseUrl: 'http://192.168.1.71:7070/api/v1',

  /** Logging configuration */
  logging: {
    /** Master switch — disables all logging when false */
    enabled: true,
    /** Minimum log level: 'debug' | 'info' | 'warn' | 'error' | 'off' */
    level: 'debug' as 'debug' | 'info' | 'warn' | 'error' | 'off',
  },
};
