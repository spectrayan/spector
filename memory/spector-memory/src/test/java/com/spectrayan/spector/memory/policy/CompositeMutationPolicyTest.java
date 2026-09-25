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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CompositeMutationPolicyTest {

    @Test
    void delegatesToAll() {
        AtomicBoolean aWrite = new AtomicBoolean();
        AtomicBoolean aDel = new AtomicBoolean();
        MutationPolicy policyA = new MutationPolicy() {
            public void checkWrite(WriteRequest r) { aWrite.set(true); }
            public void checkDeletion(DeletionRequest r) { aDel.set(true); }
        };
        
        AtomicBoolean bWrite = new AtomicBoolean();
        AtomicBoolean bDel = new AtomicBoolean();
        MutationPolicy policyB = new MutationPolicy() {
            public void checkWrite(WriteRequest r) { bWrite.set(true); }
            public void checkDeletion(DeletionRequest r) { bDel.set(true); }
        };

        CompositeMutationPolicy comp = new CompositeMutationPolicy(List.of(policyA, policyB));
        comp.checkWrite(WriteRequest.remember("ns", "mem", 0, 0));
        assertTrue(aWrite.get());
        assertTrue(bWrite.get());
        
        comp.checkDeletion(DeletionRequest.forget("ns", "mem"));
        assertTrue(aDel.get());
        assertTrue(bDel.get());
    }
}
