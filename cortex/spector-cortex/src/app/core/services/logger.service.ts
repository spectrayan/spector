// ═══════════════════════════════════════════════════════════════════════
// Spector Cortex — Logger Service
// ═══════════════════════════════════════════════════════════════════════
// Centralized logging with environment-driven level filtering.
//
// Usage:
//   private readonly log = inject(LoggerService);
//   this.log.debug('MyComponent', 'Loaded data', someObj);
//   this.log.info('MyService', 'Connection established');
//   this.log.warn('AuthService', 'Token expiring soon');
//   this.log.error('GraphExplorer', 'Failed to load', err);
//
// Configuration (environment.ts):
//   logging.enabled = true/false   — master switch
//   logging.level   = 'debug' | 'info' | 'warn' | 'error' | 'off'

import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';

export type LogLevel = 'debug' | 'info' | 'warn' | 'error' | 'off';

const LEVEL_PRIORITY: Record<LogLevel, number> = {
  debug: 0,
  info: 1,
  warn: 2,
  error: 3,
  off: 4,
};

/** ANSI-style prefix colors for browser console (CSS %c formatting). */
const LEVEL_STYLES: Record<string, string> = {
  debug: 'color: #7c8fa6; font-weight: normal',
  info:  'color: #00bcd4; font-weight: bold',
  warn:  'color: #ffc107; font-weight: bold',
  error: 'color: #ef5350; font-weight: bold',
};

const TAG_STYLE = 'color: #00ffcc; font-weight: bold';

@Injectable({ providedIn: 'root' })
export class LoggerService {
  private readonly enabled: boolean;
  private readonly minLevel: number;

  constructor() {
    const config = environment.logging;
    this.enabled = config?.enabled ?? true;
    this.minLevel = LEVEL_PRIORITY[config?.level ?? 'debug'];
  }

  /** Verbose development-time logging. Stripped in production (level >= info). */
  debug(tag: string, message: string, ...data: any[]): void {
    this.log('debug', tag, message, data);
  }

  /** Informational messages — feature lifecycle, connections, state changes. */
  info(tag: string, message: string, ...data: any[]): void {
    this.log('info', tag, message, data);
  }

  /** Recoverable issues — deprecations, fallbacks, non-critical failures. */
  warn(tag: string, message: string, ...data: any[]): void {
    this.log('warn', tag, message, data);
  }

  /** Unrecoverable errors — crashes, data loss, critical failures. */
  error(tag: string, message: string, ...data: any[]): void {
    this.log('error', tag, message, data);
  }

  // ── Internal ──

  private log(level: LogLevel, tag: string, message: string, data: any[]): void {
    if (!this.enabled || LEVEL_PRIORITY[level] < this.minLevel) return;

    const timestamp = new Date().toISOString().substring(11, 23); // HH:mm:ss.SSS
    const prefix = `%c${level.toUpperCase().padEnd(5)} %c[${tag}]`;
    const formatted = `${prefix} %c${timestamp} — ${message}`;

    const consoleFn = level === 'error' ? console.error
                    : level === 'warn'  ? console.warn
                    : level === 'info'  ? console.info
                    : console.debug;

    if (data.length > 0) {
      consoleFn(formatted, LEVEL_STYLES[level], TAG_STYLE, 'color: inherit', ...data);
    } else {
      consoleFn(formatted, LEVEL_STYLES[level], TAG_STYLE, 'color: inherit');
    }
  }
}
