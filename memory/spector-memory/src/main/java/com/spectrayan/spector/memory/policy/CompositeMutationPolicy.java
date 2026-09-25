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

import java.util.List;
import java.util.Objects;

public class CompositeMutationPolicy implements MutationPolicy {

    private final List<MutationPolicy> policies;

    public CompositeMutationPolicy(List<MutationPolicy> policies) {
        this.policies = List.copyOf(Objects.requireNonNull(policies, "policies"));
    }

    @Override
    public void checkDeletion(DeletionRequest request) {
        for (MutationPolicy policy : policies) {
            policy.checkDeletion(request);
        }
    }

    @Override
    public void checkWrite(WriteRequest request) {
        for (MutationPolicy policy : policies) {
            policy.checkWrite(request);
        }
    }
}
