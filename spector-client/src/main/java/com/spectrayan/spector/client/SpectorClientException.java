package com.spectrayan.spector.client;

/**
 * Base exception for all Spector Client SDK errors.
 */
public class SpectorClientException extends RuntimeException {

    public SpectorClientException(String message) {
        super(message);
    }

    public SpectorClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
