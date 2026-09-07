# Copyright 2026 Spectrayan — Apache 2.0
"""
Domain exceptions for the Spector Client SDK.
"""

from __future__ import annotations
from typing import Optional


class SpectorClientError(Exception):
    """Base exception for all Spector Client errors."""

    def __init__(self, message: str, status_code: Optional[int] = None, details: Optional[dict] = None):
        super().__init__(message)
        self.message = message
        self.status_code = status_code
        self.details = details or {}

    def __str__(self) -> str:
        if self.status_code:
            return f"[{self.status_code}] {self.message}"
        return self.message


class MemoryNotFoundError(SpectorClientError):
    """Raised when a requested memory ID does not exist."""

    def __init__(self, memory_id: str, message: Optional[str] = None):
        msg = message or f"Memory with ID '{memory_id}' was not found"
        super().__init__(msg, status_code=404)
        self.memory_id = memory_id


class SpectorAuthError(SpectorClientError):
    """Raised when authentication fails (invalid API key or bearer token)."""

    def __init__(self, message: str = "Authentication failed", status_code: int = 401):
        super().__init__(message, status_code=status_code)


class SpectorValidationError(SpectorClientError):
    """Raised when a request fails validation constraints."""

    def __init__(self, message: str = "Validation failed", details: Optional[dict] = None):
        super().__init__(message, status_code=400, details=details)


class SpectorServerError(SpectorClientError):
    """Raised when the Spector server encounters an internal error."""

    def __init__(self, message: str = "Internal server error", status_code: int = 500, details: Optional[dict] = None):
        super().__init__(message, status_code=status_code, details=details)


class TransportError(SpectorClientError):
    """Raised when transport communication (HTTP connection, stdio pipe, JSON-RPC) fails."""
