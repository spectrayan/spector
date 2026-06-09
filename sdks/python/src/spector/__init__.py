# Copyright 2026 Spectrayan — Apache 2.0
"""
Spector Python SDK — Zero-dependency client for Spector's MCP server.

Usage::

    from spector import SpectorClient

    with SpectorClient(jar_path="/path/to/spector.jar",
                       config_path="/path/to/spector.yml") as client:
        # Memory operations
        mem_id = client.memory.remember("User prefers dark mode", tags=["preferences"])
        results = client.memory.recall("user preferences")
        record = client.memory.inspect(mem_id)

        # Search operations
        hits = client.engine.search("SIMD acceleration", top_k=5)
"""

from spector.client import SpectorClient
from spector.models import (
    MemoryType,
    MemorySource,
    RecallOptions,
    CognitiveResult,
    CognitiveRecord,
    SearchResult,
    MemoryStatus,
)

__version__ = "0.1.0"
__all__ = [
    "SpectorClient",
    "MemoryType",
    "MemorySource",
    "RecallOptions",
    "CognitiveResult",
    "CognitiveRecord",
    "SearchResult",
    "MemoryStatus",
]
