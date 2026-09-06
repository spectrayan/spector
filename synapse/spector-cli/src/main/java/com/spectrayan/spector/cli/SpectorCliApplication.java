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

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Spring Boot host application for the {@code spectorctl} CLI.
 * <p>
 * Unifies CLI configuration and lifecycle under the {@code spector-spring}
 * auto-configuration infrastructure, while hosting Picocli commands
 * via {@link SpectorPicocliRunner} and {@link SpringPicocliFactory}.
 * </p>
 */
@SpringBootApplication(exclude = {
        org.springframework.boot.batch.autoconfigure.BatchAutoConfiguration.class,
        org.springframework.boot.batch.autoconfigure.BatchJobLauncherAutoConfiguration.class
})
public class SpectorCliApplication {

    public static void main(String[] args) {
        String activeProfile = determineProfile(args);

        int exitCode = SpringApplication.exit(
                new SpringApplicationBuilder(SpectorCliApplication.class)
                        .web(WebApplicationType.NONE)
                        .bannerMode(Banner.Mode.OFF)
                        .logStartupInfo(false)
                        .profiles(activeProfile)
                        .initializers(new SpectorCliConfigInitializer(args))
                        .properties("spring.main.lazy-initialization=true")
                        .run(args)
        );

        System.exit(exitCode);
    }

    /**
     * Inspects CLI arguments prior to context refresh to route to the optimal profile:
     * <ul>
     *   <li>{@code cli-remote}: Default for remote calls, search/recall, status, and help flags.
     *       Disables local cognitive memory beans for instant startup.</li>
     *   <li>{@code cli-embedded}: Activated when running local operations (e.g. {@code mcp},
     *       {@code memory}, {@code remember --root}, {@code remember --config}, {@code --offline}).
     *       Enables full Spector cognitive memory auto-configuration.</li>
     * </ul>
     *
     * @param args CLI command-line arguments
     * @return active profile name
     */
    public static String determineProfile(String[] args) {
        if (args == null || args.length == 0) {
            return "cli-remote";
        }
        // Help and version flags always stay remote for sub-second execution
        for (String arg : args) {
            if (arg.equals("-h") || arg.equals("--help") || arg.equals("-V") || arg.equals("--version")) {
                return "cli-remote";
            }
        }

        boolean hasRemoteFlags = false;
        boolean hasEmbeddedFlags = false;
        boolean isRememberOrIngest = false;
        boolean isMemory = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--profile=cli-embedded")
                    || (arg.equals("--profile") && i + 1 < args.length && args[i + 1].equals("cli-embedded"))) {
                return "cli-embedded";
            }
            if (arg.equals("--profile=cli-remote")
                    || (arg.equals("--profile") && i + 1 < args.length && args[i + 1].equals("cli-remote"))) {
                return "cli-remote";
            }
            if (arg.equals("mcp") || arg.equals("--root") || arg.equals("--offline")
                    || arg.equals("--local") || arg.equals("--embedded")) {
                hasEmbeddedFlags = true;
            }
            if (arg.equals("memory")) {
                isMemory = true;
            }
            if (arg.equals("remember") || arg.equals("ingest")) {
                isRememberOrIngest = true;
            }
            if (arg.startsWith("--content") || arg.startsWith("--file")) {
                hasRemoteFlags = true;
            }
        }

        if (hasEmbeddedFlags && !hasRemoteFlags) {
            return "cli-embedded";
        }
        if (isMemory) {
            return "cli-embedded";
        }
        if (isRememberOrIngest && !hasRemoteFlags) {
            return "cli-embedded";
        }

        return "cli-remote";
    }
}
