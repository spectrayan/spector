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
 * Utility for wrapping runtime exceptions in standardized {@link CognitivePathwayException}.
 */
public final class PathwayExceptions {

    private PathwayExceptions() {
        // utility class
    }

    /**
     * Wraps a runtime exception in a {@link CognitivePathwayException}, preserving existing
     * pathway exceptions without double-wrapping.
     *
     * @param pathwayName pathway name
     * @param e           runtime exception
     * @return cognitive pathway exception
     */
    public static CognitivePathwayException wrap(final String pathwayName, final RuntimeException e) {
        if (e instanceof CognitivePathwayException cpe) {
            return cpe;
        }
        return new CognitivePathwayException(
                pathwayName,
                "<entry>",
                Faults.kindOf(e),
                false,
                e);
    }
}
