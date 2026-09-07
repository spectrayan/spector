# Copyright 2026 Spectrayan — Apache 2.0
"""
Asynchronous REST transport using Python's standard library asyncio (with optional httpx acceleration).
"""

from __future__ import annotations
import asyncio
from typing import Any, AsyncIterator, Dict, Optional

from spector_client.config import ClientConfig
from spector_client.models import SseEvent
from spector_client.transports.base import AsyncBaseTransport
from spector_client.transports.rest import RestTransport


class AsyncRestTransport(AsyncBaseTransport):
    """Asynchronous HTTP REST transport for Spector Synapse."""

    def __init__(self, config: ClientConfig):
        self.config = config
        self._sync_transport = RestTransport(config)

    async def request(
        self,
        method: str,
        path: str,
        query_params: Optional[Dict[str, Any]] = None,
        body: Optional[Any] = None,
    ) -> Any:
        return await asyncio.to_thread(
            self._sync_transport.request,
            method=method,
            path=path,
            query_params=query_params,
            body=body,
        )

    async def stream_events(
        self,
        path: str,
        query_params: Optional[Dict[str, Any]] = None,
    ) -> AsyncIterator[SseEvent]:
        # Generator bridge to run blocking stream in worker thread and yield through async queue
        queue: asyncio.Queue[Optional[SseEvent]] = asyncio.Queue()
        error_holder: list[Exception] = []

        def worker():
            try:
                for event in self._sync_transport.stream_events(path, query_params):
                    asyncio.run_coroutine_threadsafe(queue.put(event), loop).result()
            except Exception as e:
                error_holder.append(e)
            finally:
                asyncio.run_coroutine_threadsafe(queue.put(None), loop).result()

        loop = asyncio.get_running_loop()
        worker_task = asyncio.to_thread(worker)

        # Start consumer
        try:
            while True:
                item = await queue.get()
                if item is None:
                    break
                yield item
            if error_holder:
                raise error_holder[0]
        finally:
            await worker_task

    async def close(self) -> None:
        await asyncio.to_thread(self._sync_transport.close)
