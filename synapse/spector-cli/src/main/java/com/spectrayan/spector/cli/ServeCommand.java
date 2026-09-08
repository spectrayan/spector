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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.spectrayan.spector.synapse.SynapseApplication;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Starts the Spector Synapse daemon server (REST, SSE, and MCP HTTP endpoints).
 */
@Component
@Command(
        name = "serve",
        description = "Start the Spector Synapse server (REST, SSE, and MCP HTTP endpoints).",
        mixinStandardHelpOptions = true
)
public class ServeCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(ServeCommand.class);

    @ParentCommand
    private SpectorCtl parent;

    @Option(names = {"-p"}, description = "Port to listen on (alias for --port).")
    private Integer portOverride;

    @Option(names = {"--data-dir", "-d"}, description = "Persistence directory for on-disk memory.")
    private String dataDir;

    @Option(names = {"--config", "-c"}, description = "Path to custom spector.yml configuration file.")
    private String configFile;

    @Override
    public Integer call() {
        int port = (portOverride != null) ? portOverride : (parent != null ? parent.port : 7070);
        log.info("Starting Spector Synapse server on port {}", port);

        List<String> args = new ArrayList<>();
        args.add("--server.port=" + port);
        args.add("--armeria.ports[0].port=" + port);
        args.add("--armeria.ports[0].protocols[0]=HTTP");

        if (dataDir != null && !dataDir.isBlank()) {
            args.add("--spector.data-dir=" + dataDir);
            args.add("--spector.memory.persistence-path=" + dataDir);
            args.add("--spector.memory.persistence-mode=DISK");
        }

        if (configFile != null && !configFile.isBlank()) {
            args.add("--spring.config.additional-location=optional:file:" + configFile);
        }

        SynapseApplication.main(args.toArray(new String[0]));

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            log.info("Spector Synapse server interrupted, shutting down.");
            Thread.currentThread().interrupt();
        }

        return 0;
    }
}
