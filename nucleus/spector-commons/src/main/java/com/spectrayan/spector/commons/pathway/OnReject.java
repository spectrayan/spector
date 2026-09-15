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

/**
 * Action taken when a {@link BulkheadRelay} cannot acquire a permit within the wait period.
 */
public enum OnReject {

    /** Throw a {@link CognitivePathwayException} with fault kind {@link FaultKind#TRANSIENT}. */
    FAIL,

    /** Mark the stage as bypassed on the {@link ConductionOutcome} and continue the relay chain. */
    BYPASS
}
