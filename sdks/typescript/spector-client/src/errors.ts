/*
 * Copyright 2026 Spectrayan — Apache 2.0
 */

/**
 * Base error class for all Spector Client errors.
 */
export class SpectorClientError extends Error {
  constructor(
    message: string,
    public readonly statusCode?: number,
    public readonly details?: unknown
  ) {
    super(message);
    this.name = 'SpectorClientError';
    Object.setPrototypeOf(this, new.target.prototype);
  }
}

/**
 * Thrown when a memory ID is not found.
 */
export class MemoryNotFoundError extends SpectorClientError {
  constructor(public readonly memoryId: string, message?: string) {
    super(message ?? `Memory with ID '${memoryId}' was not found`, 404);
    this.name = 'MemoryNotFoundError';
  }
}

/**
 * Thrown when authentication fails.
 */
export class SpectorAuthError extends SpectorClientError {
  constructor(message: string = 'Authentication failed', statusCode: number = 401) {
    super(message, statusCode);
    this.name = 'SpectorAuthError';
  }
}

/**
 * Thrown when request parameter validation fails.
 */
export class SpectorValidationError extends SpectorClientError {
  constructor(message: string = 'Validation failed', details?: unknown) {
    super(message, 400, details);
    this.name = 'SpectorValidationError';
  }
}

/**
 * Thrown when the server returns a 5xx error.
 */
export class SpectorServerError extends SpectorClientError {
  constructor(message: string = 'Internal server error', statusCode: number = 500, details?: unknown) {
    super(message, statusCode, details);
    this.name = 'SpectorServerError';
  }
}

/**
 * Thrown when network connection or transport execution fails.
 */
export class TransportError extends SpectorClientError {
  constructor(message: string, details?: unknown) {
    super(message, undefined, details);
    this.name = 'TransportError';
  }
}
