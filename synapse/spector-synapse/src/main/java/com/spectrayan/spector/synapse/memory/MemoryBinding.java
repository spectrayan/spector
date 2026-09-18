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
package com.spectrayan.spector.synapse.memory;

import com.spectrayan.spector.memory.SpectorMemory;

/**
 * Immutable per-request memory binding context holding the resolved {@link SpectorMemory}
 * instance along with its owning account, resolved namespace identifier, lease, and context (ADR-0029 §16).
 *
 * @param memory               the active SpectorMemory engine
 * @param accountId            the authenticated account identifier
 * @param namespaceId          the resolved data-plane namespace TSID
 * @param slug                 the requested or resolved namespace slug
 * @param lease                the active engine lease handle (released on unbind)
 * @param requestMemoryContext the full resolved memory request context
 */
public record MemoryBinding(
        SpectorMemory memory,
        String accountId,
        String namespaceId,
        String slug,
        AutoCloseable lease,
        RequestMemoryContext requestMemoryContext) {

    /** RequestAttributes attribute key for the bound context. */
    public static final String ATTRIBUTE_KEY = "com.spectrayan.spector.synapse.memory.MemoryBinding";

    /**
     * Backward-compatible constructor without lease or request context.
     */
    public MemoryBinding(SpectorMemory memory, String accountId, String namespaceId, String slug) {
        this(memory, accountId, namespaceId, slug, null, null);
    }

    /**
     * Retrieves the current request's bound {@link MemoryBinding} from RequestContextHolder if available.
     *
     * @return optional containing the active MemoryBinding, or empty if none bound
     */
    public static java.util.Optional<MemoryBinding> current() {
        org.springframework.web.context.request.RequestAttributes attrs =
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            Object binding = attrs.getAttribute(ATTRIBUTE_KEY, org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
            if (binding instanceof MemoryBinding mb) {
                return java.util.Optional.of(mb);
            }
        }
        return java.util.Optional.empty();
    }
}
