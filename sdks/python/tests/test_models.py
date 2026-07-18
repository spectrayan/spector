# Copyright 2026 Spectrayan — Apache 2.0
"""Unit tests for Spector Python SDK models and transport serialization."""

import json
import pytest

from spector.models import (
    CognitiveRecord,
    CognitiveResult,
    MemorySource,
    MemoryStatus,
    MemoryType,
    RecallOptions,
    SearchResult,
)


class TestMemoryType:
    def test_values(self):
        assert MemoryType.WORKING.value == "WORKING"
        assert MemoryType.EPISODIC.value == "EPISODIC"
        assert MemoryType.SEMANTIC.value == "SEMANTIC"
        assert MemoryType.PROCEDURAL.value == "PROCEDURAL"

    def test_is_str_enum(self):
        assert isinstance(MemoryType.WORKING, str)
        assert MemoryType.SEMANTIC == "SEMANTIC"

    def test_from_string(self):
        assert MemoryType("EPISODIC") == MemoryType.EPISODIC


class TestMemorySource:
    def test_values(self):
        assert MemorySource.USER_STATED.value == "USER_STATED"
        assert MemorySource.OBSERVED.value == "OBSERVED"
        assert MemorySource.INFERRED.value == "INFERRED"
        assert MemorySource.CONSOLIDATED.value == "CONSOLIDATED"


class TestRecallOptions:
    def test_defaults(self):
        opts = RecallOptions()
        assert opts.top_k == 10
        assert opts.alpha == 0.6
        assert opts.beta == 0.4
        assert opts.recall_mode == "LEARN"

    def test_to_args_defaults(self):
        args = RecallOptions().to_args()
        assert args == {"top_k": 10}

    def test_to_args_with_tags(self):
        opts = RecallOptions(top_k=5, tags=["java", "performance"])
        args = opts.to_args()
        assert args["top_k"] == 5
        assert args["tags"] == "java,performance"

    def test_to_args_with_all_overrides(self):
        opts = RecallOptions(
            top_k=3,
            tags=["test"],
            min_importance=0.5,
            memory_types=[MemoryType.SEMANTIC, MemoryType.EPISODIC],
            min_valence=-50,
            max_valence=100,
            alpha=0.8,
            beta=0.2,
            recall_mode="OBSERVE",
        )
        args = opts.to_args()
        assert args["top_k"] == 3
        assert args["tags"] == "test"
        assert args["min_importance"] == 0.5
        assert args["memory_types"] == "SEMANTIC,EPISODIC"
        assert args["min_valence"] == -50
        assert args["max_valence"] == 100
        assert args["alpha"] == 0.8
        assert args["beta"] == 0.2
        assert args["recall_mode"] == "OBSERVE"

    def test_frozen(self):
        opts = RecallOptions()
        with pytest.raises(AttributeError):
            opts.top_k = 20


class TestCognitiveResult:
    def test_creation(self):
        result = CognitiveResult(id="test-1", text="Hello", score=0.95)
        assert result.id == "test-1"
        assert result.text == "Hello"
        assert result.score == 0.95
        assert result.memory_type == "SEMANTIC"

    def test_from_text(self):
        results = CognitiveResult.from_text("Some recall output")
        assert len(results) == 1
        assert results[0].text == "Some recall output"


class TestCognitiveRecord:
    def test_creation(self):
        record = CognitiveRecord(
            id="rec-1",
            text="Test memory",
            importance=0.75,
            valence=10,
        )
        assert record.id == "rec-1"
        assert record.importance == 0.75
        assert not record.tombstoned
        assert not record.pinned

    def test_from_text(self):
        record = CognitiveRecord.from_text("Raw inspect output")
        assert record.raw_text == "Raw inspect output"


class TestSearchResult:
    def test_creation(self):
        result = SearchResult(id="doc-1", text="Found document", score=0.88)
        assert result.score == 0.88

    def test_from_text(self):
        results = SearchResult.from_text("Search output")
        assert len(results) == 1


class TestMemoryStatus:
    def test_from_text(self):
        status = MemoryStatus.from_text("Tier counts: ...")
        assert status.raw_text == "Tier counts: ..."
