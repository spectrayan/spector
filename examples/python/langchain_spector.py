# Copyright 2026 Spectrayan — Apache 2.0
"""
Spector Cognitive Memory Adapter for LangChain / LangGraph.
Provides a clean bridge between Spector's fused cognitive recall and LangChain memory.
"""

from typing import Any, Dict, List, Optional
from spector_client import SpectorClient, MemoryTier


class SpectorLangChainMemory:
    """LangChain-compatible memory component backed by Spector Cognitive Memory."""

    def __init__(
        self,
        base_url: str = "http://localhost:7070",
        api_key: Optional[str] = None,
        top_k: int = 5,
        profile: str = "BALANCED",
    ):
        self.client = SpectorClient.builder() \
            .with_rest(base_url=base_url, api_key=api_key) \
            .build()
        self.top_k = top_k
        self.profile = profile

    def save_context(self, inputs: Dict[str, Any], outputs: Dict[str, str]) -> None:
        """Stores conversation exchange into Spector episodic memory."""
        user_input = inputs.get("input") or str(inputs)
        ai_output = outputs.get("output") or str(outputs)
        text = f"User: {user_input}\nAssistant: {ai_output}"
        self.client.memory.remember(
            text=text,
            tier=MemoryTier.EPISODIC,
            tags=["conversation", "langchain"],
        )

    def load_memory_variables(self, inputs: Dict[str, Any]) -> Dict[str, Any]:
        """Recalls relevant context for the current user input."""
        query = inputs.get("input") or str(inputs)
        records = self.client.memory.recall(
            query=query,
            top_k=self.top_k,
            profile=self.profile,
        )
        context = "\n".join([f"- {r.text} (score: {r.score:.2f})" for r in records])
        return {"history": context, "records": records}

    def clear(self) -> None:
        """Consolidates memory store."""
        self.client.memory.consolidate()


if __name__ == "__main__":
    memory = SpectorLangChainMemory()
    print("Spector LangChain Memory adapter initialized successfully.")

