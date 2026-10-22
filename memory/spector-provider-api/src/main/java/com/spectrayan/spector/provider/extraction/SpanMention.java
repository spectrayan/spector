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
package com.spectrayan.spector.provider.extraction;

/**
 * A labeled span in the original text.
 *
 * @param text  the surface form, sliced from the source
 * @param label the label that scored this span
 * @param start inclusive character offset
 * @param end   exclusive character offset
 * @param score sigmoid probability in {@code [0, 1]}
 */
public record SpanMention(String text, String label, int start, int end, float score) {
}
