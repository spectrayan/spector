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
package com.spectrayan.spector.connector.sink;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * High-performance, regex-based security scrubber to prevent PII and secrets
 * from entering AI memory.
 */
public final class PiiScrubber {

    private static final class ScrubRule {
        final Pattern pattern;
        final String replacement;

        ScrubRule(String regex, String replacement) {
            this.pattern = Pattern.compile(regex);
            this.replacement = replacement;
        }
    }

    private static final List<ScrubRule> RULES = new ArrayList<>();

    static {
        // SSN: XXX-XX-XXXX
        RULES.add(new ScrubRule("\\b\\d{3}-\\d{2}-\\d{4}\\b", "[REDACTED_SSN]"));
        
        // Emails
        RULES.add(new ScrubRule("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "[REDACTED_EMAIL]"));
        
        // AWS Access Key ID
        RULES.add(new ScrubRule("\\b(AKIA|AGPA|AIDA|AROA|AIPA|ANPA|ANVA|ASIA)[A-Z0-9]{16}\\b", "[REDACTED_AWS_KEY_ID]"));
        
        // AWS Secret Access Key (look for key-value pair first)
        RULES.add(new ScrubRule("(?i)(?:aws_secret_access_key|secret_key|aws_secret)\\s*[:=]\\s*['\"]?([A-Za-z0-9/+=]{40})['\"]?", "[REDACTED_AWS_SECRET]"));
        
        // Generic Password / API Key in JSON/YAML/Properties
        RULES.add(new ScrubRule("(?i)(password|passwd|secret|api_key|apikey|token|private_key)\\s*[:=]\\s*[\"']?([A-Za-z0-9_\\-\\.\\~]{10,100})[\"']?", "$1:[REDACTED_SECRET]"));

        // Private Key blocks (PEM)
        RULES.add(new ScrubRule("(?s)-----BEGIN [A-Z ]+ PRIVATE KEY-----[\\s\\S]+?-----END [A-Z ]+ PRIVATE KEY-----", "[REDACTED_PRIVATE_KEY]"));

        // Phone numbers
        RULES.add(new ScrubRule("\\b(?:\\+\\d{1,3}[- ]?)?\\(?\\d{3}\\)?[- ]?\\d{3}[- ]?\\d{4}\\b", "[REDACTED_PHONE]"));

        // Credit card numbers (13-16 digits, with or without dashes/spaces)
        RULES.add(new ScrubRule("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{1,4}\\b", "[REDACTED_CC]"));
    }

    private PiiScrubber() {}

    /**
     * Scrubs any sensitive information from the input text.
     *
     * @param input raw text
     * @return clean text
     */
    public static String scrub(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String current = input;
        for (ScrubRule rule : RULES) {
            current = rule.pattern.matcher(current).replaceAll(rule.replacement);
        }
        return current;
    }
}
