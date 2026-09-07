# Copyright 2026 Spectrayan — Apache 2.0
"""Unit tests for spector_client domain models."""

import unittest
from spector_client.models import (
    MemoryTier,
    MemorySource,
    RecallRecord,
    SearchRecord,
    MemoryRecord,
    MemoryStatus,
    SseEvent,
)


class TestModels(unittest.TestCase):
    def test_tier_values(self):
        self.assertEqual(MemoryTier.WORKING.value, "WORKING")
        self.assertEqual(MemoryTier.EPISODIC.value, "EPISODIC")
        self.assertEqual(MemoryTier.SEMANTIC.value, "SEMANTIC")
        self.assertEqual(MemoryTier.PROCEDURAL.value, "PROCEDURAL")

    def test_str_enum(self):
        self.assertTrue(isinstance(MemoryTier.WORKING, str))
        self.assertEqual(MemoryTier.SEMANTIC, "SEMANTIC")

    def test_recall_record(self):
        rec = RecallRecord(
            id="08ABC123",
            text="User prefers python",
            score=0.92,
            tier=MemoryTier.SEMANTIC,
            tags=["preferences", "python"],
        )
        self.assertEqual(rec.id, "08ABC123")
        self.assertEqual(rec.score, 0.92)
        self.assertIn("python", rec.tags)

    def test_memory_record(self):
        rec = MemoryRecord(
            id="08DEF456",
            text="Procedural deployment guide",
            tier=MemoryTier.PROCEDURAL,
            resolved=True,
        )
        self.assertEqual(rec.id, "08DEF456")
        self.assertTrue(rec.resolved)

    def test_sse_event(self):
        event = SseEvent(event="memory.mutation", data={"id": "123", "action": "stored"})
        self.assertEqual(event.event, "memory.mutation")
        self.assertEqual(event.data["id"], "123")


if __name__ == "__main__":
    unittest.main()
