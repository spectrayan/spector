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
package com.spectrayan.spector.memory.policy;

import com.spectrayan.spector.commons.error.SpectorServerException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class FencingMutationPolicyTest {

    @Test
    void checkWrite_acceptsValidEpoch() {
        AtomicLong epoch = new AtomicLong(5);
        FencingMutationPolicy policy = new FencingMutationPolicy(epoch::get);
        WriteRequest req = WriteRequest.remember("ns1", "mem1", 5, 100);
        assertDoesNotThrow(() -> policy.checkWrite(req));
        
        WriteRequest req2 = WriteRequest.remember("ns1", "mem1", 6, 100);
        assertDoesNotThrow(() -> policy.checkWrite(req2));
    }

    @Test
    void checkWrite_rejectsStaleEpoch() {
        AtomicLong epoch = new AtomicLong(5);
        FencingMutationPolicy policy = new FencingMutationPolicy(epoch::get);
        WriteRequest req = WriteRequest.remember("ns1", "mem1", 4, 100);
        
        SpectorServerException ex = assertThrows(SpectorServerException.class, () -> policy.checkWrite(req));
        assertTrue(ex.getMessage().contains("is older than current epoch"));
    }
}
