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
package com.spectrayan.spector.synapse.agent.chat.policy;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.kernel.api.MemorySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Enforces dual-plane persistence invariants (ADR-0084) for cognitive memory writes.
 *
 * <p>Prevents tag index pollution and vector-space poisoning by rejecting:
 * <ul>
 *   <li>High-cardinality session and turn identifiers</li>
 *   <li>Operational transcript substitute tags (role, model, type:turn)</li>
 *   <li>Raw tool JSON, CoT reasoning scratchpads, unsummarized transcripts, and graph checkpoints</li>
 * </ul>
 *
 * <p>Additionally enforces mandatory {@link MemorySource} provenance.</p>
 */
@Component
public class MemoryTagPolicy {

    private static final Logger log = LoggerFactory.getLogger(MemoryTagPolicy.class);

    /**
     * Regex patterns for strictly prohibited tag prefixes and values.
     */
    public static final List<Pattern> DENYLIST_PATTERNS = List.of(
            Pattern.compile("^session([:_\\-].*)?$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^id([:_\\-].*)?$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^type([:_\\-].*)?$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^role([:_\\-].*)?$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^model([:_\\-].*)?$", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Standard domain namespaces allowed for cognitive memory tags.
     */
    public static final Set<String> ALLOWED_NAMESPACES = Set.of(
            "preference", "people", "decision", "project", "entity",
            "architecture", "concept", "fact", "summary", "topic",
            "domain", "knowledge", "skill", "tech"
    );

    /**
     * Allowed provenance sources for conversational cognitive memory writes.
     */
    public static final Set<MemorySource> PERMITTED_SOURCES = Set.of(
            MemorySource.USER_STATED,
            MemorySource.OBSERVED,
            MemorySource.INFERRED,
            MemorySource.REFLECTED
    );

    private static final int MAX_TAG_LENGTH = 64;
    private static final int MAX_CONTENT_LENGTH = 8192;

    /**
     * Comprehensive validation of cognitive memory write parameters.
     *
     * @param content the text payload of the memory engram
     * @param tags    the collection of tags to be assigned
     * @param source  the cognitive provenance source
     * @throws SpectorValidationException if any invariant is violated
     */
    public void validate(String content, Collection<String> tags, MemorySource source) {
        validateContent(content);
        validateTags(tags);
        validateSource(source);
    }

    /**
     * Adapter alias for validate(text, tags, source) to support ChatCognitivePort callers.
     */
    public void validateSalientMemory(String text, List<String> tags, MemorySource source) {
        validate(text, tags, source);
    }

    /**
     * Validates that all tags conform to the dual-plane hygiene policy.
     *
     * @param tags collection of tag strings
     * @throws SpectorValidationException if any tag violates denylist or namespace constraints
     */
    public void validateTags(Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }

        for (String tag : tags) {
            validateSingleTag(tag);
        }
    }

    private void validateSingleTag(String tag) {
        if (tag == null || tag.isBlank()) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID, "memory_tag", "Tag must not be null or blank");
        }

        String trimmed = tag.trim();
        if (trimmed.length() > MAX_TAG_LENGTH) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID, "memory_tag",
                    "Tag exceeds maximum length of " + MAX_TAG_LENGTH + " characters: " + trimmed);
        }

        // 1. Check against denylist regexes
        for (Pattern pattern : DENYLIST_PATTERNS) {
            if (pattern.matcher(trimmed).matches()) {
                throw new SpectorValidationException(
                        ErrorCode.ARGUMENT_INVALID, "memory_tag",
                        "Tag matches prohibited denylist pattern [" + pattern.pattern() + "]: " + trimmed);
            }
        }

        // 2. Namespace validation (if tag contains a colon separator)
        int colonIdx = trimmed.indexOf(':');
        if (colonIdx > 0) {
            String namespace = trimmed.substring(0, colonIdx).toLowerCase(Locale.ROOT);
            String value = trimmed.substring(colonIdx + 1).trim();

            if (!ALLOWED_NAMESPACES.contains(namespace)) {
                throw new SpectorValidationException(
                        ErrorCode.ARGUMENT_INVALID, "memory_tag",
                        "Tag namespace '" + namespace + "' is not permitted. Allowed namespaces: " + ALLOWED_NAMESPACES);
            }
            if (value.isBlank()) {
                throw new SpectorValidationException(
                        ErrorCode.ARGUMENT_INVALID, "memory_tag",
                        "Tag value after namespace must not be blank: " + trimmed);
            }
            if (!value.matches("^[a-zA-Z0-9_-]+$")) {
                throw new SpectorValidationException(
                        ErrorCode.ARGUMENT_INVALID, "memory_tag",
                        "Tag value must be a clean alphanumeric token matching ^[a-zA-Z0-9_-]+$: " + trimmed);
            }
        } else {
            // Bare domain tag: must either be an allowed namespace or alphanumeric identifier
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (!ALLOWED_NAMESPACES.contains(lower) && !lower.matches("^[a-z0-9_-]+$")) {
                throw new SpectorValidationException(
                        ErrorCode.ARGUMENT_INVALID, "memory_tag",
                        "Bare tag must be an allowed namespace or clean alphanumeric token: " + trimmed);
            }
        }
    }

    /**
     * Validates that the memory content payload does not contain raw operational noise.
     *
     * @param content the raw text payload to store
     * @throws SpectorValidationException if payload contains tool JSON, CoT, transcripts, or checkpoints
     */
    public void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID, "memory_content", "Memory content must not be null or blank");
        }

        String trimmed = content.trim();
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID, "memory_content",
                    "Memory content exceeds maximum allowed length of " + MAX_CONTENT_LENGTH + " characters");
        }

        // 1. Reject Chain-of-Thought (CoT) scratchpads
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("<think>") || lower.contains("</think>") ||
                lower.startsWith("thinking process:") || lower.contains("[internal cot]")) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID, "memory_content",
                    "Memory content contains prohibited Chain-of-Thought (<think>) reasoning scratchpad");
        }

        // 2. Reject raw tool JSON payloads
        String unquoted = trimmed;
        if (unquoted.startsWith("```") && unquoted.endsWith("```")) {
            int firstNewline = unquoted.indexOf('\n');
            if (firstNewline != -1 && firstNewline < unquoted.length() - 3) {
                unquoted = unquoted.substring(firstNewline + 1, unquoted.length() - 3).trim();
            } else {
                unquoted = unquoted.substring(3, unquoted.length() - 3).trim();
            }
        }
        String testLower = unquoted.toLowerCase(Locale.ROOT);
        if ((unquoted.startsWith("{") && unquoted.endsWith("}")) ||
                (unquoted.startsWith("[") && unquoted.endsWith("]")) ||
                unquoted.startsWith("[{")) {
            if (testLower.contains("\"toolexecutionrequests\"") ||
                    testLower.contains("\"arguments\":") ||
                    testLower.contains("\"callid\":") ||
                    testLower.contains("\"toolname\":") ||
                    testLower.contains("\"status\":\"success\"") ||
                    testLower.contains("\"status\":\"failure\"")) {
                throw new SpectorValidationException(
                        ErrorCode.ARGUMENT_INVALID, "memory_content",
                        "Memory content contains raw tool execution JSON payload");
            }
        }

        // 3. Reject unsummarized multi-turn transcripts
        if (lower.contains("user:") && lower.contains("assistant:") && trimmed.contains("\n")) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID, "memory_content",
                    "Memory content contains raw unsummarized conversation transcript");
        }

        // 4. Reject LangGraph checkpoints or binary serialized states
        if (trimmed.contains("org.bsc.langgraph4j.checkpoint") ||
                lower.contains("\"checkpoint_id\":") ||
                lower.contains("\"channelvalues\":")) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID, "memory_content",
                    "Memory content contains serialized LangGraph checkpoint data");
        }
    }

    /**
     * Validates that the memory provenance source is explicitly defined and permitted.
     *
     * @param source the MemorySource enum instance
     * @throws SpectorValidationException if source is null or unpermitted
     */
    public void validateSource(MemorySource source) {
        if (source == null) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID, "memory_source", "MemorySource must not be null");
        }
        if (!PERMITTED_SOURCES.contains(source)) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID, "memory_source",
                    "MemorySource " + source + " is not permitted for conversational cognitive memory ingestion. Allowed: " + PERMITTED_SOURCES);
        }
    }

    /**
     * Safe query check whether a tag violates policy without throwing an exception.
     */
    public boolean isTagPermitted(String tag) {
        try {
            validateSingleTag(tag);
            return true;
        } catch (SpectorValidationException e) {
            return false;
        }
    }

    /**
     * Returns true if tag violates policy.
     */
    public boolean isForbiddenTag(String tag) {
        return !isTagPermitted(tag);
    }

    /**
     * Checks whether content contains prohibited operational noise (tool JSON, CoT, transcripts, checkpoints).
     *
     * @param content the text content to inspect
     * @return true if content is prohibited
     */
    public boolean isProhibitedContent(String content) {
        if (content == null || content.isBlank()) {
            return true;
        }
        try {
            validateContent(content);
            return false;
        } catch (SpectorValidationException e) {
            return true;
        }
    }
}
