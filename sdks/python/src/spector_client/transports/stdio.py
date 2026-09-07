# Copyright 2026 Spectrayan — Apache 2.0
"""
Subprocess stdio transport executing spector.jar via JSON-RPC 2.0.
"""

from __future__ import annotations
import json
import logging
import subprocess
import threading
from typing import Any, Dict, Optional

from spector_client.exceptions import TransportError

logger = logging.getLogger("spector_client.transport.stdio")


class StdioTransport:
    """Spawns spector.jar and communicates via JSON-RPC 2.0 over stdio."""

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

    def start(self) -> None:
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

        logger.info("Starting Spector JAR subprocess: %s", " ".join(cmd))

        self._process = subprocess.Popen(
            cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            bufsize=0,
        )

        self._stderr_thread = threading.Thread(
            target=self._drain_stderr, daemon=True, name="spector-stderr"
        )
        self._stderr_thread.start()

    def _drain_stderr(self) -> None:
        if not self._process or not self._process.stderr:
            return
        try:
            for line in iter(self._process.stderr.readline, b""):
                text = line.decode("utf-8", errors="replace").strip()
                if text:
                    logger.debug("[Spector-JVM] %s", text)
        except Exception:
            pass

    def call_tool(self, tool_name: str, arguments: Optional[Dict[str, Any]] = None) -> Any:
        if not self._process or self._process.poll() is not None:
            raise TransportError("Spector subprocess is not running")

        with self._lock:
            self._request_id += 1
            payload = {
                "jsonrpc": "2.0",
                "id": self._request_id,
                "method": "tools/call",
                "params": {
                    "name": tool_name,
                    "arguments": arguments or {},
                },
            }

            req_bytes = (json.dumps(payload) + "\n").encode("utf-8")
            try:
                self._process.stdin.write(req_bytes)
                self._process.stdin.flush()

                resp_line = self._process.stdout.readline()
                if not resp_line:
                    raise TransportError("Empty response from Spector subprocess")

                resp_data = json.loads(resp_line.decode("utf-8"))
                if "error" in resp_data:
                    err = resp_data["error"]
                    raise TransportError(f"MCP error [{err.get('code')}]: {err.get('message')}")
                return resp_data.get("result", {})
            except Exception as e:
                raise TransportError(f"Failed to communicate with Spector subprocess: {e}")

    def close(self) -> None:
        if self._process and self._process.poll() is None:
            try:
                self._process.stdin.close()
            except Exception:
                pass
            try:
                self._process.terminate()
                self._process.wait(timeout=3)
            except Exception:
                if self._process.poll() is None:
                    self._process.kill()
