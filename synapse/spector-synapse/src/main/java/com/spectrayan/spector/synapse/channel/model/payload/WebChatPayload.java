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
package com.spectrayan.spector.synapse.channel.model.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured WebChat WebSocket/REST/SSE payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WebChatPayload(
        @JsonProperty("id") String id,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("user_name") String userName,
        @JsonProperty("content") String content,
        @JsonProperty("reply_to") String replyTo,
        @JsonProperty("file_url") String fileUrl,
        @JsonProperty("file_name") String fileName,
        @JsonProperty("mime_type") String mimeType,
        @JsonProperty("client_info") String clientInfo,
        @JsonProperty("page_url") String pageUrl
) {}
