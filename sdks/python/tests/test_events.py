# Copyright 2026 Spectrayan — Apache 2.0
"""Unit tests for Server-Sent Events (SSE) stream parsing."""

import io
import json
import unittest
from unittest.mock import patch

from spector_client.config import ClientConfig
from spector_client.events import EventClient
from spector_client.transports.rest import RestTransport


class MockSseResponse(io.BytesIO):
    def close(self):
        super().close()


class TestSseStreaming(unittest.TestCase):
    @patch("urllib.request.urlopen")
    def test_sse_stream_parsing(self, mock_urlopen):
        raw_sse = (
            b"event: memory.mutation\r\n"
            b'data: {"id": "mem-1", "action": "stored"}\r\n'
            b"id: evt-101\r\n"
            b"\r\n"
            b"event: cortex\r\n"
            b'data: {"pulse": 42}\r\n'
            b"\r\n"
        )
        mock_urlopen.return_value = MockSseResponse(raw_sse)

        config = ClientConfig(base_url="http://localhost:7070")
        transport = RestTransport(config)
        client = EventClient(transport)

        events = list(client.stream(topics=["memory", "cortex"]))
        self.assertEqual(len(events), 2)

        self.assertEqual(events[0].event, "memory.mutation")
        self.assertEqual(events[0].data, {"id": "mem-1", "action": "stored"})
        self.assertEqual(events[0].id, "evt-101")

        self.assertEqual(events[1].event, "cortex")
        self.assertEqual(events[1].data, {"pulse": 42})


if __name__ == "__main__":
    unittest.main()
