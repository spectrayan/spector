/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.agent.tools;
import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.runtime.SpectorRuntime;
import io.modelcontextprotocol.spec.McpSchema;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Evaluates mathematical expressions — basic arithmetic, percentages,
 * and common math functions (sqrt, sin, cos, log, etc.).
 *
 * <p>Uses a safe recursive-descent parser — no script engine or eval().</p>
 */
@Component
public class CalculatorTool extends McpToolHandler {

    private static final Logger log = LoggerFactory.getLogger(CalculatorTool.class);

    @Override public String name() { return "calculator"; }

    @Override public String description() {
        return "Evaluate a mathematical expression. Supports: +, -, *, /, %, ^, sqrt, abs, sin, cos, tan, log, pi, e.";
    }

    @Override public McpToolCategory category() { return McpToolCategory.DATA; }
    @Override public boolean isWriteTool() { return false; }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "expression", Map.of("type", "string",
                                "description", "Math expression to evaluate (e.g., '2 + 3 * 4', 'sqrt(16)', '15% of 200')")
                ),
                "required", java.util.List.of("expression")
        );
    }

    @Override
    public io.modelcontextprotocol.spec.McpSchema.CallToolResult execute(com.spectrayan.spector.runtime.SpectorRuntime runtime, Map<String, Object> args) throws Exception {
        return textResult(executeInternal(args));
    }

    private String executeInternal(Map<String, Object> args) throws Exception {
        var expr = (String) args.get("expression");
        if (expr == null || expr.isBlank()) return "Error: Missing required argument: expression";

        // Preprocess common patterns
        expr = expr.trim()
                .replaceAll("(?i)(\\d+)%\\s*of\\s*(\\d+)", "($1/100.0)*$2")
                .replaceAll("(?i)sqrt\\((.*?)\\)", "Math.sqrt($1)")
                .replaceAll("(?i)abs\\((.*?)\\)", "Math.abs($1)")
                .replaceAll("(?i)sin\\((.*?)\\)", "Math.sin($1)")
                .replaceAll("(?i)cos\\((.*?)\\)", "Math.cos($1)")
                .replaceAll("(?i)tan\\((.*?)\\)", "Math.tan($1)")
                .replaceAll("(?i)log\\((.*?)\\)", "Math.log($1)")
                .replaceAll("(?i)\\bpi\\b", String.valueOf(Math.PI))
                .replaceAll("(?i)\\be\\b", String.valueOf(Math.E))
                .replace("^", "**");

        try {
            double result = evaluateExpression(expr);
            log.debug("[CalculatorTool] {} = {}", args.get("expression"), result);
            if (result == Math.floor(result) && !Double.isInfinite(result) && result >= Long.MIN_VALUE && result <= Long.MAX_VALUE) {
                return args.get("expression") + " = " + (long) result;
            }
            return args.get("expression") + " = " + result;
        } catch (Exception e) {
            return "Error evaluating expression: " + e.getMessage();
        }
    }

    /** Recursive-descent expression evaluator. */
    private static double evaluateExpression(String expr) {
        return new Object() {
            int pos = -1; int ch;
            void nextChar() { ch = (++pos < expr.length()) ? expr.charAt(pos) : -1; }
            boolean eat(int c) { while (ch == ' ') nextChar(); if (ch == c) { nextChar(); return true; } return false; }

            double parse() { nextChar(); double x = parseExpr(); if (pos < expr.length()) throw new RuntimeException("Unexpected: " + (char) ch); return x; }
            double parseExpr() { double x = parseTerm(); for (;;) { if (eat('+')) x += parseTerm(); else if (eat('-')) x -= parseTerm(); else return x; } }
            double parseTerm() { double x = parseFactor(); for (;;) { if (eat('*')) { if (eat('*')) x = Math.pow(x, parseFactor()); else x *= parseFactor(); } else if (eat('/')) x /= parseFactor(); else if (eat('%')) x %= parseFactor(); else return x; } }

            double parseFactor() {
                if (eat('+')) return +parseFactor();
                if (eat('-')) return -parseFactor();
                double x; int startPos = this.pos;
                if (eat('(')) { x = parseExpr(); if (!eat(')')) throw new RuntimeException("Missing )"); }
                else if ((ch >= '0' && ch <= '9') || ch == '.') { while ((ch >= '0' && ch <= '9') || ch == '.') nextChar(); x = Double.parseDouble(expr.substring(startPos, this.pos)); }
                else if (ch >= 'A' && ch <= 'z') {
                    while (ch >= 'A' && ch <= 'z' || ch == '.') nextChar();
                    String func = expr.substring(startPos, this.pos);
                    if (eat('(')) { x = parseExpr(); if (!eat(')')) throw new RuntimeException("Missing ) after " + func); }
                    else { return switch (func) { case "Math.PI" -> Math.PI; case "Math.E" -> Math.E; default -> throw new RuntimeException("Unknown: " + func); }; }
                    x = switch (func) { case "Math.sqrt" -> Math.sqrt(x); case "Math.abs" -> Math.abs(x); case "Math.sin" -> Math.sin(x); case "Math.cos" -> Math.cos(x); case "Math.tan" -> Math.tan(x); case "Math.log" -> Math.log(x); default -> throw new RuntimeException("Unknown function: " + func); };
                } else throw new RuntimeException("Unexpected: " + (char) ch);
                return x;
            }
        }.parse();
    }
}
