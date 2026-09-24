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
package com.spectrayan.spector.batch.exporting;

/**
 * Data record serialized as JSONL into {@code graph/facts.jsonl} representing
 * temporal knowledge facts and bitemporal intervals (ADR-0045, memory-portability R1.3).
 *
 * @param factId          fact identifier
 * @param subject         subject entity name
 * @param predicate       predicate relation type name
 * @param object          object entity name
 * @param validFrom       validity start timestamp in epoch seconds
 * @param validTo         validity end timestamp in epoch seconds (Long.MAX_VALUE for open-ended)
 * @param txTime          transaction assertion timestamp in epoch milliseconds
 * @param confidence      fact confidence score [0.0, 1.0]
 * @param retractsFactId  superseded fact identifier (-1 if none)
 * @param flags           fact state flags
 */
public record ExportFactRecord(
        int factId,
        String subject,
        String predicate,
        String object,
        long validFrom,
        long validTo,
        long txTime,
        float confidence,
        int retractsFactId,
        byte flags
) {}
