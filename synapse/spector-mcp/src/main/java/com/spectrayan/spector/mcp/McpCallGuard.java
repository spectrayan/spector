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
package com.spectrayan.spector.mcp;

import java.util.Map;

/**
 * Optional per-call check installed by the stdio server before tools run.
 * HTTP MCP does not install this; it enforces the pin on the request thread.
 */
public final class McpCallGuard {

    /** @return an error message to return to the client, or {@code null} to proceed */
    @FunctionalInterface
    public interface Check {
        String reject(Map<String, Object> args);
    }

    private static volatile Check check;

    private McpCallGuard() {
    }

    public static void install(Check next) {
        check = next;
    }

    public static void clear() {
        check = null;
    }

    public static String reject(Map<String, Object> args) {
        Check current = check;
        return current == null ? null : current.reject(args);
    }
}
