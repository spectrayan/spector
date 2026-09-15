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
package com.spectrayan.spector.commons.pathway;

import com.spectrayan.spector.commons.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AttributeBag")
class AttributeBagTest {

    private static final Key<String> USER_QUERY = Key.of("userQuery", String.class);
    private static final Key<Integer> RETRY_COUNT = Key.of("retryCount", Integer.class);
    private static final Key<Boolean> DEGRADED = Key.of("degraded", Boolean.class);

    @Test
    @DisplayName("Stores and retrieves typed values")
    void storeAndRetrieve() {
        var bag = AttributeBag.create();
        bag.put(USER_QUERY, "neural recall");
        bag.put(RETRY_COUNT, 3);

        assertThat(bag.contains(USER_QUERY)).isTrue();
        assertThat(bag.get(USER_QUERY)).isEqualTo("neural recall");
        assertThat(bag.find(USER_QUERY)).contains("neural recall");

        assertThat(bag.contains(RETRY_COUNT)).isTrue();
        assertThat(bag.get(RETRY_COUNT)).isEqualTo(3);

        assertThat(bag.contains(DEGRADED)).isFalse();
        assertThat(bag.find(DEGRADED)).isEmpty();
    }

    @Test
    @DisplayName("Throws CognitivePathwayException with CONTRACT when key is absent on get")
    void throwsContractExceptionOnAbsentKey() {
        var bag = AttributeBag.create();
        assertThatThrownBy(() -> bag.get(USER_QUERY))
                .isInstanceOf(CognitivePathwayException.class)
                .satisfies(e -> {
                    var cpe = (CognitivePathwayException) e;
                    assertThat(cpe.errorCode()).isEqualTo(ErrorCode.MEMORY_PATHWAY_FAILED);
                    assertThat(cpe.kind()).isEqualTo(FaultKind.CONTRACT);
                });
    }

    @Test
    @DisplayName("Snapshot creates shallow copy that isolates modifications")
    void snapshotIsolatesModifications() {
        var bag = AttributeBag.create();
        bag.put(USER_QUERY, "initial");

        var snapshot = bag.snapshot();
        assertThat(snapshot.get(USER_QUERY)).isEqualTo("initial");

        bag.put(USER_QUERY, "modified");
        assertThat(bag.get(USER_QUERY)).isEqualTo("modified");
        assertThat(snapshot.get(USER_QUERY)).isEqualTo("initial");

        snapshot.put(RETRY_COUNT, 5);
        assertThat(bag.contains(RETRY_COUNT)).isFalse();
        assertThat(snapshot.contains(RETRY_COUNT)).isTrue();
    }

    @Test
    @DisplayName("Removing value by putting null")
    void removingValue() {
        var bag = AttributeBag.create();
        bag.put(USER_QUERY, "query");
        assertThat(bag.contains(USER_QUERY)).isTrue();

        bag.put(USER_QUERY, null);
        assertThat(bag.contains(USER_QUERY)).isFalse();
        assertThat(bag.find(USER_QUERY)).isEmpty();
    }
}
