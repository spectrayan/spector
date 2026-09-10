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
package com.spectrayan.spector.kernel.id;

import com.spectrayan.spector.kernel.id.MemoryId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryIdTest {

    @Test
    @DisplayName("factoryMethodSetsPartitionSeqToZero")
    void factoryMethodSetsPartitionSeqToZero() {
        MemoryId id = MemoryId.of("ns", "name");
        assertThat(id.partitionSeq()).isZero();
    }

    @Test
    @DisplayName("toStringFormatsWithoutPartition")
    void toStringFormatsWithoutPartition() {
        MemoryId id = MemoryId.of("ns", "name");
        assertThat(id.toString()).isEqualTo("ns/name");
    }

    @Test
    @DisplayName("toStringFormatsWithPartition")
    void toStringFormatsWithPartition() {
        MemoryId id = new MemoryId("ns", "name", 2);
        assertThat(id.toString()).isEqualTo("ns/name#2");
    }

    @Test
    @DisplayName("compareToOrdersByNamespaceThenName")
    void compareToOrdersByNamespaceThenName() {
        MemoryId id1 = new MemoryId("a", "x", 0);
        MemoryId id2 = new MemoryId("b", "x", 0);
        MemoryId id3 = new MemoryId("b", "y", 0);
        MemoryId id4 = new MemoryId("b", "y", 1);
        
        List<MemoryId> list = Arrays.asList(id3, id1, id4, id2);
        list.sort(null);
        
        assertThat(list).containsExactly(id1, id2, id3, id4);
    }

    @Test
    @DisplayName("equalityByValueNotReference")
    void equalityByValueNotReference() {
        MemoryId id1 = new MemoryId("ns", "name", 1);
        MemoryId id2 = new MemoryId("ns", "name", 1);
        
        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }
}
