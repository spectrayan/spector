/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.client.exception;

import com.spectrayan.spector.client.generated.invoker.ApiException;

/**
 * Translates low-level {@link ApiException} instances into typed domain exceptions.
 */
public final class SpectorExceptionHandler {

    private SpectorExceptionHandler() {
    }

    /**
     * Translates an {@link ApiException} to a typed {@link SpectorException}.
     */
    public static SpectorException translate(ApiException e, String resourceId) {
        int code = e.getCode();
        String body = e.getResponseBody();
        String message = e.getMessage();

        if (code == 401 || code == 403) {
            return new SpectorAuthException(code, "Authentication/authorization failed: " + message, body, e);
        }
        if (code == 404) {
            return new MemoryNotFoundException(resourceId, code, "Resource not found" + (resourceId != null ? " [id=" + resourceId + "]" : "") + ": " + message, body, e);
        }
        if (code == 400 || code == 422) {
            return new SpectorValidationException(code, "Validation error: " + message, body, e);
        }
        if (code >= 500) {
            return new SpectorServerException(code, "Server error (" + code + "): " + message, body, e);
        }
        return new SpectorException(code, "Spector API request failed with status " + code + ": " + message, body, e);
    }

    /**
     * Executes an API call returning a value, wrapping and translating checked exceptions.
     */
    public static <T> T execute(ApiSupplier<T> supplier) {
        return execute(supplier, null);
    }

    /**
     * Executes an API call returning a value with an optional associated resource ID for not-found translation.
     */
    public static <T> T execute(ApiSupplier<T> supplier, String resourceId) {
        try {
            return supplier.get();
        } catch (ApiException e) {
            throw translate(e, resourceId);
        } catch (Exception e) {
            throw new SpectorException("Client execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Executes a void API call, wrapping and translating checked exceptions.
     */
    public static void executeVoid(ApiAction action) {
        executeVoid(action, null);
    }

    /**
     * Executes a void API call with an optional associated resource ID.
     */
    public static void executeVoid(ApiAction action, String resourceId) {
        try {
            action.run();
        } catch (ApiException e) {
            throw translate(e, resourceId);
        } catch (Exception e) {
            throw new SpectorException("Client execution failed: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    public interface ApiSupplier<T> {
        T get() throws ApiException;
    }

    @FunctionalInterface
    public interface ApiAction {
        void run() throws ApiException;
    }
}
