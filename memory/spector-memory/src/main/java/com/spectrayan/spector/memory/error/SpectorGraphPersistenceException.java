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
package com.spectrayan.spector.memory.error;

import com.spectrayan.spector.commons.error.ErrorCode;

/**
 * Exception thrown when graph persistence (save/load) fails.
 *
 * <p>Covers Hebbian, temporal, entity, and co-activation
 * graph file I/O ({@code SPE-310-010}).</p>
 *
 * @see ErrorCode#GRAPH_PERSISTENCE_FAILED
 */
public class SpectorGraphPersistenceException extends SpectorGraphException {

    private final String graphType;
    private final String path;

    public SpectorGraphPersistenceException(String graphType, Object path) {
        super(ErrorCode.GRAPH_PERSISTENCE_FAILED, graphType, path);
        this.graphType = graphType;
        this.path = String.valueOf(path);
    }

    public SpectorGraphPersistenceException(String graphType, Object path, Throwable cause) {
        super(ErrorCode.GRAPH_PERSISTENCE_FAILED, cause, graphType, path);
        this.graphType = graphType;
        this.path = String.valueOf(path);
    }

    /** Returns the type of graph that failed to persist. */
    public String getGraphType() {
        return graphType;
    }

    /** Returns the file path involved. */
    public String getPath() {
        return path;
    }
}
