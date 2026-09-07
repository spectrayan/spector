# Copyright 2026 Spectrayan — Apache 2.0
"""
Ergonomic client interfaces for Spector Cognitive Memory operations.
"""

from __future__ import annotations
from typing import Any, Dict, List, Optional

from spector_client.exceptions import MemoryNotFoundError
from spector_client.models import (
    MemoryRecord,
    MemoryStatus,
    MemoryTier,
    RecallRecord,
    SearchRecord,
)
from spector_client.transports.base import BaseTransport, AsyncBaseTransport


class MemoryClient:
    """Synchronous facade for Spector Cognitive Memory operations."""

    def __init__(self, transport: BaseTransport):
        self._transport = transport

    def remember(
        self,
        text: str,
        tier: str | MemoryTier = MemoryTier.SEMANTIC,
        tags: Optional[List[str]] = None,
        interest: float = 0.0,
        urgency: float = 0.0,
        challenge: float = 0.0,
        valence: int = 0,
        arousal: int = 0,
        metadata: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """Stores a memory with cognitive scoring hints."""
        tier_str = tier.value if isinstance(tier, MemoryTier) else str(tier)
        body = {
            "text": text,
            "tier": tier_str,
            "tags": ",".join(tags) if tags else None,
            "interest": interest,
            "urgency": urgency,
            "challenge": challenge,
            "valence": valence,
            "arousal": arousal,
            "metadata": metadata or {},
        }
        return self._transport.request("POST", "/api/v1/memory/remember", body=body)

    def store(self, text: str, tags: Optional[List[str]] = None) -> Dict[str, Any]:
        """Stores a memory synchronously, returning the assigned memory ID."""
        body = {
            "text": text,
            "tags": tags or [],
        }
        return self._transport.request("POST", "/api/v1/memory/store", body=body)

    def recall(
        self,
        query: str,
        top_k: int = 5,
        profile: str = "BALANCED",
        min_salience: float = 0.0,
        tags: Optional[List[str]] = None,
    ) -> List[RecallRecord]:
        """Recalls memories using fused cognitive scoring (similarity + Hebbian + temporal)."""
        body = {
            "query": query,
            "topK": top_k,
            "profile": profile,
            "minSalience": min_salience,
            "tags": tags or [],
        }
        res = self._transport.request("POST", "/api/v1/memory/recall", body=body)
        items = res
        if isinstance(res, dict):
            items = res.get("results") or res.get("memories") or res.get("data") or []
        records: List[RecallRecord] = []
        if isinstance(items, list):
            for item in items:
                tier_val = item.get("tier", "SEMANTIC")
                score_val = item.get("score") if item.get("score") is not None else item.get("cognitiveScore", 0.0)
                age_days_val = item.get("ageDays") if item.get("ageDays") is not None else item.get("age_days", 0.0)
                decay_factor_val = item.get("decayFactor") if item.get("decayFactor") is not None else item.get("decay_factor", 1.0)
                records.append(
                    RecallRecord(
                        id=str(item.get("id", "")),
                        text=item.get("text", ""),
                        score=float(score_val or 0.0),
                        tier=MemoryTier(tier_val) if tier_val in MemoryTier.__members__ else MemoryTier.SEMANTIC,
                        tags=item.get("tags") or [],
                        valence=int(item.get("valence", 0)),
                        arousal=int(item.get("arousal", 0)),
                        importance=float(item.get("importance", 0.0)),
                        metadata=item.get("metadata") or {},
                        similarity=float(item.get("similarity", 0.0)),
                        age_days=float(age_days_val or 0.0),
                        decay_factor=float(decay_factor_val or 1.0),
                    )
                )
        return records

    def search(self, query: str, top_k: int = 5) -> List[SearchRecord]:
        """Performs pure semantic vector similarity search."""
        body = {"query": query, "topK": top_k}
        res = self._transport.request("POST", "/api/v1/memory/search", body=body)
        records: List[SearchRecord] = []
        if isinstance(res, list):
            for item in res:
                records.append(
                    SearchRecord(
                        id=str(item.get("id", "")),
                        text=item.get("text", ""),
                        score=float(item.get("score", 0.0)),
                        tags=item.get("tags") or [],
                        metadata=item.get("metadata") or {},
                    )
                )
        return records

    def get(self, memory_id: str) -> MemoryRecord:
        """Retrieves a memory by ID. Raises MemoryNotFoundError if not found."""
        res = self._transport.request("GET", f"/api/v1/memory/{memory_id}")
        tier_val = res.get("tier", "SEMANTIC")
        return MemoryRecord(
            id=str(res.get("id", memory_id)),
            text=res.get("text", ""),
            tier=MemoryTier(tier_val) if tier_val in MemoryTier.__members__ else MemoryTier.SEMANTIC,
            tags=res.get("tags") or [],
            valence=int(res.get("valence", 0)),
            arousal=int(res.get("arousal", 0)),
            importance=float(res.get("importance", 0.0)),
            decay_factor=float(res.get("decayFactor", 1.0)),
            recall_count=int(res.get("recallCount", 0)),
            resolved=bool(res.get("resolved", False)),
            tombstoned=bool(res.get("tombstoned", False)),
            metadata=res.get("metadata") or {},
            created_at=res.get("createdAt"),
            updated_at=res.get("updatedAt"),
        )

    def find(self, memory_id: str) -> Optional[MemoryRecord]:
        """Finds a memory by ID, returning None if not found."""
        try:
            return self.get(memory_id)
        except MemoryNotFoundError:
            return None

    def update(
        self,
        memory_id: str,
        text: Optional[str] = None,
        tags: Optional[List[str]] = None,
        metadata: Optional[Dict[str, Any]] = None,
    ) -> None:
        """Updates an existing memory's content or tags."""
        body: Dict[str, Any] = {}
        if text is not None:
            body["text"] = text
        if tags is not None:
            body["tags"] = tags
        if metadata is not None:
            body["metadata"] = metadata
        self._transport.request("PUT", f"/api/v1/memory/{memory_id}", body=body)

    def forget(self, memory_id: str, reason: Optional[str] = None) -> None:
        """Tombstones and marks a memory as forgotten."""
        body = {"reason": reason} if reason else None
        self._transport.request("DELETE", f"/api/v1/memory/{memory_id}", body=body)

    def reinforce(self, memory_id: str, valence: int = 1) -> None:
        """Reinforces a memory via Long-Term Potentiation (LTP)."""
        body = {"valence": valence}
        self._transport.request("POST", f"/api/v1/memory/{memory_id}/reinforce", body=body)

    def suppress(self, memory_id: str, reason: Optional[str] = None) -> None:
        """Suppresses a memory from active recall consideration."""
        body = {"action": "suppress", "reason": reason}
        self._transport.request("POST", f"/api/v1/memory/{memory_id}/suppress", body=body)

    def unsuppress(self, memory_id: str) -> None:
        """Restores a previously suppressed memory to recall consideration."""
        body = {"action": "unsuppress"}
        self._transport.request("POST", f"/api/v1/memory/{memory_id}/suppress", body=body)

    def resolve(self, memory_id: str) -> None:
        """Resolves a memory (Zeigarnik task completion)."""
        body = {"resolved": True}
        self._transport.request("POST", f"/api/v1/memory/{memory_id}/resolve", body=body)

    def unresolve(self, memory_id: str) -> None:
        """Reopens active tension on a memory."""
        body = {"resolved": False}
        self._transport.request("POST", f"/api/v1/memory/{memory_id}/resolve", body=body)

    def status(self) -> MemoryStatus:
        """Retrieves memory status, tier counts, and index dimensions."""
        res = self._transport.request("GET", "/api/v1/memory/status")
        return MemoryStatus(
            total_memories=int(res.get("totalMemories", 0)),
            working_count=int(res.get("workingCount", 0)),
            episodic_count=int(res.get("episodicCount", 0)),
            semantic_count=int(res.get("semanticCount", 0)),
            procedural_count=int(res.get("proceduralCount", 0)),
            tombstone_count=int(res.get("tombstoneCount", 0)),
            dimensions=int(res.get("dimensions", 384)),
            persistence_mode=str(res.get("persistenceMode", "OFF")),
            extra=res,
        )

    def stats(self) -> Dict[str, Any]:
        """Retrieves system health and performance statistics."""
        return self._transport.request("GET", "/api/v1/memory/stats")

    def browse(self, tags: Optional[List[str]] = None) -> List[Dict[str, Any]]:
        """Browses memories using tag-based exact matching."""
        body = {"tags": tags or []}
        return self._transport.request("POST", "/api/v1/memory/browse", body=body)

    def table(
        self,
        page: int = 0,
        page_size: int = 20,
        tier: Optional[str] = None,
        tombstoned: bool = False,
    ) -> Dict[str, Any]:
        """Retrieves paginated memory table records."""
        params = {
            "page": page,
            "pageSize": page_size,
            "tier": tier,
            "tombstoned": str(tombstoned).lower(),
        }
        return self._transport.request("GET", "/api/v1/memory/table", query_params=params)

    def vector(self, memory_id: str) -> List[int]:
        """Retrieves quantized embedding vector for a memory."""
        res = self._transport.request("GET", f"/api/v1/memory/{memory_id}/vector")
        return res.get("vector", [])

    def consolidate(self) -> None:
        """Manually triggers memory consolidation (circadian sweep)."""
        self._transport.request("POST", "/api/v1/memory/consolidate")

    def vacuum(self, tier: Optional[str] = None) -> Dict[str, Any]:
        """Triggers vacuum compaction for a tier or all tiers."""
        body = {"tier": tier} if tier else {}
        return self._transport.request("POST", "/api/v1/memory/vacuum", body=body)


class AsyncMemoryClient:
    """Asynchronous facade for Spector Cognitive Memory operations."""

    def __init__(self, transport: AsyncBaseTransport):
        self._transport = transport

    async def remember(
        self,
        text: str,
        tier: str | MemoryTier = MemoryTier.SEMANTIC,
        tags: Optional[List[str]] = None,
        interest: float = 0.0,
        urgency: float = 0.0,
        challenge: float = 0.0,
        valence: int = 0,
        arousal: int = 0,
        metadata: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        tier_str = tier.value if isinstance(tier, MemoryTier) else str(tier)
        body = {
            "text": text,
            "tier": tier_str,
            "tags": ",".join(tags) if tags else None,
            "interest": interest,
            "urgency": urgency,
            "challenge": challenge,
            "valence": valence,
            "arousal": arousal,
            "metadata": metadata or {},
        }
        return await self._transport.request("POST", "/api/v1/memory/remember", body=body)

    async def store(self, text: str, tags: Optional[List[str]] = None) -> Dict[str, Any]:
        body = {"text": text, "tags": tags or []}
        return await self._transport.request("POST", "/api/v1/memory/store", body=body)

    async def recall(
        self,
        query: str,
        top_k: int = 5,
        profile: str = "BALANCED",
        min_salience: float = 0.0,
        tags: Optional[List[str]] = None,
    ) -> List[RecallRecord]:
        body = {
            "query": query,
            "topK": top_k,
            "profile": profile,
            "minSalience": min_salience,
            "tags": tags or [],
        }
        res = await self._transport.request("POST", "/api/v1/memory/recall", body=body)
        items = res
        if isinstance(res, dict):
            items = res.get("results") or res.get("memories") or res.get("data") or []
        records: List[RecallRecord] = []
        if isinstance(items, list):
            for item in items:
                tier_val = item.get("tier", "SEMANTIC")
                score_val = item.get("score") if item.get("score") is not None else item.get("cognitiveScore", 0.0)
                age_days_val = item.get("ageDays") if item.get("ageDays") is not None else item.get("age_days", 0.0)
                decay_factor_val = item.get("decayFactor") if item.get("decayFactor") is not None else item.get("decay_factor", 1.0)
                records.append(
                    RecallRecord(
                        id=str(item.get("id", "")),
                        text=item.get("text", ""),
                        score=float(score_val or 0.0),
                        tier=MemoryTier(tier_val) if tier_val in MemoryTier.__members__ else MemoryTier.SEMANTIC,
                        tags=item.get("tags") or [],
                        valence=int(item.get("valence", 0)),
                        arousal=int(item.get("arousal", 0)),
                        importance=float(item.get("importance", 0.0)),
                        metadata=item.get("metadata") or {},
                        similarity=float(item.get("similarity", 0.0)),
                        age_days=float(age_days_val or 0.0),
                        decay_factor=float(decay_factor_val or 1.0),
                    )
                )
        return records

    async def search(self, query: str, top_k: int = 5) -> List[SearchRecord]:
        body = {"query": query, "topK": top_k}
        res = await self._transport.request("POST", "/api/v1/memory/search", body=body)
        records: List[SearchRecord] = []
        if isinstance(res, list):
            for item in res:
                records.append(
                    SearchRecord(
                        id=str(item.get("id", "")),
                        text=item.get("text", ""),
                        score=float(item.get("score", 0.0)),
                        tags=item.get("tags") or [],
                        metadata=item.get("metadata") or {},
                    )
                )
        return records

    async def get(self, memory_id: str) -> MemoryRecord:
        res = await self._transport.request("GET", f"/api/v1/memory/{memory_id}")
        tier_val = res.get("tier", "SEMANTIC")
        return MemoryRecord(
            id=str(res.get("id", memory_id)),
            text=res.get("text", ""),
            tier=MemoryTier(tier_val) if tier_val in MemoryTier.__members__ else MemoryTier.SEMANTIC,
            tags=res.get("tags") or [],
            valence=int(res.get("valence", 0)),
            arousal=int(res.get("arousal", 0)),
            importance=float(res.get("importance", 0.0)),
            decay_factor=float(res.get("decayFactor", 1.0)),
            recall_count=int(res.get("recallCount", 0)),
            resolved=bool(res.get("resolved", False)),
            tombstoned=bool(res.get("tombstoned", False)),
            metadata=res.get("metadata") or {},
            created_at=res.get("createdAt"),
            updated_at=res.get("updatedAt"),
        )

    async def find(self, memory_id: str) -> Optional[MemoryRecord]:
        try:
            return await self.get(memory_id)
        except MemoryNotFoundError:
            return None

    async def update(
        self,
        memory_id: str,
        text: Optional[str] = None,
        tags: Optional[List[str]] = None,
        metadata: Optional[Dict[str, Any]] = None,
    ) -> None:
        body: Dict[str, Any] = {}
        if text is not None:
            body["text"] = text
        if tags is not None:
            body["tags"] = tags
        if metadata is not None:
            body["metadata"] = metadata
        await self._transport.request("PUT", f"/api/v1/memory/{memory_id}", body=body)

    async def forget(self, memory_id: str, reason: Optional[str] = None) -> None:
        body = {"reason": reason} if reason else None
        await self._transport.request("DELETE", f"/api/v1/memory/{memory_id}", body=body)

    async def reinforce(self, memory_id: str, valence: int = 1) -> None:
        body = {"valence": valence}
        await self._transport.request("POST", f"/api/v1/memory/{memory_id}/reinforce", body=body)

    async def suppress(self, memory_id: str, reason: Optional[str] = None) -> None:
        body = {"action": "suppress", "reason": reason}
        await self._transport.request("POST", f"/api/v1/memory/{memory_id}/suppress", body=body)

    async def unsuppress(self, memory_id: str) -> None:
        body = {"action": "unsuppress"}
        await self._transport.request("POST", f"/api/v1/memory/{memory_id}/suppress", body=body)

    async def resolve(self, memory_id: str) -> None:
        body = {"resolved": True}
        await self._transport.request("POST", f"/api/v1/memory/{memory_id}/resolve", body=body)

    async def unresolve(self, memory_id: str) -> None:
        body = {"resolved": False}
        await self._transport.request("POST", f"/api/v1/memory/{memory_id}/resolve", body=body)

    async def status(self) -> MemoryStatus:
        res = await self._transport.request("GET", "/api/v1/memory/status")
        return MemoryStatus(
            total_memories=int(res.get("totalMemories", 0)),
            working_count=int(res.get("workingCount", 0)),
            episodic_count=int(res.get("episodicCount", 0)),
            semantic_count=int(res.get("semanticCount", 0)),
            procedural_count=int(res.get("proceduralCount", 0)),
            tombstone_count=int(res.get("tombstoneCount", 0)),
            dimensions=int(res.get("dimensions", 384)),
            persistence_mode=str(res.get("persistenceMode", "OFF")),
            extra=res,
        )

    async def stats(self) -> Dict[str, Any]:
        return await self._transport.request("GET", "/api/v1/memory/stats")

    async def browse(self, tags: Optional[List[str]] = None) -> List[Dict[str, Any]]:
        body = {"tags": tags or []}
        return await self._transport.request("POST", "/api/v1/memory/browse", body=body)

    async def table(
        self,
        page: int = 0,
        page_size: int = 20,
        tier: Optional[str] = None,
        tombstoned: bool = False,
    ) -> Dict[str, Any]:
        params = {
            "page": page,
            "pageSize": page_size,
            "tier": tier,
            "tombstoned": str(tombstoned).lower(),
        }
        return await self._transport.request("GET", "/api/v1/memory/table", query_params=params)

    async def vector(self, memory_id: str) -> List[int]:
        res = await self._transport.request("GET", f"/api/v1/memory/{memory_id}/vector")
        return res.get("vector", [])

    async def consolidate(self) -> None:
        await self._transport.request("POST", "/api/v1/memory/consolidate")

    async def vacuum(self, tier: Optional[str] = None) -> Dict[str, Any]:
        body = {"tier": tier} if tier else {}
        return await self._transport.request("POST", "/api/v1/memory/vacuum", body=body)
