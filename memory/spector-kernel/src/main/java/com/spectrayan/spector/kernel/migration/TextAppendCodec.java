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
package com.spectrayan.spector.kernel.migration;

import com.spectrayan.spector.kernel.migration.FormatCodec;
import com.spectrayan.spector.kernel.migration.CodecStep;
import com.spectrayan.spector.kernel.layout.TextBlobLayout;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Set;

/**
 * Codec binding for TextBlobMemory format.
 */
public final class TextAppendCodec implements FormatCodec<TextBlobLayout> {

    private final TextBlobLayout layout = new TextBlobLayout();

    @Override
    public TextBlobLayout layout() {
        return layout;
    }

    @Override
    public Set<Integer> legacyMagics() {
        return Set.of(LegacyTextToSmkmStep.FROM_FORMAT.magic());
    }

    @Override
    public int versionOf(int magic, MemorySegment headerPrefix) {
        return 1;
    }

    @Override
    public List<CodecStep> steps() {
        return List.of(new LegacyTextToSmkmStep());
    }
}
