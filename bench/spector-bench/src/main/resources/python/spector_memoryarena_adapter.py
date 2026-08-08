#!/usr/bin/env python3
# Copyright 2026 Spectrayan
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
Spector MemoryArena Bridge Adapter.

Subclasses MemoryArena's standard BaseMemoryBackend interface to connect
Python agent benchmark runs directly to Spector MCP Server via stdio JSON-RPC.
"""

import json
import subprocess
import sys
from typing import Any, Dict, List, Optional


class SpectorMemoryArenaAdapter:
    """
    Python adapter for MemoryArena (ICML 2026) connecting to Spector MCP Server.
    
    Provides standard lifecycle methods:
      - remember(text, metadata) -> calls spector MCP memory_remember
      - recall(query, k) -> calls spector MCP memory_recall
      - reinforce(memory_id, valence) -> calls spector MCP memory_reinforce
      - forget(memory_id) -> calls spector MCP memory_forget
    """

    def __init__(self, mcp_jar_path: str = "synapse/spector-mcp/target/spector-mcp-1.0.0-SNAPSHOT.jar"):
        self.mcp_jar_path = mcp_jar_path
        self.process = None

    def start(self) -> None:
        """Starts the Spector Java MCP Server process via stdio transport."""
        cmd = [
            "java",
            "--add-modules", "jdk.incubator.vector",
            "-jar", self.mcp_jar_path
        ]
        self.process = subprocess.Popen(
            cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )

    def stop(self) -> None:
        """Terminates the Spector MCP Server process."""
        if self.process:
            self.process.terminate()
            self.process.wait()
            self.process = None

    def _call_tool(self, tool_name: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
        """Internal helper to execute JSON-RPC request to Spector MCP Server."""
        if not self.process:
            raise RuntimeError("Spector MCP Server process is not running. Call start() first.")
        
        request = {
            "jsonrpc": "2.0",
            "id": 1,
            "method": "tools/call",
            "params": {
                "name": tool_name,
                "arguments": arguments
            }
        }
        self.process.stdin.write(json.dumps(request) + "\n")
        self.process.stdin.flush()
        
        response_line = self.process.stdout.readline()
        if not response_line:
            raise RuntimeError("Empty response from Spector MCP Server.")
        return json.loads(response_line)

    def remember(self, text: str, importance: float = 0.5, valence: float = 0.0) -> Dict[str, Any]:
        """Stores experience into Spector memory."""
        return self._call_tool("memory_remember", {
            "text": text,
            "importance": importance,
            "interest": valence
        })

    def recall(self, query: str, top_k: int = 5) -> List[Dict[str, Any]]:
        """Recalls relevant memories from Spector memory."""
        result = self._call_tool("memory_recall", {
            "query": query
        })
        return result.get("result", {}).get("memories", [])

    def reinforce(self, memory_id: str, valence: float) -> Dict[str, Any]:
        """Applies Hebbian valence reinforcement to a memory."""
        return self._call_tool("memory_reinforce", {
            "id": memory_id,
            "valence": valence
        })

    def forget(self, memory_id: str) -> Dict[str, Any]:
        """Suppresses/forgets a specific memory in Spector engine."""
        return self._call_tool("memory_suppress", {
            "id": memory_id
        })
