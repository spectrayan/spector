# Copyright 2026 Spectrayan — Apache 2.0
"""
MCP JSON-RPC 2.0 transport over HTTP.
"""

from __future__ import annotations
import json
import urllib.request
from typing import Any, Dict, Optional

from spector_client.config import ClientConfig
from spector_client.exceptions import TransportError


class McpHttpTransport:
    """Communicates with Spector's MCP Server over HTTP JSON-RPC 2.0."""

    def __init__(self, endpoint_url: str, config: Optional[ClientConfig] = None):
        self.endpoint_url = endpoint_url
        self.config = config or ClientConfig()
        self._request_id = 0

    def call_tool(self, tool_name: str, arguments: Optional[Dict[str, Any]] = None) -> Any:
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

        data = json.dumps(payload).encode("utf-8")
        headers = self.config.get_auth_headers()
        headers["Content-Type"] = "application/json"
        headers["Accept"] = "application/json"

        req = urllib.request.Request(self.endpoint_url, data=data, headers=headers, method="POST")

        try:
            with urllib.request.urlopen(req, timeout=self.config.read_timeout) as resp:
                resp_data = json.loads(resp.read().decode("utf-8"))
                if "error" in resp_data:
                    err = resp_data["error"]
                    raise TransportError(f"MCP error [{err.get('code')}]: {err.get('message')}")
                return resp_data.get("result", {})
        except Exception as e:
            raise TransportError(f"Failed to execute MCP tool '{tool_name}' over HTTP: {e}")

    def close(self) -> None:
        pass
