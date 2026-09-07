# Copyright 2026 Spectrayan — Apache 2.0
"""
Configuration settings for the Spector Client SDK.
"""

from __future__ import annotations
from dataclasses import dataclass, field
from typing import Dict, Optional


@dataclass
class ClientConfig:
    """Client connection and execution options."""
    base_url: str = "http://localhost:7070"
    api_key: Optional[str] = None
    bearer_token: Optional[str] = None
    user_id: Optional[str] = None
    agent_id: Optional[str] = None
    namespace: Optional[str] = None
    connect_timeout: float = 10.0
    read_timeout: float = 30.0
    headers: Dict[str, str] = field(default_factory=dict)

    def get_auth_headers(self) -> Dict[str, str]:
        """Returns standard authentication headers based on configured credentials."""
        h = dict(self.headers)
        if self.api_key:
            h["X-API-Key"] = self.api_key
        if self.bearer_token:
            h["Authorization"] = f"Bearer {self.bearer_token}"
        if self.user_id:
            h["X-User-Id"] = self.user_id
        if self.agent_id:
            h["X-Agent-Id"] = self.agent_id
        if self.namespace:
            h["X-Namespace"] = self.namespace
        return h
