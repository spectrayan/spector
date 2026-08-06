/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.config.properties;

import static com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_CORS_ALLOWED_ORIGINS;

import java.io.Serializable;

/**
 * Configuration properties POJO for CORS origin settings.
 */
public class CorsProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private String allowedOrigins = DEFAULT_CORS_ALLOWED_ORIGINS;

    public CorsProperties() {}

    public CorsProperties(String allowedOrigins) {
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public String allowedOrigins() {
        return getAllowedOrigins();
    }
}
