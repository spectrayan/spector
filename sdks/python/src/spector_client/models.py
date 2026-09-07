# Copyright 2026 Spectrayan — Apache 2.0
"""
Domain models and enums for the Spector Client SDK.
"""

from __future__ import annotations
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, List, Optional


class MemoryTier(str, Enum):
    """Biological memory tiers in Spector."""
    WORKING = "WORKING"
    EPISODIC = "EPISODIC"
    SEMANTIC = "SEMANTIC"
    PROCEDURAL = "PROCEDURAL"


class MemorySource(str, Enum):
    """Provenance source of the memory."""
    USER_STATED = "USER_STATED"
    OBSERVED = "OBSERVED"
    INFERRED = "INFERRED"
    PROCEDURAL = "PROCEDURAL"


@dataclass
class RecallRecord:
    """A memory retrieved from a recall query with cognitive scoring."""
    id: str
    text: str
    score: float = 0.0
    tier: MemoryTier = MemoryTier.SEMANTIC
    tags: List[str] = field(default_factory=list)
    valence: int = 0
    arousal: int = 0
    importance: float = 0.0
    metadata: Dict[str, Any] = field(default_factory=dict)
    similarity: float = 0.0
    age_days: float = 0.0
    decay_factor: float = 1.0


@dataclass
class SearchRecord:
    """A vector similarity search match."""
    id: str
    text: str
    score: float = 0.0
    tags: List[str] = field(default_factory=list)
    metadata: Dict[str, Any] = field(default_factory=dict)


@dataclass
class MemoryRecord:
    """Detailed memory engram record for inspection and status."""
    id: str
    text: str
    tier: MemoryTier = MemoryTier.SEMANTIC
    tags: List[str] = field(default_factory=list)
    valence: int = 0
    arousal: int = 0
    importance: float = 0.0
    decay_factor: float = 1.0
    recall_count: int = 0
    source: MemorySource = MemorySource.OBSERVED
    resolved: bool = False
    tombstoned: bool = False
    metadata: Dict[str, Any] = field(default_factory=dict)
    created_at: Optional[str] = None
    updated_at: Optional[str] = None


@dataclass
class MemoryStatus:
    """Real-time memory tier statistics and system health."""
    total_memories: int = 0
    working_count: int = 0
    episodic_count: int = 0
    semantic_count: int = 0
    procedural_count: int = 0
    tombstone_count: int = 0
    dimensions: int = 384
    persistence_mode: str = "OFF"
    extra: Dict[str, Any] = field(default_factory=dict)


@dataclass
class SseEvent:
    """Server-Sent Event received from the real-time event stream."""
    event: str
    data: Any
    id: Optional[str] = None
    retry: Optional[int] = None
