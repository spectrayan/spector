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
package com.spectrayan.spector.metrics.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

/**
 * Default observation convention mapping {@link MemoryObservationContext} fields to standard KeyValues.
 */
public class DefaultSpectorObservationConvention implements SpectorObservationConvention {

    public static final DefaultSpectorObservationConvention INSTANCE = new DefaultSpectorObservationConvention();

    @Override
    public String getName() {
        return null;
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(MemoryObservationContext context) {
        KeyValues kvs = KeyValues.of(
                KeyValue.of(SpectorObservationDocumentation.LowCardinalityKeys.OPERATION, context.getOperation()),
                KeyValue.of(SpectorObservationDocumentation.LowCardinalityKeys.STATUS, context.getStatus())
        );

        if (context.getTier() != null) {
            kvs = kvs.and(KeyValue.of(SpectorObservationDocumentation.LowCardinalityKeys.TIER, context.getTier()));
        }
        if (context.getNamespace() != null) {
            kvs = kvs.and(KeyValue.of(SpectorObservationDocumentation.LowCardinalityKeys.NAMESPACE, context.getNamespace()));
        }

        if (context.getError() != null) {
            kvs = kvs.and(KeyValue.of(SpectorObservationDocumentation.LowCardinalityKeys.ERROR,
                    context.getError().getClass().getSimpleName()));
        }

        return kvs;
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(MemoryObservationContext context) {
        KeyValues kvs = KeyValues.empty();

        if (context.getMemoryId() != null) {
            kvs = kvs.and(KeyValue.of(SpectorObservationDocumentation.HighCardinalityKeys.MEMORY_ID, context.getMemoryId()));
        }
        if (context.getSessionId() != null) {
            kvs = kvs.and(KeyValue.of(SpectorObservationDocumentation.HighCardinalityKeys.SESSION_ID, context.getSessionId()));
        }
        if (context.getQuery() != null) {
            kvs = kvs.and(KeyValue.of(SpectorObservationDocumentation.HighCardinalityKeys.QUERY, context.getQuery()));
        }
        if (context.getTaskId() != null) {
            kvs = kvs.and(KeyValue.of(SpectorObservationDocumentation.HighCardinalityKeys.TASK_ID, context.getTaskId()));
        }

        for (var entry : context.getCustomTags().entrySet()) {
            kvs = kvs.and(KeyValue.of(entry.getKey(), entry.getValue()));
        }

        return kvs;
    }
}
