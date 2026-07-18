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
package com.spectrayan.spector.provider.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents a message in a multimodal conversation.
 */
public record ChatMessage(
        ChatRole role,
        List<ContentBlock> content
) {

    public ChatMessage {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(content, "content must not be null");
        if (content.isEmpty()) {
            throw new IllegalArgumentException("content list must not be empty");
        }
    }

    public static ChatMessage system(String text) {
        return new ChatMessage(ChatRole.SYSTEM, List.of(new TextContent(text)));
    }

    public static ChatMessage user(String text) {
        return new ChatMessage(ChatRole.USER, List.of(new TextContent(text)));
    }

    public static ChatMessage user(ContentBlock... blocks) {
        return new ChatMessage(ChatRole.USER, List.of(blocks));
    }

    public static ChatMessage ai(String text) {
        return new ChatMessage(ChatRole.AI, List.of(new TextContent(text)));
    }

    public String text() {
        var sb = new StringBuilder();
        for (var block : content) {
            if (block instanceof TextContent txt) {
                sb.append(txt.text());
            }
        }
        return sb.toString();
    }
}
