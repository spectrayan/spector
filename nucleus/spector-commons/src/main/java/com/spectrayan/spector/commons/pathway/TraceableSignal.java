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

import java.util.List;

/**
 * Interface implemented by signals that collect step-by-step relay execution traces.
 */
public interface TraceableSignal {

    /**
     * Checks if execution tracing is enabled for this signal.
     *
     * @return true if tracing is active
     */
    boolean isTraceEnabled();

    /**
     * Records a relay execution trace.
     *
     * @param trace the trace record to append
     */
    void recordTrace(RelayTrace trace);

    /**
     * Returns the recorded execution traces.
     *
     * @return unmodifiable list of trace records
     */
    List<RelayTrace> traces();
}
