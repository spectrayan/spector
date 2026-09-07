# Copyright 2026 Spectrayan — Apache 2.0
"""
Main entry point and fluent builders for Spector Python Client SDK.
"""

from __future__ import annotations
from typing import Any, Dict, Optional

from spector_client.config import ClientConfig
from spector_client.events import EventClient, AsyncEventClient
from spector_client.memory import MemoryClient, AsyncMemoryClient
from spector_client.transports.base import BaseTransport, AsyncBaseTransport
from spector_client.transports.rest import RestTransport
from spector_client.transports.async_rest import AsyncRestTransport
from spector_client.transports.mcp_http import McpHttpTransport
from spector_client.transports.stdio import StdioTransport


class SpectorClient:
    """Main synchronous client for Spector Cognitive Memory & Search."""

    def __init__(self, transport: BaseTransport):
        self._transport = transport
        self._memory = MemoryClient(transport)
        self._events = EventClient(transport)

    @classmethod
    def builder(cls) -> SpectorClientBuilder:
        return SpectorClientBuilder()

    @classmethod
    def create_default(cls, base_url: str = "http://localhost:7070") -> SpectorClient:
        return cls.builder().with_rest(base_url=base_url).build()

    @property
    def memory(self) -> MemoryClient:
        return self._memory

    @property
    def events(self) -> EventClient:
        return self._events

    @property
    def transport(self) -> BaseTransport:
        return self._transport

    def close(self) -> None:
        self._transport.close()

    def __enter__(self) -> SpectorClient:
        return self

    def __exit__(self, exc_type, exc_val, exc_tb) -> None:
        self.close()


class AsyncSpectorClient:
    """Main asynchronous client for Spector Cognitive Memory & Search."""

    def __init__(self, transport: AsyncBaseTransport):
        self._transport = transport
        self._memory = AsyncMemoryClient(transport)
        self._events = AsyncEventClient(transport)

    @classmethod
    def builder(cls) -> AsyncSpectorClientBuilder:
        return AsyncSpectorClientBuilder()

    @classmethod
    def create_default(cls, base_url: str = "http://localhost:7070") -> AsyncSpectorClient:
        return cls.builder().with_rest(base_url=base_url).build()

    @property
    def memory(self) -> AsyncMemoryClient:
        return self._memory

    @property
    def events(self) -> AsyncEventClient:
        return self._events

    @property
    def transport(self) -> AsyncBaseTransport:
        return self._transport

    async def close(self) -> None:
        await self._transport.close()

    async def __aenter__(self) -> AsyncSpectorClient:
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb) -> None:
        await self.close()


class SpectorClientBuilder:
    """Fluent builder for synchronous SpectorClient."""

    def __init__(self):
        self._config = ClientConfig()
        self._custom_transport: Optional[BaseTransport] = None

    def with_rest(
        self,
        base_url: str = "http://localhost:7070",
        api_key: Optional[str] = None,
        bearer_token: Optional[str] = None,
        user_id: Optional[str] = None,
        agent_id: Optional[str] = None,
        namespace: Optional[str] = None,
        timeout: float = 30.0,
        headers: Optional[Dict[str, str]] = None,
    ) -> SpectorClientBuilder:
        self._config.base_url = base_url
        self._config.api_key = api_key
        self._config.bearer_token = bearer_token
        self._config.user_id = user_id
        self._config.agent_id = agent_id
        self._config.namespace = namespace
        self._config.read_timeout = timeout
        if headers:
            self._config.headers.update(headers)
        return self

    def with_transport(self, transport: BaseTransport) -> SpectorClientBuilder:
        self._custom_transport = transport
        return self

    def build(self) -> SpectorClient:
        transport = self._custom_transport or RestTransport(self._config)
        return SpectorClient(transport=transport)


class AsyncSpectorClientBuilder:
    """Fluent builder for asynchronous AsyncSpectorClient."""

    def __init__(self):
        self._config = ClientConfig()
        self._custom_transport: Optional[AsyncBaseTransport] = None

    def with_rest(
        self,
        base_url: str = "http://localhost:7070",
        api_key: Optional[str] = None,
        bearer_token: Optional[str] = None,
        user_id: Optional[str] = None,
        agent_id: Optional[str] = None,
        namespace: Optional[str] = None,
        timeout: float = 30.0,
        headers: Optional[Dict[str, str]] = None,
    ) -> AsyncSpectorClientBuilder:
        self._config.base_url = base_url
        self._config.api_key = api_key
        self._config.bearer_token = bearer_token
        self._config.user_id = user_id
        self._config.agent_id = agent_id
        self._config.namespace = namespace
        self._config.read_timeout = timeout
        if headers:
            self._config.headers.update(headers)
        return self

    def with_transport(self, transport: AsyncBaseTransport) -> AsyncSpectorClientBuilder:
        self._custom_transport = transport
        return self

    def build(self) -> AsyncSpectorClient:
        transport = self._custom_transport or AsyncRestTransport(self._config)
        return AsyncSpectorClient(transport=transport)
