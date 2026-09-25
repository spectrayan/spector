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
package com.spectrayan.spector.synapse.ingestion.sensory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.security.CodeSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that the runtime classpath bundles Junrar 7.6.1+ to prevent CVE-2026-86071
 * directory traversal vulnerabilities.
 */
@DisplayName("Junrar Dependency Security Version Gate (CVE-2026-86071)")
class JunrarVersionSecurityTest {

    @Test
    @DisplayName("Verify Junrar version on classpath is 7.6.1 or later to remediate CVE-2026-86071")
    void testJunrarVersionPatched() throws Exception {
        Class<?> archiveClass = Class.forName("com.github.junrar.Archive");
        assertNotNull(archiveClass, "com.github.junrar.Archive must be resolvable");

        CodeSource codeSource = archiveClass.getProtectionDomain().getCodeSource();
        assertNotNull(codeSource, "CodeSource for Junrar must be available");
        URL location = codeSource.getLocation();
        assertNotNull(location, "Location for Junrar jar must not be null");

        String locationStr = location.toString();
        assertFalse(locationStr.contains("junrar-7.6.0.jar"),
                "Vulnerable Junrar 7.6.0 jar detected in classpath: " + locationStr);

        Package pkg = archiveClass.getPackage();
        String version = pkg != null ? pkg.getImplementationVersion() : null;
        if (version != null && !version.isBlank()) {
            assertNotEquals("7.6.0", version, "Vulnerable Junrar 7.6.0 must not be present on classpath");
            assertTrue(version.startsWith("7.6.1") || version.compareTo("7.6.1") >= 0,
                    "Junrar version must be at least 7.6.1, but found: " + version);
        }
    }
}
