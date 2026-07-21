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

import picocli.CommandLine;

/**
 * Supplies the {@code spectorctl --version} banner from {@link BuildInfo}.
 *
 * <p>Wired into the root command via {@code versionProvider}, so both
 * {@code --version} and {@code -V} render the same output and exit with 0.</p>
 */
public class VersionProvider implements CommandLine.IVersionProvider {

    @Override
    public String[] getVersion() {
        BuildInfo info = BuildInfo.load();

        return new String[]{
                "Spector v" + info.version(),
                "  Build:    " + info.buildTime(),
                "  Commit:   " + info.commit() + " (" + info.branch() + ")",
                "  Java:     " + info.javaVersion() + " (" + info.javaVendor() + ")",
                "  OS:       " + info.osName() + " (" + info.osArch() + ")"
        };
    }                                                                                                                                               }