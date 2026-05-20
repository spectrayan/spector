package org.springframework.ai.vectorstore.spector.rag;

/**
 * Exception thrown by {@link SpectorRagService} when a dependency fails
 * (vector store unavailable, context builder error, etc.).
 *
 * <p>This exception propagates dependency errors without crashing the application,
 * allowing callers to handle retrieval failures gracefully.</p>
 */
public class SpectorRagServiceException extends RuntimeException {

    /**
     * Creates a new SpectorRagServiceException with the specified message.
     *
     * @param message the error message
     */
    public SpectorRagServiceException(String message) {
        super(message);
    }

    /**
     * Creates a new SpectorRagServiceException with the specified message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public SpectorRagServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
