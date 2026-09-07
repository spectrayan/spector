# Copyright 2026 Spectrayan — Apache 2.0
"""
Base transport protocol and interface for Spector Client.
"""

from __future__ import annotations
from abc import ABC, abstractmethod
from typing import Any, Dict, Iterator, Optional, AsyncIterator

from spector_client.models import SseEvent


class BaseTransport(ABC):
    """Abstract synchronous transport."""

    @abstractmethod
    def request(
        self,
        method: str,
        path: str,
        query_params: Optional[Dict[str, Any]] = None,
        body: Optional[Any] = None,
    ) -> Any:
        """Executes an HTTP request and returns parsed JSON response."""
        pass

    @abstractmethod
    def stream_events(
        self,
        path: str,
        query_params: Optional[Dict[str, Any]] = None,
    ) -> Iterator[SseEvent]:
        """Streams Server-Sent Events from the endpoint."""
        pass

    @abstractmethod
    def close(self) -> None:
        """Closes any open resources or connections."""
        pass


class AsyncBaseTransport(ABC):
    """Abstract asynchronous transport."""

    @abstractmethod
    async def request(
        self,
        method: str,
        path: str,
        query_params: Optional[Dict[str, Any]] = None,
        body: Optional[Any] = None,
    ) -> Any:
        """Asynchronously executes an HTTP request and returns parsed JSON response."""
        pass

    @abstractmethod
    def stream_events(
        self,
        path: str,
        query_params: Optional[Dict[str, Any]] = None,
    ) -> AsyncIterator[SseEvent]:
        """Asynchronously streams Server-Sent Events from the endpoint."""
        pass

    @abstractmethod
    async def close(self) -> None:
        """Closes any open resources or connections asynchronously."""
        pass
