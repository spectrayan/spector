# Copyright 2026 Spectrayan — Apache 2.0
"""
High-level search engine client wrapping MCP engine tools.
"""

from __future__ import annotations

from typing import Optional, TYPE_CHECKING

if TYPE_CHECKING:
    from spector.transport import StdioTransport


class EngineClient:
    """Search engine operations — wraps all ``engine_*`` MCP tools.

    Do not instantiate directly. Access via ``SpectorClient.engine``::

        with SpectorClient(jar_path="spector.jar") as client:
            results = client.engine.search("SIMD acceleration", top_k=5)
    """

    def __init__(self, transport: StdioTransport):
        self._transport = transport

    def search(self, query: str, *, top_k: int = 10) -> str:
        """Vector similarity search.

        Args:
            query: The search query text.
            top_k: Maximum results (default: 10).

        Returns:
            The raw search result text.
        """
        return self._transport.call_tool("engine_search", {
            "query": query,
            "top_k": top_k,
        })

    def hybrid_search(
        self,
        query: str,
        *,
        top_k: int = 10,
        keyword_weight: float = 0.3,
    ) -> str:
        """Keyword + vector hybrid search with RRF fusion.

        Args:
            query: The search query text.
            top_k: Maximum results (default: 10).
            keyword_weight: Weight for keyword scoring (default: 0.3).

        Returns:
            The raw hybrid search result text.
        """
        return self._transport.call_tool("engine_hybrid_search", {
            "query": query,
            "top_k": top_k,
            "keyword_weight": keyword_weight,
        })

    def rag(self, query: str, *, top_k: int = 5) -> str:
        """Retrieval-Augmented Generation context retrieval.

        Args:
            query: The RAG query.
            top_k: Number of context chunks (default: 5).

        Returns:
            The retrieved context text for LLM consumption.
        """
        return self._transport.call_tool("engine_rag", {
            "query": query,
            "top_k": top_k,
        })

    def ingest(
        self,
        id: str,
        text: str,
        *,
        metadata: Optional[dict] = None,
    ) -> str:
        """Adds a document to the search index.

        Args:
            id: Unique document identifier.
            text: Document content.
            metadata: Optional metadata dict.

        Returns:
            Confirmation text.
        """
        args: dict = {"id": id, "text": text}
        if metadata:
            args["metadata"] = str(metadata)
        return self._transport.call_tool("engine_ingest", args)

    def delete(self, id: str) -> str:
        """Removes a document from the index.

        Args:
            id: Document ID to remove.

        Returns:
            Confirmation text.
        """
        return self._transport.call_tool("engine_delete", {"id": id})

    def status(self) -> str:
        """Returns engine capabilities and stats.

        Returns:
            The raw status text.
        """
        return self._transport.call_tool("engine_status", {})
