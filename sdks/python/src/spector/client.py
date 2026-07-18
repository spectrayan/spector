# Copyright 2026 Spectrayan — Apache 2.0
"""
Main entry point for the Spector Python SDK.

``SpectorClient`` manages the JVM process lifecycle and provides access to
the ``MemoryClient`` and ``EngineClient`` sub-clients.
"""

from __future__ import annotations

import logging
from typing import Optional

from spector.engine import EngineClient
from spector.memory import MemoryClient
from spector.transport import StdioTransport

logger = logging.getLogger("spector")


class SpectorClient:
    """Main client for the Spector AI Memory & Search platform.

    Spawns a Spector MCP server as a subprocess and communicates via
    JSON-RPC 2.0 over stdio. Use as a context manager for clean lifecycle::

        with SpectorClient(
            jar_path="/path/to/spector.jar",
            config_path="/path/to/spector.yml",
        ) as client:
            # Memory operations
            mem_id = client.memory.remember("User prefers dark mode",
                                            tags=["preferences"])
            results = client.memory.recall("user preferences")
            record = client.memory.inspect(mem_id)
            export = client.memory.export_json()

            # Search operations
            hits = client.engine.search("SIMD acceleration")
            context = client.engine.rag("How does quantization work?")

    Args:
        jar_path: Absolute path to the ``spector.jar`` distribution.
        config_path: Optional path to ``spector.yml`` config file.
        java_bin: Java binary (default: ``"java"``). Must be JDK 25+.
        extra_jvm_args: Additional JVM arguments (e.g., ``["-Xmx512m"]``).
    """

    def __init__(
        self,
        jar_path: str,
        config_path: Optional[str] = None,
        java_bin: str = "java",
        extra_jvm_args: Optional[list[str]] = None,
    ):
        self._transport = StdioTransport(
            jar_path=jar_path,
            config_path=config_path,
            java_bin=java_bin,
            extra_jvm_args=extra_jvm_args,
        )
        self._memory: Optional[MemoryClient] = None
        self._engine: Optional[EngineClient] = None

    # ── Lifecycle ──

    def start(self) -> SpectorClient:
        """Starts the Spector MCP server subprocess.

        This is called automatically when using the context manager.
        """
        self._transport.start()
        self._memory = MemoryClient(self._transport)
        self._engine = EngineClient(self._transport)
        logger.info("SpectorClient started")
        return self

    def stop(self) -> None:
        """Stops the Spector MCP server subprocess.

        This is called automatically when exiting the context manager.
        """
        self._transport.stop()
        self._memory = None
        self._engine = None
        logger.info("SpectorClient stopped")

    @property
    def is_running(self) -> bool:
        """Returns True if the MCP server is running."""
        return self._transport.is_running

    # ── Sub-clients ──

    @property
    def memory(self) -> MemoryClient:
        """Access cognitive memory operations.

        Returns:
            The ``MemoryClient`` for ``memory_*`` tools.

        Raises:
            RuntimeError: If the client is not started.
        """
        if self._memory is None:
            raise RuntimeError("SpectorClient is not started. Call start() or use 'with' context manager.")
        return self._memory

    @property
    def engine(self) -> EngineClient:
        """Access search engine operations.

        Returns:
            The ``EngineClient`` for ``engine_*`` tools.

        Raises:
            RuntimeError: If the client is not started.
        """
        if self._engine is None:
            raise RuntimeError("SpectorClient is not started. Call start() or use 'with' context manager.")
        return self._engine

    # ── Raw MCP access ──

    def call_tool(self, name: str, arguments: Optional[dict] = None) -> str:
        """Calls any MCP tool by name (low-level access).

        Use this for custom or future tools not covered by the
        ``memory`` and ``engine`` sub-clients.

        Args:
            name: MCP tool name (e.g., ``"memory_remember"``).
            arguments: Tool arguments dict.

        Returns:
            The text content from the tool response.
        """
        return self._transport.call_tool(name, arguments)

    def list_tools(self) -> list[dict]:
        """Lists all available MCP tools.

        Returns:
            List of tool descriptors from the server.
        """
        return self._transport.list_tools()

    # ── Context Manager ──

    def __enter__(self) -> SpectorClient:
        return self.start()

    def __exit__(self, exc_type, exc_val, exc_tb) -> None:
        self.stop()

    def __repr__(self) -> str:
        status = "running" if self.is_running else "stopped"
        return f"SpectorClient(status={status})"
