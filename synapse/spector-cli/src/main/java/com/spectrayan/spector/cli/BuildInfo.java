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
package com.spectrayan.spector.cli;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Build and runtime metadata reported by {@code spectorctl --version}.
 *
 * <p>Version, build timestamp and git coordinates are baked in at build time
 * via Maven resource filtering of {@code spector-cli-build.properties}. Java
 * and OS details are read from system properties at runtime, so they describe
 * the JVM actually executing the CLI rather than the machine that built it.</p>
 *
 * <p>Every field degrades to {@value #UNKNOWN} rather than failing: the CLI
 * must be able to report its version even when built from a source tarball
 * with no git history, or when the metadata resource is missing entirely.</p>
 *
 * @param version     project version, e.g. {@code 0.1.0-alpha}
 * @param buildTime   UTC build timestamp in ISO-8601, e.g. {@code 2026-07-21T10:08:20Z}
 * @param commit      abbreviated git commit SHA, e.g. {@code 147070a}
 * @param branch      git branch the build was cut from
 * @param javaVersion running JVM version
 * @param javaVendor  running JVM vendor
 * @param osName      host operating system name
 * @param osArch      host CPU architecture
 */
public record BuildInfo(
        String version,
        String buildTime,
        String commit,
        String branch,
        String javaVersion,
        String javaVendor,
        String osName,
        String osArch
) {

    /** Classpath location of the Maven-filtered metadata resource. */
    private static final String RESOURCE = "/spector-cli-build.properties";

    /** Placeholder substituted for any value that is absent or unresolved. */
    static final String UNKNOWN = "unknown";

    /**
     * Loads build metadata from the classpath, falling back to {@value #UNKNOWN}
     * for anything unavailable.
     *
     * @return populated build info; never {@code null} and never throws
     */
    public static BuildInfo load() {
        var props = new Properties();
        try (var in = BuildInfo.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // Unreadable metadata is not worth failing --version over.
            // An empty Properties yields an all-unknown BuildInfo.
        }
        return fromProperties(props);
    }

    /**
     * Builds an instance from already-loaded properties.
     * Package-private so tests can exercise the fallback paths without a build.
     *
     * @param props filtered build properties, possibly empty
     * @return populated build info
     */
    static BuildInfo fromProperties(Properties props) {
        return new BuildInfo(
                clean(props.getProperty("version")),
                clean(props.getProperty("buildTime")),
                clean(props.getProperty("commit")),
                clean(props.getProperty("branch")),
                clean(System.getProperty("java.version")),
                clean(System.getProperty("java.vendor")),
                clean(System.getProperty("os.name")),
                clean(System.getProperty("os.arch"))
        );
    }

    /**
     * Normalises a raw value, mapping anything unusable to {@value #UNKNOWN}.
     *
     * <p>A value still starting with {@code ${} is an unresolved Maven
     * placeholder — this happens when the git-commit-id plugin finds no
     * repository, so the build proceeds without commit coordinates.</p>
     *
     * @param value raw property value, possibly {@code null}
     * @return trimmed value, or {@value #UNKNOWN}
     */
    static String clean(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty() || trimmed.startsWith("${")) {
            return UNKNOWN;
        }
        return trimmed;
    }
}
