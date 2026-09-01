/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.aisme.privacy;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance, zero-dependency edge local PII sanitizer and deterministic salted pseudonymizer.
 *
 * <h3>Biological Analog: Episodic De-individuation & Semantic Abstraction</h3>
 * <p>Sanitizes personal identifiable information (PII) before synaptic memory consolidation
 * while preserving causal entity linkage across autobiographical episodes using deterministic HMAC tokens.</p>
 */
public final class EdgeAnonymizer {

    private static final Logger log = LoggerFactory.getLogger(EdgeAnonymizer.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b(?:\\+\\d{1,3}[- ]?)?\\(?\\d{3}\\)?[- ]?\\d{3}[- ]?\\d{4}\\b");
    private static final Pattern SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile("\\b(?:\\d{4}[- ]?){3}\\d{4}\\b");
    private static final Pattern IPV4_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern AWS_KEY_PATTERN = Pattern.compile("\\b(AKIA|AGPA|AIDA|AROA|AIPA|ANPA|ANVA|ASIA)[A-Z0-9]{16}\\b");
    private static final Pattern PEM_KEY_PATTERN = Pattern.compile("(?s)-----BEGIN [A-Z ]+ PRIVATE KEY-----[\\s\\S]+?-----END [A-Z ]+ PRIVATE KEY-----");
    private static final Pattern GENERIC_SECRET_PATTERN = Pattern.compile("(?i)(password|passwd|secret|api_key|apikey|token|private_key)\\s*[:=]\\s*[\"']?([A-Za-z0-9_\\-\\.\\~]{10,100})[\"']?");

    private final byte[] saltBytes;

    public EdgeAnonymizer(String salt) {
        if (salt == null || salt.isBlank()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "salt must not be null or blank");
        }
        this.saltBytes = salt.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Sanitizes and pseudonymizes cleartext input, replacing sensitive entities with deterministic HMAC tokens.
     *
     * @param input cleartext narrative or memory content
     * @return sanitized and pseudonymized content
     */
    public String anonymize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String result = input;

        // PEM private keys
        result = PEM_KEY_PATTERN.matcher(result).replaceAll("[REDACTED_PRIVATE_KEY]");

        // Generic secrets & API keys
        result = GENERIC_SECRET_PATTERN.matcher(result).replaceAll("$1:[REDACTED_SECRET]");

        // AWS keys
        result = AWS_KEY_PATTERN.matcher(result).replaceAll("[REDACTED_AWS_KEY]");

        // Emails -> Deterministic Pseudonyms
        result = replaceWithPseudonym(result, EMAIL_PATTERN, "EMAIL");

        // Phone numbers -> Deterministic Pseudonyms
        result = replaceWithPseudonym(result, PHONE_PATTERN, "PHONE");

        // SSNs -> Deterministic Pseudonyms
        result = replaceWithPseudonym(result, SSN_PATTERN, "SSN");

        // Credit Cards -> Redaction
        result = CREDIT_CARD_PATTERN.matcher(result).replaceAll("[REDACTED_CARD]");

        // IPv4 Addresses -> Deterministic Pseudonyms
        result = replaceWithPseudonym(result, IPV4_PATTERN, "IP");

        return result;
    }

    /**
     * Sanitizes a collection of memory tags.
     *
     * @param tags original tags
     * @return sanitized set of tags
     */
    public Set<String> anonymizeTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return tags != null ? tags : Collections.emptySet();
        }

        Set<String> sanitized = new HashSet<>();
        for (String tag : tags) {
            sanitized.add(anonymize(tag));
        }
        return Collections.unmodifiableSet(sanitized);
    }

    /**
     * Computes a deterministic pseudonym for a given entity value and category type.
     *
     * @param entity raw entity string
     * @param type   entity category type
     * @return deterministic pseudonym, e.g. {@code "[EMAIL_a9f3b2c1]"}
     */
    public String pseudonymize(String entity, String type) {
        if (entity == null || entity.isEmpty()) {
            return entity;
        }
        String hmacHex = computeHmacHex(entity.trim().toLowerCase());
        String shortHash = hmacHex.length() >= 8 ? hmacHex.substring(0, 8) : hmacHex;
        return "[" + type + "_" + shortHash + "]";
    }

    private String replaceWithPseudonym(String text, Pattern pattern, String type) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String match = matcher.group();
            String pseudo = pseudonymize(match, type);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(pseudo));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String computeHmacHex(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(saltBytes, HMAC_ALGORITHM);
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(hmacBytes.length * 2);
            for (byte b : hmacBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to compute HMAC-SHA256 pseudonym", e);
            return Integer.toHexString(Objects.hash(value, new String(saltBytes, StandardCharsets.UTF_8)));
        }
    }
}
