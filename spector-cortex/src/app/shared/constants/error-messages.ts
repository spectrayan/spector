// ═══════════════════════════════════════════════════════════════════════
// Spector Cortex — Error Message Constants
// ═══════════════════════════════════════════════════════════════════════
// Centralized, user-friendly error messages with token substitution.
// Uses {token} placeholders instead of string concatenation.
//
// Usage:
//   import { ERROR_MESSAGES, formatMessage } from '../shared/constants/error-messages';
//   const msg = formatMessage(ERROR_MESSAGES.MEMORY.REINFORCE_FAILED, { id: 'abc123' });

/**
 * Replaces `{token}` placeholders in a template string with provided values.
 *
 * @example
 * formatMessage('Failed to reinforce memory "{id}"', { id: 'abc123' })
 * // → 'Failed to reinforce memory "abc123"'
 */
export function formatMessage(template: string, params?: Record<string, string | number>): string {
  if (!params) return template;
  return Object.entries(params).reduce(
    (msg, [key, value]) => msg.replace(new RegExp(`\\{${key}\\}`, 'g'), String(value)),
    template,
  );
}

export const ERROR_MESSAGES = {
  // ── Memory Operations ──
  MEMORY: {
    REINFORCE_SUCCESS: 'Memory "{id}" reinforced successfully',
    REINFORCE_FAILED: 'Unable to reinforce memory. Please try again.',
    SUPPRESS_SUCCESS: 'Memory "{id}" suppressed',
    SUPPRESS_FAILED: 'Unable to suppress memory. Please try again.',
    RESOLVE_SUCCESS: '{action} memory "{id}"',
    RESOLVE_FAILED: 'Unable to toggle resolve state. Please try again.',
    FORGET_SUCCESS: 'Memory "{id}" forgotten',
    FORGET_FAILED: 'Unable to forget memory. Please try again.',
    REFLECT_SUCCESS: 'Sleep consolidation cycle completed',
    REFLECT_FAILED: 'Consolidation cycle failed. Please try again.',
    BULK_REINFORCE_SUCCESS: 'Reinforced {count} memories',
    BULK_REINFORCE_PARTIAL: 'Bulk reinforce completed with some issues',
    BULK_SUPPRESS_SUCCESS: 'Suppressed {count} memories',
    BULK_SUPPRESS_PARTIAL: 'Bulk suppression completed with some issues',
    BULK_FORGET_SUCCESS: 'Forgot {count} memories',
    BULK_FORGET_PARTIAL: 'Bulk forget completed with some issues',
    RECONSOLIDATE_SUCCESS: 'Memory reconsolidated — new embedding computed',
    RECONSOLIDATE_FAILED: 'Reconsolidation failed. Please try again.',
    FORK_SUCCESS: 'Forked memory created: {id}',
    FORK_FAILED: 'Fork operation failed. Please try again.',
    ADD_SUCCESS: 'Memory stored successfully ({chunks} chunks)',
    ADD_FAILED: 'Failed to store memory. Please try again.',
    INGEST_SUBMITTED: 'Ingestion submitted — check notifications for progress',
  },

  // ── Settings / Profile ──
  SETTINGS: {
    SAVE_SUCCESS: 'Salience profile saved successfully',
    SAVE_FAILED: 'Unable to save profile. Please try again.',
    RESCORE_SUCCESS: 'Background re-scoring started — existing memories will be updated',
    RESCORE_FAILED: 'Re-score operation failed. Please try again.',
    RESET_SUCCESS: 'Profile reset to defaults',
  },

  // ── Connectors ──
  CONNECTOR: {
    START_SUCCESS: 'Connector started',
    START_FAILED: 'Unable to start connector. Please try again.',
    STOP_SUCCESS: 'Connector stopped',
    STOP_FAILED: 'Unable to stop connector. Please try again.',
    RELOAD_SUCCESS: 'Connector reloaded',
    RELOAD_FAILED: 'Unable to reload connector. Please try again.',
    REMOVE_SUCCESS: 'Connector removed',
    REMOVE_FAILED: 'Unable to remove connector. Please try again.',
    CREATE_SUCCESS: 'Connector created successfully',
    CREATE_FAILED: 'Unable to create connector. Please try again.',
    TEST_SUCCESS: 'Connection successful!',
    TEST_FAILED: 'Connection test failed. Check your configuration.',
  },

  // ── Providers ──
  PROVIDER: {
    ACTIVATE_EMBED_SUCCESS: 'Embedding provider "{name}" activated',
    ACTIVATE_GEN_SUCCESS: 'Generation provider "{name}" activated',
    ACTIVATE_FAILED: 'Unable to activate provider. Please try again.',
  },

  // ── Auth / Admin ──
  AUTH: {
    LOGIN_FAILED: 'Invalid credentials. Please try again.',
    SESSION_EXPIRED: 'Your session has expired. Please sign in again.',
    FORBIDDEN: 'You don\'t have permission for this action.',
    USER_CREATED: 'User "{username}" created successfully',
    USER_DELETED: 'User "{username}" deleted',
    PASSWORD_CHANGED: 'Password changed successfully',
    ROLE_UPDATED: 'Role updated for "{username}"',
  },

  // ── HTTP Errors (generic fallbacks) ──
  HTTP: {
    BAD_REQUEST: 'Invalid request. Please check your input.',
    UNAUTHORIZED: 'Your session has expired. Please sign in again.',
    FORBIDDEN: 'You don\'t have permission for this action.',
    NOT_FOUND: 'The requested resource was not found.',
    CONFLICT: 'This operation conflicts with existing data.',
    RATE_LIMITED: 'Too many requests. Please wait a moment.',
    SERVER_ERROR: 'Something went wrong on our end. Please try again.',
    NETWORK_ERROR: 'Unable to connect to the server. Check your network.',
    UNKNOWN: 'An unexpected error occurred. Please try again.',
  },

  // ── Confirm Dialogs ──
  CONFIRM: {
    FORGET_TITLE: 'Forget Memory',
    FORGET_MESSAGE: 'This will tombstone the memory. It can be recovered via vacuum.',
    DELETE_TITLE: 'Delete Permanently',
    DELETE_MESSAGE: 'This action cannot be undone. Are you sure?',
    BULK_FORGET_TITLE: 'Forget {count} Memories',
    BULK_FORGET_MESSAGE: 'This will tombstone {count} memories. They can be recovered via vacuum.',
    REMOVE_CONNECTOR_TITLE: 'Remove Connector',
    REMOVE_CONNECTOR_MESSAGE: 'This will remove the connector and stop any active ingestion.',
    DELETE_USER_TITLE: 'Delete User',
    DELETE_USER_MESSAGE: 'This will permanently delete user "{username}" and all their data.',
  },
} as const;
