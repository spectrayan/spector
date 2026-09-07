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

import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Main entry point for the spector command-line tool.
 *
 * <p>Provides subcommands for managing a running Spector instance
 * via its REST API, starting embedded MCP, running diagnostic doctor,
 * or serving the Synapse daemon.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 * spector [--host HOST] [--port PORT] [--json] COMMAND
 *
 * Commands:
 *   init              Initialize local Spector configuration and storage directories
 *   doctor            Diagnose local environment, Java 25 Vector API, and dependencies
 *   mcp               Start the Spector MCP server (STDIO JSON-RPC 2.0 transport)
 *   serve             Start the Spector Synapse daemon (REST, SSE, and MCP HTTP)
 *   remember (ingest) Store or ingest documents/memories
 *   recall (search)   Recall or search documents/memories
 *   index             Manage indexes (create, delete, list)
 *   status            Show instance status
 *   memory            Manage cognitive memory subsystem
 * </pre>
 */
@Component
@Command(
        name = "spector",
        description = "Command-line tool and local runtime for Spector Cognitive Memory.",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        subcommands = {
                InitCommand.class,
                DoctorCommand.class,
                McpCommand.class,
                ServeCommand.class,
                RememberCommand.class,
                RecallCommand.class,
                IndexCommand.class,
                StatusCommand.class,
                MemoryCommand.class
        }
)
public class SpectorCtl implements Runnable {

    @Option(names = {"--host"}, description = "Spector host (default: localhost).",
            defaultValue = "localhost", scope = CommandLine.ScopeType.INHERIT)
    String host;

    @Option(names = {"--port"}, description = "Spector port (default: 7070).",
            defaultValue = "7070", scope = CommandLine.ScopeType.INHERIT)
    int port;

    @Option(names = {"--json"}, description = "Output in JSON format.",
            defaultValue = "false", scope = CommandLine.ScopeType.INHERIT)
    boolean json;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        // When invoked without a subcommand, print usage (satisfies Req 18.6)
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    public static void main(String[] args) {
        SpectorCliApplication.main(args);
    }

    /**
     * Handles execution exceptions to provide friendly error messages.
     * Satisfies Req 18.4 (connection errors) and 18.5 (invalid arguments).
     */
    public static class ExceptionHandler implements CommandLine.IExecutionExceptionHandler {
        @Override
        public int handleExecutionException(Exception ex, CommandLine commandLine,
                                            CommandLine.ParseResult parseResult) {
            String msg = ex.getMessage();
            if (msg == null || msg.isBlank()) {
                // NPE and similar exceptions have null messages — print the class + stack trace
                commandLine.getErr().println("Error: " + ex.getClass().getSimpleName());
                ex.printStackTrace(commandLine.getErr());
            } else {
                commandLine.getErr().println("Error: " + msg);
            }
            return 1;
        }
    }
}
