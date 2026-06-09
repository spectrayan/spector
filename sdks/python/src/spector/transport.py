# Copyright 2026 Spectrayan — Apache 2.0
"""
JSON-RPC 2.0 transport over stdio for communicating with the Spector MCP server.

Spawns the JVM process, writes JSON-RPC requests to stdin, reads responses
from stdout. Thread-safe via a reentrant lock on the I/O streams.
"""

from __future__ import annotations

import json
import logging
import subprocess
import sys
import threading
from typing import Any, Optional

logger = logging.getLogger("spector.transport")


class TransportError(Exception):
    """Raised when the MCP transport encounters an error."""


class ToolError(Exception):
    """Raised when an MCP tool call returns an error result."""


class StdioTransport:
    """JSON-RPC 2.0 transport over subprocess stdin/stdout.

    The Spector MCP server communicates via JSON-RPC 2.0 over stdio:
    - Requests are written to the process's **stdin** (one JSON line per request)
    - Responses are read from the process's **stdout** (one JSON line per response)
    - All logging goes to **stderr** (captured and forwarded to Python logging)

    Thread-safety: All I/O is serialized via a reentrant lock. Multiple
    threads can safely call ``send()`` concurrently.
    """

    def __init__(
        self,
        jar_path: str,
        config_path: Optional[str] = None,
        java_bin: str = "java",
        extra_jvm_args: Optional[list[str]] = None,
    ):
        self._jar_path = jar_path
        self._config_path = config_path
        self._java_bin = java_bin
        self._extra_jvm_args = extra_jvm_args or []
        self._process: Optional[subprocess.Popen] = None
        self._request_id = 0
        self._lock = threading.RLock()
        self._stderr_thread: Optional[threading.Thread] = None

    # ── Lifecycle ──

    def start(self) -> None:
        """Spawns the JVM process and performs MCP initialization handshake."""
        cmd = [
            self._java_bin,
            "--add-modules", "jdk.incubator.vector",
            "--enable-native-access=ALL-UNNAMED",
            "--enable-preview",
            *self._extra_jvm_args,
            "-jar", self._jar_path,
        ]
        if self._config_path:
            cmd.extend(["--config", self._config_path])

        logger.info("Starting Spector MCP server: %s", " ".join(cmd))

        self._process = subprocess.Popen(
            cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            bufsize=0,  # unbuffered
        )

        # Capture stderr in a background thread
        self._stderr_thread = threading.Thread(
            target=self._drain_stderr, daemon=True, name="spector-stderr"
        )
        self._stderr_thread.start()

        # MCP initialization handshake
        self._initialize()

    def stop(self) -> None:
        """Terminates the JVM process."""
        if self._process and self._process.poll() is None:
            logger.info("Stopping Spector MCP server (PID=%d)", self._process.pid)
            try:
                self._process.stdin.close()
            except Exception:
                pass
            try:
                self._process.terminate()
                self._process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self._process.kill()
                self._process.wait()
            logger.info("Spector MCP server stopped")
        self._process = None

    @property
    def is_running(self) -> bool:
        """Returns True if the JVM process is alive."""
        return self._process is not None and self._process.poll() is None

    # ── MCP Protocol ──

    def _initialize(self) -> dict:
        """Performs the MCP initialize + initialized handshake."""
        result = self.send("initialize", {
            "protocolVersion": "2025-03-26",
            "capabilities": {},
            "clientInfo": {"name": "spector-python-sdk", "version": "0.1.0"},
        })

        # Send initialized notification (no response expected)
        self._write_message({
            "jsonrpc": "2.0",
            "method": "notifications/initialized",
        })

        logger.info(
            "MCP initialized: server=%s, version=%s",
            result.get("serverInfo", {}).get("name", "unknown"),
            result.get("serverInfo", {}).get("version", "unknown"),
        )
        return result

    def send(self, method: str, params: Optional[dict] = None) -> Any:
        """Sends a JSON-RPC 2.0 request and waits for the response.

        Args:
            method: The RPC method name (e.g., "tools/call").
            params: Optional parameters dict.

        Returns:
            The ``result`` field from the JSON-RPC response.

        Raises:
            TransportError: If the process is not running or I/O fails.
            ToolError: If the response contains an error.
        """
        with self._lock:
            self._request_id += 1
            req_id = self._request_id

            message: dict[str, Any] = {
                "jsonrpc": "2.0",
                "id": req_id,
                "method": method,
            }
            if params is not None:
                message["params"] = params

            self._write_message(message)
            return self._read_response(req_id)

    def call_tool(self, name: str, arguments: Optional[dict] = None) -> str:
        """Calls an MCP tool and returns the text content.

        This is the primary method for tool invocation. It handles the
        ``tools/call`` protocol and extracts the text content from the response.

        Args:
            name: Tool name (e.g., "memory_remember").
            arguments: Tool arguments dict.

        Returns:
            The text content from the tool's response.

        Raises:
            ToolError: If the tool returns an error or ``isError=True``.
        """
        result = self.send("tools/call", {
            "name": name,
            "arguments": arguments or {},
        })

        # Extract text content from MCP tool result
        content_list = result.get("content", [])
        if not content_list:
            return ""

        # Check for isError flag
        if result.get("isError", False):
            text = content_list[0].get("text", "Unknown tool error")
            raise ToolError(f"Tool '{name}' returned error: {text}")

        # Concatenate all text content blocks
        texts = [c.get("text", "") for c in content_list if c.get("type") == "text"]
        return "\n".join(texts)

    def list_tools(self) -> list[dict]:
        """Lists all available MCP tools.

        Returns:
            List of tool descriptors with ``name``, ``description``, and ``inputSchema``.
        """
        result = self.send("tools/list", {})
        return result.get("tools", [])

    # ── Internal I/O ──

    def _write_message(self, message: dict) -> None:
        """Writes a JSON-RPC message to the process's stdin."""
        if not self.is_running:
            raise TransportError("Spector MCP server is not running")

        try:
            line = json.dumps(message, separators=(",", ":")) + "\n"
            self._process.stdin.write(line.encode("utf-8"))
            self._process.stdin.flush()
            logger.debug("→ %s", line.strip())
        except (BrokenPipeError, OSError) as e:
            raise TransportError(f"Failed to write to MCP server: {e}") from e

    def _read_response(self, expected_id: int) -> Any:
        """Reads a JSON-RPC response for the given request ID."""
        if not self.is_running:
            raise TransportError("Spector MCP server is not running")

        try:
            while True:
                line = self._process.stdout.readline()
                if not line:
                    raise TransportError("MCP server closed stdout unexpectedly")

                line_str = line.decode("utf-8").strip()
                if not line_str:
                    continue

                logger.debug("← %s", line_str[:500])

                try:
                    response = json.loads(line_str)
                except json.JSONDecodeError as e:
                    logger.warning("Non-JSON line from MCP server: %s", line_str[:200])
                    continue

                # Skip notifications (no "id" field)
                if "id" not in response:
                    continue

                if response["id"] != expected_id:
                    logger.warning(
                        "Unexpected response ID: got %s, expected %d",
                        response["id"], expected_id,
                    )
                    continue

                if "error" in response:
                    err = response["error"]
                    raise ToolError(
                        f"JSON-RPC error {err.get('code', -1)}: {err.get('message', 'unknown')}"
                    )

                return response.get("result", {})

        except (OSError, ValueError) as e:
            raise TransportError(f"Failed to read from MCP server: {e}") from e

    def _drain_stderr(self) -> None:
        """Background thread that reads stderr and logs it."""
        try:
            while self._process and self._process.poll() is None:
                line = self._process.stderr.readline()
                if not line:
                    break
                text = line.decode("utf-8", errors="replace").rstrip()
                if text:
                    logger.debug("[JVM] %s", text)
        except Exception:
            pass
