# Copyright 2026 Spectrayan — Apache 2.0
"""Unit tests for MemoryClient and AsyncMemoryClient cognitive verbs."""

import unittest
from spector_client.memory import MemoryClient, AsyncMemoryClient
from spector_client.models import MemoryTier
from spector_client.transports.base import BaseTransport, AsyncBaseTransport


class MockTransport(BaseTransport):
    def __init__(self):
        self.last_method = None
        self.last_path = None
        self.last_body = None
        self.last_query_params = None
        self.canned_response = {}

    def request(self, method, path, query_params=None, body=None):
        self.last_method = method
        self.last_path = path
        self.last_query_params = query_params
        self.last_body = body
        return self.canned_response

    def stream_events(self, path, query_params=None):
        return iter([])

    def close(self):
        pass


class AsyncMockTransport(AsyncBaseTransport):
    def __init__(self):
        self.last_method = None
        self.last_path = None
        self.last_body = None
        self.last_query_params = None
        self.canned_response = {}

    async def request(self, method, path, query_params=None, body=None):
        self.last_method = method
        self.last_path = path
        self.last_query_params = query_params
        self.last_body = body
        return self.canned_response

    async def stream_events(self, path, query_params=None):
        if False:
            yield None

    async def close(self):
        pass


class TestMemoryClientSync(unittest.TestCase):
    def test_remember(self):
        transport = MockTransport()
        transport.canned_response = {"status": "ACCEPTED", "message": "Memory queued"}
        client = MemoryClient(transport)

        res = client.remember("Fast SIMD indexing", tier=MemoryTier.SEMANTIC, tags=["simd", "perf"])
        self.assertEqual(transport.last_method, "POST")
        self.assertEqual(transport.last_path, "/api/v1/memory/remember")
        self.assertEqual(transport.last_body["text"], "Fast SIMD indexing")
        self.assertEqual(transport.last_body["tier"], "SEMANTIC")
        self.assertEqual(transport.last_body["tags"], "simd,perf")
        self.assertEqual(res["status"], "ACCEPTED")

    def test_recall(self):
        transport = MockTransport()
        transport.canned_response = [
            {
                "id": "mem-1",
                "text": "SIMD acceleration",
                "score": 0.89,
                "tier": "SEMANTIC",
                "tags": ["simd"],
            }
        ]
        client = MemoryClient(transport)

        records = client.recall("simd query", top_k=3)
        self.assertEqual(transport.last_path, "/api/v1/memory/recall")
        self.assertEqual(transport.last_body["query"], "simd query")
        self.assertEqual(transport.last_body["topK"], 3)
        self.assertEqual(len(records), 1)
        self.assertEqual(records[0].id, "mem-1")
        self.assertEqual(records[0].score, 0.89)

    def test_forget(self):
        transport = MockTransport()
        client = MemoryClient(transport)
        client.forget("mem-99", reason="obsolete")
        self.assertEqual(transport.last_method, "DELETE")
        self.assertEqual(transport.last_path, "/api/v1/memory/mem-99")
        self.assertEqual(transport.last_body, {"reason": "obsolete"})

    def test_reinforce_and_resolve(self):
        transport = MockTransport()
        client = MemoryClient(transport)
        client.reinforce("mem-99", valence=1)
        self.assertEqual(transport.last_path, "/api/v1/memory/mem-99/reinforce")
        self.assertEqual(transport.last_body, {"valence": 1})

        client.resolve("mem-99")
        self.assertEqual(transport.last_path, "/api/v1/memory/mem-99/resolve")
        self.assertEqual(transport.last_body, {"resolved": True})


class TestMemoryClientAsync(unittest.IsolatedAsyncioTestCase):
    async def test_remember_async(self):
        transport = AsyncMockTransport()
        transport.canned_response = {"status": "ACCEPTED"}
        client = AsyncMemoryClient(transport)

        res = await client.remember("Async memory test", tier=MemoryTier.WORKING)
        self.assertEqual(transport.last_method, "POST")
        self.assertEqual(transport.last_path, "/api/v1/memory/remember")
        self.assertEqual(transport.last_body["tier"], "WORKING")
        self.assertEqual(res["status"], "ACCEPTED")

    async def test_recall_async(self):
        transport = AsyncMockTransport()
        transport.canned_response = [
            {"id": "mem-async", "text": "Result", "score": 0.99, "tier": "WORKING"}
        ]
        client = AsyncMemoryClient(transport)

        records = await client.recall("test")
        self.assertEqual(len(records), 1)
        self.assertEqual(records[0].id, "mem-async")


if __name__ == "__main__":
    unittest.main()
