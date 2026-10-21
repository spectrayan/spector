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
package com.spectrayan.spector.memory.cortex.consolidation;

import com.spectrayan.spector.kernel.id.TsidGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProvenanceKeysTest {

    @Test
    @DisplayName("TSID-shaped IDs, with or without a short prefix, map to the decoded TSID")
    void tsidIdsDecode() {
        long tsid = new TsidGenerator().generateLong();
        String encoded = TsidGenerator.encodeCrockford(tsid);

        assertThat(ProvenanceKeys.of(encoded)).isEqualTo(tsid);
        assertThat(ProvenanceKeys.of(AbstractConsolidator.MERGED_ID_PREFIX + encoded)).isEqualTo(tsid);
    }

    @Test
    @DisplayName("Other IDs map to a stable, non-zero hash")
    void otherIdsHash() {
        long key = ProvenanceKeys.of("dup-a");

        assertThat(key).isNotZero();
        assertThat(ProvenanceKeys.of("dup-a")).isEqualTo(key);
        assertThat(ProvenanceKeys.of("dup-b")).isNotEqualTo(key);
        assertThat(ProvenanceKeys.of("not-a-tsid-at-all")).isNotZero();
    }
}
