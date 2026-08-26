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
package com.spectrayan.spector.provider.onnx;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Fast, thread-safe WordPiece tokenizer for in-process transformer embedding models.
 *
 * <p>Supports BERT, MiniLM, BGE, and custom transformer vocabularies with
 * special token handling ([CLS], [SEP], [UNK], [PAD]).</p>
 */
public final class NativeWordPieceTokenizer {

    public static final int CLS_TOKEN_ID = 101;
    public static final int SEP_TOKEN_ID = 102;
    public static final int UNK_TOKEN_ID = 100;
    public static final int PAD_TOKEN_ID = 0;
    public static final int DEFAULT_MAX_LENGTH = 512;

    private final Map<String, Integer> vocab;
    private final int maxSequenceLength;

    public NativeWordPieceTokenizer() {
        this(null, DEFAULT_MAX_LENGTH);
    }

    public NativeWordPieceTokenizer(Map<String, Integer> customVocab, int maxSequenceLength) {
        this.vocab = customVocab != null ? Collections.unmodifiableMap(new HashMap<>(customVocab)) : null;
        this.maxSequenceLength = maxSequenceLength > 0 ? maxSequenceLength : DEFAULT_MAX_LENGTH;
    }

    /**
     * Loads a vocabulary from a standard vocab.txt file.
     */
    public static NativeWordPieceTokenizer fromVocabFile(Path vocabPath, int maxSequenceLength) throws IOException {
        Map<String, Integer> map = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(vocabPath)) {
            String line;
            int idx = 0;
            while ((line = reader.readLine()) != null) {
                map.put(line.trim(), idx++);
            }
        }
        return new NativeWordPieceTokenizer(map, maxSequenceLength);
    }

    /**
     * Tokenizes input text into input_ids, attention_mask, and token_type_ids.
     */
    public TokenizedInput tokenize(String text) {
        if (text == null || text.isBlank()) {
            return emptyTokenized();
        }

        List<Integer> tokenIds = new ArrayList<>();
        tokenIds.add(CLS_TOKEN_ID);

        String[] words = text.toLowerCase(Locale.ROOT).split("\\s+");
        for (String word : words) {
            if (word.isBlank()) continue;
            if (tokenIds.size() >= maxSequenceLength - 1) break;

            if (vocab != null && !vocab.isEmpty()) {
                encodeWordPiece(word, tokenIds);
            } else {
                // In built-in fallback mode, hash tokens stably to 30,522 vocab space
                int id = (Math.abs(word.hashCode()) % 30000) + 105;
                tokenIds.add(id);
            }
        }

        tokenIds.add(SEP_TOKEN_ID);

        int len = Math.min(tokenIds.size(), maxSequenceLength);
        long[] inputIds = new long[len];
        long[] attentionMask = new long[len];
        long[] tokenTypeIds = new long[len];

        for (int i = 0; i < len; i++) {
            inputIds[i] = tokenIds.get(i);
            attentionMask[i] = 1L;
            tokenTypeIds[i] = 0L;
        }

        return new TokenizedInput(inputIds, attentionMask, tokenTypeIds, len);
    }

    private void encodeWordPiece(String word, List<Integer> tokenIds) {
        int start = 0;
        int len = word.length();
        while (start < len) {
            if (tokenIds.size() >= maxSequenceLength - 1) break;
            int end = len;
            String curSubstr = null;
            while (start < end) {
                String sub = word.substring(start, end);
                if (start > 0) {
                    sub = "##" + sub;
                }
                if (vocab.containsKey(sub)) {
                    curSubstr = sub;
                    break;
                }
                end--;
            }
            if (curSubstr == null) {
                tokenIds.add(UNK_TOKEN_ID);
                break;
            }
            tokenIds.add(vocab.get(curSubstr));
            start = end;
        }
    }

    private TokenizedInput emptyTokenized() {
        return new TokenizedInput(
                new long[]{CLS_TOKEN_ID, SEP_TOKEN_ID},
                new long[]{1L, 1L},
                new long[]{0L, 0L},
                2
        );
    }

    public record TokenizedInput(
            long[] inputIds,
            long[] attentionMask,
            long[] tokenTypeIds,
            int length
    ) {}
}
