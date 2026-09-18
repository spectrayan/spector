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
package com.spectrayan.spector.synapse.catalog.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.spectrayan.spector.synapse.catalog.NamespaceBias;
import com.spectrayan.spector.synapse.catalog.NamespaceType;

/**
 * Request payload for creating a new namespace (ADR-0029 §8.1).
 * The caller supplies the {@code slug}; the system allocates the immutable TSID {@code namespaceId}.
 *
 * @param slug        human-readable slug, unique per account (1-63 alphanumeric/hyphen/underscore)
 * @param type        namespace type (default: PROJECT)
 * @param displayName optional human-readable display name
 * @param description optional human-readable description
 * @param bias        optional namespace domain bias overlay
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateNamespaceRequest(
        String slug,
        NamespaceType type,
        String displayName,
        String description,
        NamespaceBias bias) {
}
