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
package com.spectrayan.spector.connector.spi;

import com.spectrayan.spector.connector.model.ExecutionRecord;

import java.util.List;

/**
 * SPI for recording connector execution history.
 */
public interface ExecutionLogger {

    /** Log an execution record. */
    void log(ExecutionRecord record);

    /** Get execution history for a route. */
    List<ExecutionRecord> getHistory(String routeId, int limit);

    /** Get the latest execution for a route. */
    java.util.Optional<ExecutionRecord> getLatest(String routeId);
}
