# Copyright 2026 Spectrayan — Apache 2.0
"""
Server-Sent Events (SSE) streaming client for real-time memory and cognitive events.
"""

from __future__ import annotations
from typing import AsyncIterator, Iterator, List, Optional, Union

from spector_client.models import SseEvent
from spector_client.transports.base import BaseTransport, AsyncBaseTransport


class EventClient:
    """Synchronous client for real-time Server-Sent Events (SSE)."""

    def __init__(self, transport: BaseTransport):
        self._transport = transport

    def stream(self, topics: Optional[Union[str, List[str]]] = None) -> Iterator[SseEvent]:
        """Streams Server-Sent Events from the Spector Synapse event stream.

        Args:
            topics: Comma-separated string or list of topics to filter (e.g. ['memory', 'cortex']).

        Yields:
            SseEvent instances containing parsed data payloads.
        """
        filter_param: Optional[str] = None
        if isinstance(topics, list):
            filter_param = ",".join(topics)
        elif isinstance(topics, str):
            filter_param = topics

        params = {"filter": filter_param} if filter_param else {}
        return self._transport.stream_events("/api/v1/events", query_params=params)


class AsyncEventClient:
    """Asynchronous client for real-time Server-Sent Events (SSE)."""

    def __init__(self, transport: AsyncBaseTransport):
        self._transport = transport

    def stream(self, topics: Optional[Union[str, List[str]]] = None) -> AsyncIterator[SseEvent]:
        """Asynchronously streams Server-Sent Events from the Spector Synapse event stream."""
        filter_param: Optional[str] = None
        if isinstance(topics, list):
            filter_param = ",".join(topics)
        elif isinstance(topics, str):
            filter_param = topics

        params = {"filter": filter_param} if filter_param else {}
        return self._transport.stream_events("/api/v1/events", query_params=params)
