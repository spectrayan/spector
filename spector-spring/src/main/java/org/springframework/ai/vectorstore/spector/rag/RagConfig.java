package org.springframework.ai.vectorstore.spector.rag;

/**
 * Configuration for RAG retrieval operations in {@link SpectorRagService}.
 *
 * @param topK               the number of top results to retrieve (1–100, default 5)
 * @param similarityThreshold the minimum relevance score for results (0.0–1.0, default 0.7)
 * @param tokenLimit         the maximum number of tokens in assembled context (1–8192, default 4096)
 */
public record RagConfig(int topK, float similarityThreshold, int tokenLimit) {

    /** Default topK value. */
    public static final int DEFAULT_TOP_K = 5;

    /** Default similarity threshold. */
    public static final float DEFAULT_SIMILARITY_THRESHOLD = 0.7f;

    /** Default token limit. */
    public static final int DEFAULT_TOKEN_LIMIT = 4096;

    public RagConfig {
        if (topK < 1 || topK > 100) {
            throw new IllegalArgumentException("topK must be between 1 and 100, got: " + topK);
        }
        if (similarityThreshold < 0.0f || similarityThreshold > 1.0f) {
            throw new IllegalArgumentException(
                    "similarityThreshold must be between 0.0 and 1.0, got: " + similarityThreshold);
        }
        if (tokenLimit < 1 || tokenLimit > 8192) {
            throw new IllegalArgumentException("tokenLimit must be between 1 and 8192, got: " + tokenLimit);
        }
    }

    /**
     * Creates a RagConfig with all default values.
     */
    public static RagConfig defaults() {
        return new RagConfig(DEFAULT_TOP_K, DEFAULT_SIMILARITY_THRESHOLD, DEFAULT_TOKEN_LIMIT);
    }
}
