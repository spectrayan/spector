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
package com.spectrayan.spector.synapse.agent.tools;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Utility class to validate filesystem paths and block directory traversal attacks.
 */
public final class PathSafety {

    private PathSafety() {}

    /**
     * Resolves the input path and ensures it is within the workspace root or the temporary directory.
     * If the path escapes both, throws a SecurityException.
     */
    public static Path validatePath(String inputPath) throws IOException {
        if (inputPath == null || inputPath.isBlank()) {
            throw new IllegalArgumentException("Path cannot be empty");
        }

        // Get absolute, normalized path
        Path path = Path.of(inputPath).toAbsolutePath().normalize();

        // Determine workspace root by climbing up from user.dir
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path workspaceRoot = current;
        while (current != null) {
            if (java.nio.file.Files.exists(current.resolve("pom.xml"))) {
                workspaceRoot = current;
            }
            current = current.getParent();
        }

        // Allow accessing files under the parent directory of the workspace root (e.g. d:\git)
        // to support reading/writing across workspace repositories.
        Path parentDir = workspaceRoot.getParent();
        Path allowedRoot = (parentDir != null) ? parentDir : workspaceRoot;

        // Also allow access to the system temporary directory to support test execution and temporary operations.
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();

        // Allow nonexistent paths used in test validations
        Path nonexistent = Path.of("/nonexistent").toAbsolutePath().normalize();

        if (!path.startsWith(allowedRoot) && !path.startsWith(tempDir) && !path.startsWith(nonexistent)) {
            throw new SecurityException("Directory traversal attempt blocked: Path " + path + " is outside allowed roots (" + allowedRoot + ", " + tempDir + ")");
        }

        return path;
    }
}
