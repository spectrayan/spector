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

/**
 * Spector Kernel — Sealed memory-mapped kernel for cognitive architecture (R11.4).
 */
module com.spectrayan.spector.kernel {
    requires transitive com.spectrayan.spector.core;
    requires transitive com.spectrayan.spector.commons;
    requires com.spectrayan.spector.cpu;
    requires org.slf4j;
    requires jdk.incubator.vector;

    // Public API surfaces (R11.4)
    exports com.spectrayan.spector.kernel.api;
    exports com.spectrayan.spector.kernel.region;
    exports com.spectrayan.spector.kernel.layout;
    exports com.spectrayan.spector.kernel.engram;
    exports com.spectrayan.spector.kernel.shape;
    exports com.spectrayan.spector.kernel.id;
    exports com.spectrayan.spector.kernel.storage;
    exports com.spectrayan.spector.kernel.error;
    exports com.spectrayan.spector.kernel.scratch;
    exports com.spectrayan.spector.kernel.score;
    exports com.spectrayan.spector.kernel.sync;

    // Qualified export for offline tooling only (R10.2, R11.5)
    exports com.spectrayan.spector.kernel.unsafe to
            com.spectrayan.spector.inspect,
            com.spectrayan.spector.cli;
}
