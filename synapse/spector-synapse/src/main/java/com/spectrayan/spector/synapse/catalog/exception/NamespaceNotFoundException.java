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
package com.spectrayan.spector.synapse.catalog.exception;

import com.spectrayan.spector.commons.error.ErrorCode;

/**
 * Thrown when a requested namespace does not exist in the catalog.
 */
public class NamespaceNotFoundException extends NamespaceException {

    private final String namespaceIdOrSlug;

    /**
     * Creates a new namespace not found exception.
     *
     * @param namespaceIdOrSlug the namespace ID or slug that could not be found
     */
    public NamespaceNotFoundException(String namespaceIdOrSlug) {
        super(ErrorCode.NAMESPACE_NOT_FOUND, "NamespaceNotFound", namespaceIdOrSlug);
        this.namespaceIdOrSlug = namespaceIdOrSlug;
    }

    /**
     * Gets the namespace ID or slug.
     *
     * @return the namespace ID or slug
     */
    public String getNamespaceIdOrSlug() {
        return namespaceIdOrSlug;
    }
}
