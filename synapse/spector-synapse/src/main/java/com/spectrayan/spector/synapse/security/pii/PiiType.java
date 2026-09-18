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
package com.spectrayan.spector.synapse.security.pii;

/**
 * Categories of personally identifiable information detected before LLM calls.
 *
 * <p>{@link #PERSON} is reserved for a future optional local NER/ONNX path;
 * default Phileas policies ship identifier filters only (no remote Ph-Eye).</p>
 */
public enum PiiType {
    EMAIL,
    PHONE,
    SSN,
    CREDIT_CARD,
    IP_ADDRESS,
    PERSON,
    ADDRESS
}
