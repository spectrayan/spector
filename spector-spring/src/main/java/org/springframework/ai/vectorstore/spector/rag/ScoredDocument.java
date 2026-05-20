package org.springframework.ai.vectorstore.spector.rag;

/**
 * A document result with a relevance score from RAG retrieval.
 *
 * @param documentId the source document identifier
 * @param content    the document text content
 * @param score      the relevance score (0.0–1.0 inclusive)
 * @param chunkOffset the offset of the chunk within the source document
 */
public record ScoredDocument(String documentId, String content, float score, int chunkOffset) {

    public ScoredDocument {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be null or blank");
        }
        if (score < 0.0f || score > 1.0f) {
            throw new IllegalArgumentException("score must be between 0.0 and 1.0, got: " + score);
        }
        if (chunkOffset < 0) {
            throw new IllegalArgumentException("chunkOffset must not be negative");
        }
    }
}
