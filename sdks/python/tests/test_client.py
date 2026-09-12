# Copyright 2026 Spectrayan — Apache 2.0
"""Unit tests for SpectorClient and builder configuration."""

import unittest
from spector_client.client import SpectorClient, AsyncSpectorClient
from spector_client.config import ClientConfig
from spector_client.transports.rest import RestTransport
from spector_client.transports.async_rest import AsyncRestTransport


class TestSpectorClientBuilder(unittest.TestCase):
    def test_default_builder(self):
        client = SpectorClient.builder().with_rest(base_url="http://localhost:9090", api_key="test-key").build()
        self.assertIsInstance(client.transport, RestTransport)
        self.assertEqual(client.transport.config.base_url, "http://localhost:9090")
        self.assertEqual(client.transport.config.api_key, "test-key")

    def test_auth_headers(self):
        config = ClientConfig(
            api_key="sk-123",
            bearer_token="jwt-456",
            user_id="user_alpha",
            agent_id="agent_beta",
            namespace="team_gamma",
        )
        headers = config.get_auth_headers()
        self.assertEqual(headers["X-API-Key"], "sk-123")
        self.assertEqual(headers["Authorization"], "Bearer jwt-456")
        self.assertEqual(headers["X-User-Id"], "user_alpha")
        self.assertEqual(headers["X-Agent-Id"], "agent_beta")
        self.assertEqual(headers["X-Namespace"], "team_gamma")

    def test_async_builder(self):
        client = AsyncSpectorClient.builder().with_rest(base_url="http://remote:7070").build()
        self.assertIsInstance(client.transport, AsyncRestTransport)
        self.assertEqual(client.transport.config.base_url, "http://remote:7070")

    def test_context_manager(self):
        with SpectorClient.create_default() as client:
            self.assertIsNotNone(client.memory)
            self.assertIsNotNone(client.events)


if __name__ == "__main__":
    unittest.main()
