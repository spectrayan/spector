# Copyright 2026 Spectrayan — Apache 2.0
"""
Data models for the Spector Python SDK.

All models are plain dataclasses with no external dependencies.
They mirror the Java types in ``spector-memory`` and ``spector-engine``.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Optional


# ═══════════════════════════════════════════════════════════
# Enums
# ═══════════════════════════════════════════════════════════


class MemoryType(str, Enum):
    """Cognitive tier — mirrors ``com.spectrayan.spector.memory.model.MemoryType``."""
    WORKING = "WORKING"
    EPISODIC = "EPISODIC"
    SEMANTIC = "SEMANTIC"
    PROCEDURAL = "PROCEDURAL"


class MemorySource(str, Enum):
    """Provenance — mirrors ``com.spectrayan.spector.memory.cortex.MemorySource``."""
    USER_STATED = "USER_STATED"
    OBSERVED = "OBSERVED"
    INFERRED = "INFERRED"
    PROCEDURAL = "PROCEDURAL"
    CONSOLIDATED = "CONSOLIDATED"


# ═══════════════════════════════════════════════════════════
# Recall Configuration
# ═══════════════════════════════════════════════════════════


@dataclass(frozen=True)
class RecallOptions:
    """Configuration for ``memory.recall()``.

    Mirrors ``RecallOptions.Builder`` in Java. All fields have sensible
    defaults so callers can use ``RecallOptions()`` for default behavior.
    """
    top_k: int = 10
    tags: list[str] = field(default_factory=list)
    min_importance: float = 0.0
    memory_types: list[MemoryType] = field(default_factory=list)
    min_valence: int = -128
    max_valence: int = 127
    alpha: float = 0.6
    beta: float = 0.4
    recall_mode: str = "LEARN"

    def to_args(self) -> dict:
        """Converts to MCP tool arguments dict."""
        args: dict = {"top_k": self.top_k}
        if self.tags:
            args["tags"] = ",".join(self.tags)
        if self.min_importance > 0:
            args["min_importance"] = self.min_importance
        if self.memory_types:
            args["memory_types"] = ",".join(t.value for t in self.memory_types)
        if self.min_valence != -128:
            args["min_valence"] = self.min_valence
        if self.max_valence != 127:
            args["max_valence"] = self.max_valence
        if self.alpha != 0.6:
            args["alpha"] = self.alpha
        if self.beta != 0.4:
            args["beta"] = self.beta
        if self.recall_mode != "LEARN":
            args["recall_mode"] = self.recall_mode
        return args


# ═══════════════════════════════════════════════════════════
# Result Types
# ═══════════════════════════════════════════════════════════


@dataclass(frozen=True)
class CognitiveResult:
    """A single result from ``memory.recall()``.

    Mirrors ``com.spectrayan.spector.memory.model.CognitiveResult``.
    """
    id: str
    text: str
    score: float = 0.0
    importance: float = 0.0
    age_days: float = 0.0
    recall_count: int = 0
    valence: int = 0
    memory_type: str = "SEMANTIC"
    source: str = "OBSERVED"
    tags: list[str] = field(default_factory=list)

    @classmethod
    def from_text(cls, raw_text: str) -> list[CognitiveResult]:
        """Parses recall tool output into a list of results.

        The MCP tool returns structured text. This provides a best-effort
        parse — the raw text is always available via ``raw_text``.
        """
        # The recall tool returns formatted text, not JSON.
        # We return a single result containing the full text for now.
        # Future: parse the structured format.
        return [cls(id="recall-result", text=raw_text)]


@dataclass(frozen=True)
class CognitiveRecord:
    """Full cognitive snapshot from ``memory.inspect()`` or ``memory.browse()``.

    Mirrors ``com.spectrayan.spector.memory.model.CognitiveRecord``.
    """
    id: str
    text: str
    memory_type: str = "SEMANTIC"
    source: str = "OBSERVED"
    tags: list[str] = field(default_factory=list)
    created_at: str = ""
    importance: float = 0.0
    valence: int = 0
    arousal: int = 0
    agent_recall_count: int = 0
    spector_recall_count: int = 0
    storage_strength: float = 1.0
    tombstoned: bool = False
    consolidated: bool = False
    pinned: bool = False
    resolved: bool = False
    raw_text: str = ""

    @classmethod
    def from_text(cls, raw_text: str) -> CognitiveRecord:
        """Parses inspect tool output into a CognitiveRecord."""
        return cls(id="inspected", text=raw_text, raw_text=raw_text)


@dataclass(frozen=True)
class SearchResult:
    """A single result from ``engine.search()`` or ``engine.hybrid_search()``."""
    id: str
    text: str
    score: float = 0.0
    metadata: dict = field(default_factory=dict)
    raw_text: str = ""

    @classmethod
    def from_text(cls, raw_text: str) -> list[SearchResult]:
        """Parses search tool output."""
        return [cls(id="search-result", text=raw_text, raw_text=raw_text)]


@dataclass(frozen=True)
class MemoryStatus:
    """Memory system status from ``memory.status()``."""
    total_memories: int = 0
    working: int = 0
    episodic: int = 0
    semantic: int = 0
    procedural: int = 0
    raw_text: str = ""

    @classmethod
    def from_text(cls, raw_text: str) -> MemoryStatus:
        """Parses status tool output."""
        return cls(raw_text=raw_text)


@dataclass(frozen=True)
class MemoryInsight:
    """Metamemory analysis from ``memory.introspect()``."""
    topic: str = ""
    raw_text: str = ""

    @classmethod
    def from_text(cls, topic: str, raw_text: str) -> MemoryInsight:
        return cls(topic=topic, raw_text=raw_text)
