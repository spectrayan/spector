package com.spectrayan.spector.gpu;

/**
 * Exception thrown when a GPU memory operation fails.
 *
 * <p>Contains information about the requested allocation size and
 * the currently available device memory, enabling callers to make
 * informed decisions about memory management.</p>
 */
public class GpuMemoryException extends RuntimeException {

    private final long requestedBytes;
    private final long availableBytes;

    /**
     * Creates a GPU memory exception with allocation context.
     *
     * @param message        descriptive error message
     * @param requestedBytes the number of bytes that were requested
     * @param availableBytes the number of bytes available (or budget remaining)
     */
    public GpuMemoryException(String message, long requestedBytes, long availableBytes) {
        super(message);
        this.requestedBytes = requestedBytes;
        this.availableBytes = availableBytes;
    }

    /**
     * Creates a GPU memory exception with a cause.
     *
     * @param message        descriptive error message
     * @param cause          the underlying cause
     * @param requestedBytes the number of bytes that were requested
     * @param availableBytes the number of bytes available (or budget remaining)
     */
    public GpuMemoryException(String message, Throwable cause, long requestedBytes, long availableBytes) {
        super(message, cause);
        this.requestedBytes = requestedBytes;
        this.availableBytes = availableBytes;
    }

    /** Returns the number of bytes that were requested in the failed allocation. */
    public long getRequestedBytes() {
        return requestedBytes;
    }

    /** Returns the number of bytes available at the time of the failure. */
    public long getAvailableBytes() {
        return availableBytes;
    }
}
