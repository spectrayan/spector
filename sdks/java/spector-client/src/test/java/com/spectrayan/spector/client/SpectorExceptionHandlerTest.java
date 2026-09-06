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
package com.spectrayan.spector.client;

import com.spectrayan.spector.client.exception.MemoryNotFoundException;
import com.spectrayan.spector.client.exception.SpectorAuthException;
import com.spectrayan.spector.client.exception.SpectorException;
import com.spectrayan.spector.client.exception.SpectorExceptionHandler;
import com.spectrayan.spector.client.exception.SpectorServerException;
import com.spectrayan.spector.client.exception.SpectorValidationException;
import com.spectrayan.spector.client.generated.invoker.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpectorExceptionHandlerTest {

    @Test
    @DisplayName("401 and 403 translate to SpectorAuthException")
    void translatesAuth() {
        ApiException e401 = new ApiException(401, "Unauthorized", null, "{\"error\":\"unauthorized\"}");
        SpectorException result401 = SpectorExceptionHandler.translate(e401, null);
        assertThat(result401).isInstanceOf(SpectorAuthException.class);
        assertThat(result401.getStatusCode()).isEqualTo(401);
        assertThat(result401.getResponseBody()).isEqualTo("{\"error\":\"unauthorized\"}");

        ApiException e403 = new ApiException(403, "Forbidden", null, null);
        SpectorException result403 = SpectorExceptionHandler.translate(e403, null);
        assertThat(result403).isInstanceOf(SpectorAuthException.class);
        assertThat(result403.getStatusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("404 translates to MemoryNotFoundException with resourceId")
    void translatesNotFound() {
        ApiException e404 = new ApiException(404, "Not Found", null, "{\"error\":\"memory missing\"}");
        SpectorException result = SpectorExceptionHandler.translate(e404, "mem-999");
        assertThat(result).isInstanceOf(MemoryNotFoundException.class);
        MemoryNotFoundException notFound = (MemoryNotFoundException) result;
        assertThat(notFound.getMemoryId()).isEqualTo("mem-999");
        assertThat(notFound.getStatusCode()).isEqualTo(404);
        assertThat(notFound.getResponseBody()).isEqualTo("{\"error\":\"memory missing\"}");
    }

    @Test
    @DisplayName("400 and 422 translate to SpectorValidationException")
    void translatesValidation() {
        ApiException e400 = new ApiException(400, "Bad Request", null, "invalid text");
        SpectorException result400 = SpectorExceptionHandler.translate(e400, null);
        assertThat(result400).isInstanceOf(SpectorValidationException.class);
        assertThat(result400.getStatusCode()).isEqualTo(400);

        ApiException e422 = new ApiException(422, "Unprocessable", null, null);
        SpectorException result422 = SpectorExceptionHandler.translate(e422, null);
        assertThat(result422).isInstanceOf(SpectorValidationException.class);
        assertThat(result422.getStatusCode()).isEqualTo(422);
    }

    @Test
    @DisplayName("500+ translates to SpectorServerException")
    void translatesServerError() {
        ApiException e500 = new ApiException(500, "Internal Server Error", null, "db connection error");
        SpectorException result500 = SpectorExceptionHandler.translate(e500, null);
        assertThat(result500).isInstanceOf(SpectorServerException.class);
        assertThat(result500.getStatusCode()).isEqualTo(500);

        ApiException e503 = new ApiException(503, "Service Unavailable", null, null);
        SpectorException result503 = SpectorExceptionHandler.translate(e503, null);
        assertThat(result503).isInstanceOf(SpectorServerException.class);
        assertThat(result503.getStatusCode()).isEqualTo(503);
    }

    @Test
    @DisplayName("Other HTTP codes translate to base SpectorException")
    void translatesOtherCodes() {
        ApiException e418 = new ApiException(418, "I'm a teapot", null, null);
        SpectorException result = SpectorExceptionHandler.translate(e418, null);
        assertThat(result.getClass()).isEqualTo(SpectorException.class);
        assertThat(result.getStatusCode()).isEqualTo(418);
    }
}
