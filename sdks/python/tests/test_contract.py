# Copyright 2026 Spectrayan — Apache 2.0
"""HTTP Contract integration tests for Spector Python SDK against a local mock server."""

import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

from spector_client.client import SpectorClient
from spector_client.exceptions import MemoryNotFoundError
from spector_client.models import MemoryTier


class MockSpectorHandler(BaseHTTPRequestHandler):
    recorded_requests = []

    def log_message(self, format, *args):
        pass  # Suppress default server logs

    def do_POST(self):
        content_length = int(self.headers.get("Content-Length", 0))
        body_bytes = self.rfile.read(content_length) if content_length > 0 else b""
        body = json.loads(body_bytes.decode("utf-8")) if body_bytes else {}

        MockSpectorHandler.recorded_requests.append({
            "method": "POST",
            "path": self.path,
            "headers": dict(self.headers),
            "body": body,
        })

        parsed = urlparse(self.path)
        path = parsed.path

        if path == "/api/v1/memory/remember":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ACCEPTED", "message": "Memory queued"}).encode("utf-8"))
        elif path == "/api/v1/memory/store":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"id": "mem-101", "status": "STORED"}).encode("utf-8"))
        elif path == "/api/v1/memory/recall":
            # Test wrapped response if query contains 'wrapped', else raw list
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            recall_item = {
                "id": "mem-101",
                "text": "Vector API SIMD acceleration",
                "cognitiveScore": 0.95,
                "tier": "SEMANTIC",
                "tags": ["vector", "simd"],
                "ageDays": 1.5,
                "decayFactor": 0.98,
                "valence": 1,
                "arousal": 2,
                "importance": 0.9,
                "similarity": 0.92,
            }
            if "wrapped" in body.get("query", ""):
                payload = {"results": [recall_item]}
            else:
                payload = [recall_item]
            self.wfile.write(json.dumps(payload).encode("utf-8"))
        elif path == "/api/v1/memory/search":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps([{
                "id": "mem-202",
                "text": "Semantic search match",
                "score": 0.88,
                "tags": ["search"],
            }]).encode("utf-8"))
        elif path.endswith("/reinforce"):
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "REINFORCED"}).encode("utf-8"))
        elif path.endswith("/suppress"):
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "SUPPRESSED"}).encode("utf-8"))
        elif path.endswith("/resolve"):
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "RESOLVED"}).encode("utf-8"))
        elif path == "/api/v1/memory/browse":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps([{"id": "mem-303", "text": "Browsed memory"}]).encode("utf-8"))
        elif path == "/api/v1/memory/consolidate":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "CONSOLIDATED"}).encode("utf-8"))
        elif path == "/api/v1/memory/vacuum":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "COMPACTED"}).encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        MockSpectorHandler.recorded_requests.append({
            "method": "GET",
            "path": self.path,
            "headers": {k.lower(): v for k, v in self.headers.items()},
        })

        if path == "/api/v1/memory/status":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({
                "totalMemories": 42,
                "workingCount": 5,
                "episodicCount": 10,
                "semanticCount": 20,
                "proceduralCount": 7,
                "tombstoneCount": 0,
                "dimensions": 384,
                "persistenceMode": "ROCKSDB",
            }).encode("utf-8"))
        elif path == "/api/v1/memory/stats":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"uptimeSeconds": 1234, "queriesPerSecond": 45.2}).encode("utf-8"))
        elif path == "/api/v1/memory/table":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"page": 0, "pageSize": 20, "items": []}).encode("utf-8"))
        elif path == "/api/v1/memory/mem-101":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({
                "id": "mem-101",
                "text": "Vector API SIMD acceleration",
                "tier": "SEMANTIC",
                "tags": ["vector"],
                "importance": 0.95,
                "recallCount": 3,
                "resolved": False,
                "tombstoned": False,
            }).encode("utf-8"))
        elif path.startswith("/api/v1/memory/nonexistent"):
            self.send_response(404)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"message": "Memory not found"}).encode("utf-8"))
        elif path in ("/api/v1/events", "/api/v1/events/stream"):
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            sse_data = (
                b"event: memory.stored\r\n"
                b'data: {"id": "mem-101", "tier": "SEMANTIC"}\r\n'
                b"id: evt-001\r\n"
                b"\r\n"
            )
            self.wfile.write(sse_data)
        else:
            self.send_response(404)
            self.end_headers()

    def do_DELETE(self):
        MockSpectorHandler.recorded_requests.append({
            "method": "DELETE",
            "path": self.path,
            "headers": dict(self.headers),
        })
        self.send_response(204)
        self.end_headers()


class TestHttpContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = HTTPServer(("127.0.0.1", 0), MockSpectorHandler)
        cls.port = cls.server.server_address[1]
        cls.server_thread = threading.Thread(target=cls.server.serve_forever)
        cls.server_thread.daemon = True
        cls.server_thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def setUp(self):
        MockSpectorHandler.recorded_requests.clear()
        self.client = SpectorClient.builder() \
            .with_rest(f"http://127.0.0.1:{self.port}", api_key="sk-contract-key") \
            .with_user("usr_42") \
            .with_agent("agt_99") \
            .with_namespace("team_spec") \
            .build()

    def tearDown(self):
        self.client.close()

    def test_auth_and_metadata_headers(self):
        self.client.memory.status()
        self.assertTrue(len(MockSpectorHandler.recorded_requests) > 0)
        headers = MockSpectorHandler.recorded_requests[-1]["headers"]
        self.assertEqual(headers.get("x-api-key"), "sk-contract-key")
        self.assertEqual(headers.get("x-user-id"), "usr_42")
        self.assertEqual(headers.get("x-agent-id"), "agt_99")
        self.assertEqual(headers.get("x-namespace"), "team_spec")

    def test_remember_and_store_verbs(self):
        rem_res = self.client.memory.remember(
            "SIMD acceleration in Java 25",
            tier=MemoryTier.SEMANTIC,
            tags=["java25", "vector"],
        )
        self.assertEqual(rem_res.get("status"), "ACCEPTED")

        store_res = self.client.memory.store("Synchronous item", tags=["sync"])
        self.assertEqual(store_res.get("id"), "mem-101")

    def test_recall_raw_list_and_wrapped_envelope(self):
        # 1. Raw list recall
        records_raw = self.client.memory.recall("standard query", top_k=5)
        self.assertEqual(len(records_raw), 1)
        self.assertEqual(records_raw[0].id, "mem-101")
        self.assertEqual(records_raw[0].score, 0.95)
        self.assertEqual(records_raw[0].tier, MemoryTier.SEMANTIC)
        self.assertEqual(records_raw[0].age_days, 1.5)

        # 2. Wrapped payload recall ({"results": [...]})
        records_wrapped = self.client.memory.recall("wrapped envelope query", top_k=5)
        self.assertEqual(len(records_wrapped), 1)
        self.assertEqual(records_wrapped[0].id, "mem-101")
        self.assertEqual(records_wrapped[0].score, 0.95)

    def test_search_verb(self):
        results = self.client.memory.search("semantic search query", top_k=3)
        self.assertEqual(len(results), 1)
        self.assertEqual(results[0].id, "mem-202")
        self.assertEqual(results[0].score, 0.88)

    def test_get_and_not_found(self):
        mem = self.client.memory.get("mem-101")
        self.assertEqual(mem.id, "mem-101")
        self.assertEqual(mem.text, "Vector API SIMD acceleration")

        with self.assertRaises(MemoryNotFoundError):
            self.client.memory.get("nonexistent-mem-999")

    def test_lifecycle_verbs(self):
        # forget
        self.client.memory.forget("mem-101", reason="cleanup")
        # reinforce
        self.client.memory.reinforce("mem-101", valence=2)
        # suppress / unsuppress
        self.client.memory.suppress("mem-101", reason="filter")
        self.client.memory.unsuppress("mem-101")
        # resolve / unresolve
        self.client.memory.resolve("mem-101")
        self.client.memory.unresolve("mem-101")
        # browse
        browsed = self.client.memory.browse(tags=["vector"])
        self.assertEqual(len(browsed), 1)
        # maintenance
        self.client.memory.consolidate()
        self.client.memory.vacuum(tier="SEMANTIC")

    def test_sse_event_stream(self):
        events = list(self.client.events.stream(topics=["memory"]))
        self.assertEqual(len(events), 1)
        self.assertEqual(events[0].event, "memory.stored")
        self.assertEqual(events[0].id, "evt-001")
        self.assertEqual(events[0].data, {"id": "mem-101", "tier": "SEMANTIC"})


if __name__ == "__main__":
    unittest.main()

