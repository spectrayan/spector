# Copyright 2026 Spectrayan — Apache 2.0
"""
Spector Client SDK for Python — Zero-GC Cognitive Memory, Vector Search & Event Streaming.
"""

from spector_client.client import (
    SpectorClient,
    AsyncSpectorClient,
    SpectorClientBuilder,
    AsyncSpectorClientBuilder,
)
from spector_client.config import ClientConfig
from spector_client.exceptions import (
    SpectorClientError,
    MemoryNotFoundError,
    SpectorAuthError,
    SpectorValidationError,
    SpectorServerError,
    TransportError,
)
from spector_client.models import (
    MemoryTier,
    MemorySource,
    RecallRecord,
    SearchRecord,
    MemoryRecord,
    MemoryStatus,
    SseEvent,
)

__version__ = "0.1.0"

__all__ = [
    "SpectorClient",
    "AsyncSpectorClient",
    "SpectorClientBuilder",
    "AsyncSpectorClientBuilder",
    "ClientConfig",
    "MemoryTier",
    "MemorySource",
    "RecallRecord",
    "SearchRecord",
    "MemoryRecord",
    "MemoryStatus",
    "SseEvent",
    "SpectorClientError",
    "MemoryNotFoundError",
    "SpectorAuthError",
    "SpectorValidationError",
    "SpectorServerError",
    "TransportError",
]
