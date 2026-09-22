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
package com.spectrayan.spector.mel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.memory.SpectorMemory;

/**
 * Interactive REPL (Read-Eval-Print Loop) for the Memory Engine Language.
 *
 * <p>Provides a {@code redis-cli}-style interactive shell for developers
 * and testers to debug and interact with the Spector memory engine.
 * Supports multi-line input (continues until {@code ;}) and basic
 * command-line editing via standard I/O.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * SpectorMemory memory = SpectorMemory.builder().build();
 * new MelRepl(memory).run();
 * }</pre>
 *
 * <h3>Special Commands</h3>
 * <ul>
 *   <li>{@code \q} or {@code exit} — quit the REPL</li>
 *   <li>{@code \h} or {@code help} — show help</li>
 *   <li>{@code \c} — clear the current multi-line buffer</li>
 * </ul>
 */
public final class MelRepl {

    private static final Logger log = LoggerFactory.getLogger(MelRepl.class);

    private static final String BANNER = """
            ╔══════════════════════════════════════════════════════════╗
            ║  Spector MEL — Memory Engine Language Shell             ║
            ║  Type MEL statements ending with ;                     ║
            ║  \\q to quit, \\h for help, \\c to clear buffer          ║
            ╚══════════════════════════════════════════════════════════╝
            """;

    private static final String HELP = """
            MEL Statement Reference (Phase 1):
            
              REMEMBER TEXT 'content' INTO SEMANTIC
                  [TAGS ['tag1', 'tag2']] [IMPORTANCE n] [VALENCE n];
              
              RECALL 'query' [TOP n] [TAGS CONTAIN ['tag']]
                  [IMPORTANCE >= n] [TIERS SEMANTIC, EPISODIC];
              
              CONSOLIDATE id1, id2 INTO SEMANTIC [AS TEXT 'summary'];
              
              FORGET id1, id2 TOMBSTONE | SUPPRESS | WEAKEN;
              
              EXPLAIN RECALL 'query' TOP n;
              
              INTROSPECT [TIER SEMANTIC];
            
            Special commands:
              \\q, exit   — quit
              \\h, help   — this message
              \\c         — clear multi-line buffer
            """;

    private final SpectorMemory memory;
    private final MelEvaluator evaluator;
    private final BufferedReader reader;
    private final PrintStream out;

    public MelRepl(SpectorMemory memory) {
        this(memory, new BufferedReader(new InputStreamReader(System.in)), System.out);
    }

    /** Testable constructor with injectable I/O. */
    public MelRepl(SpectorMemory memory, BufferedReader reader, PrintStream out) {
        this.memory = memory;
        this.evaluator = new MelEvaluator(memory);
        this.reader = reader;
        this.out = out;
    }

    /**
     * Starts the REPL loop. Blocks until the user quits.
     */
    public void run() {
        out.print(BANNER);
        out.printf("Connected to memory engine (%d memories).%n%n", memory.totalMemories());

        var buffer = new StringBuilder();
        boolean running = true;

        while (running) {
            String prompt = buffer.isEmpty() ? "mel> " : " ..> ";
            out.print(prompt);
            out.flush();

            String line;
            try {
                line = reader.readLine();
            } catch (IOException e) {
                break;
            }

            if (line == null) {
                break; // EOF
            }

            String trimmed = line.trim();

            // Special commands
            if (buffer.isEmpty()) {
                if (trimmed.equals("\\q") || trimmed.equalsIgnoreCase("exit")) {
                    out.println("Goodbye.");
                    running = false;
                    continue;
                }
                if (trimmed.equals("\\h") || trimmed.equalsIgnoreCase("help")) {
                    out.print(HELP);
                    continue;
                }
            }

            if (trimmed.equals("\\c")) {
                buffer.setLength(0);
                out.println("Buffer cleared.");
                continue;
            }

            buffer.append(line).append('\n');

            // Check if statement is complete (ends with ;)
            if (trimmed.endsWith(";")) {
                executeBuffer(buffer.toString());
                buffer.setLength(0);
            }
        }
    }

    private void executeBuffer(String input) {
        try {
            var statements = MelParser.parseScript(input);
            for (var stmt : statements) {
                MelResult result = evaluator.evaluate(stmt);
                if (result.isOk()) {
                    out.println(result.output());
                } else {
                    out.printf("ERROR: %s%n", result.error().orElse("Unknown error"));
                }
                out.printf("(%d ms)%n%n", result.elapsedMs());
            }
        } catch (MelParser.MelParseException e) {
            out.printf("PARSE ERROR: %s%n%n", e.getMessage());
        } catch (Exception e) {
            out.printf("ERROR: %s%n%n", e.getMessage());
            log.debug("REPL execution error", e);
        }
    }
}
