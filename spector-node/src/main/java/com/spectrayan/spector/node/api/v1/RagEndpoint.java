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
package com.spectrayan.spector.node.api.v1;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.annotation.ExceptionHandler;
import com.linecorp.armeria.server.annotation.Post;

import com.spectrayan.spector.node.api.ApiModule;
import com.spectrayan.spector.node.api.dto.RagRequest;
import com.spectrayan.spector.node.exception.ApiExceptionHandler;
import com.spectrayan.spector.node.service.RagService;
import com.spectrayan.spector.commons.error.SpectorException;

/**
 * RAG (Retrieval-Augmented Generation) API v1 endpoint.
 *
 * <ul>
 *   <li>{@code POST /rag} — retrieve context with attributions</li>
 * </ul>
 */
@ExceptionHandler(ApiExceptionHandler.class)
public class RagEndpoint implements ApiModule {

    private final RagService ragService;

    public RagEndpoint(RagService ragService) {
        this.ragService = ragService;
    }

    @Override
    public String pathPrefix() { return "/engine"; }

    @Post("/rag")
    public HttpResponse rag(RagRequest request) throws com.spectrayan.spector.commons.error.SpectorException {
        return HttpResponse.ofJson(ragService.retrieveContext(request));
    }
}
