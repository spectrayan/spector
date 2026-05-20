package org.springframework.ai.vectorstore.spector;

/**
 * Exception thrown when the SpectorVectorStore encounters a connection or operational failure.
 */
public class SpectorVectorStoreException extends RuntimeException {

    public SpectorVectorStoreException(String message) {
        super(message);
    }

    public SpectorVectorStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
