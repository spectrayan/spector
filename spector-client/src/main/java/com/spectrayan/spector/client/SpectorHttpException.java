package com.spectrayan.spector.client;

/**
 * Thrown when the server returns an HTTP error response (4xx or 5xx).
 * Contains the HTTP status code, error message from the response body, and the request URL.
 */
public class SpectorHttpException extends SpectorClientException {

    private final int statusCode;
    private final String errorMessage;
    private final String requestUrl;

    public SpectorHttpException(int statusCode, String errorMessage, String requestUrl) {
        super("HTTP " + statusCode + " from " + requestUrl + ": " + errorMessage);
        this.statusCode = statusCode;
        this.errorMessage = errorMessage;
        this.requestUrl = requestUrl;
    }

    /** Returns the HTTP status code from the server response. */
    public int statusCode() {
        return statusCode;
    }

    /** Returns the error message extracted from the response body. */
    public String errorMessage() {
        return errorMessage;
    }

    /** Returns the request URL that produced the error. */
    public String requestUrl() {
        return requestUrl;
    }
}
