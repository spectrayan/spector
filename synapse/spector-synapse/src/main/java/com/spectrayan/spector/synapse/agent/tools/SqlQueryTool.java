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

import com.spectrayan.spector.synapse.agent.AgentTool;
import com.spectrayan.spector.synapse.agent.AgentTool.ToolCategory;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.spectrayan.spector.synapse.bridge.LlmBridge;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Translates a natural-language question into a read-only SQL query via the
 * chat model, validates that the generated statement is a genuine SELECT
 * using a real SQL parser, executes it with a
 * bounded timeout (30 seconds), and returns the results as a Markdown table for agent
 * consumption.
 */
@Component
public class SqlQueryTool implements AgentTool {

    private static final int QUERY_TIMEOUT_SECONDS = 30;
    private static final int MAX_ROWS = 200;

    private final LlmBridge llmBridge;
    private final JdbcTemplate jdbcTemplate;
    // SLF4J logger   
    private static final Logger log = LoggerFactory.getLogger(SqlQueryTool.class);
    // Schema Caching
    private volatile String cachedSchema = null;

    // Allowed tables:
    private final Set<String> allowedTables;
    // Denied tables:
    private final Set<String> deniedTables;

    public SqlQueryTool(
            LlmBridge llmBridge,
            JdbcTemplate jdbcTemplate,
            @Value("${spector.sql-query-tool.allowed-tables:}") String allowedTablesProperty,
            @Value("${spector.sql-query-tool.denied-tables:"
                    + "credentials,credential,passwords,password_resets,"
                    + "api_keys,api_key,secrets,secret,tokens,token,"
                    + "oauth_tokens,refresh_tokens,sessions,session,"
                    + "audit_log,audit_logs}") String deniedTablesProperty) {
        
        this.llmBridge = llmBridge;
        this.jdbcTemplate = jdbcTemplate;
        this.jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        this.allowedTables = parseTableSet(allowedTablesProperty);
        this.deniedTables = parseTableSet(deniedTablesProperty);
    }

    @Override
    public String name() {
        return "sql_query";
    }

    @Override
    public String description() {
        return "Answers a natural-language question about the database by generating a "
                + "read-only SQL SELECT query, executing it safely, and returning the "
                + "results as a Markdown table. Write operations are rejected.";
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.DATA;
    }

    @Override
    public boolean isWriteTool() {
        return false;
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "question", Map.of(
                                "type", "string",
                                "description", "The natural-language question to answer, e.g. "
                                        + "'How many users signed up last week?'"
                        )
                ),
                "required", List.of("question")
        );
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String question = (String) arguments.get("question");
        if (question == null || question.isBlank()) {
            return "Error: 'question' parameter is required.";
        }

        String sql;
        try {
            sql = generateSql(question);
        } catch (Exception e) {
            return "Error: failed to generate SQL - " + e.getMessage();
        }

        String validationError = validateReadOnly(sql);
        if (validationError != null) {
            return "Error: " + validationError + " Generated statement: " + sql;
        }

        try {
            return jdbcTemplate.query(sql, this::formatAsMarkdownTable);
        } catch (Exception e) {
            log.error("[SqlQueryTool] Query execution failed: {}", e.getMessage(), e);  
            return "Error: query execution failed - " + e.getMessage();
        }
    }

    private String generateSql(String question) {
        String schema = describeSchema();

        String systemPrompt = """
                You are a SQL generation assistant. Given a database schema and a
                natural-language question, respond with ONLY a single, read-only
                SQL SELECT statement that answers the question.

                Rules:
                - Output SQL only. No explanation, no Markdown code fences.
                - Only use SELECT (or WITH ... SELECT) statements.
                - Never use any other SQL statements except for SELECT, e.g. INSERT, UPDATE, DELETE, DROP, etc.
                - Only reference tables and columns that exist in the schema below.
                - Prefer explicit column lists over SELECT *.

                Schema:
                %s
                """.formatted(schema);

        String rawResponse = llmBridge.generate(systemPrompt, question);

        return cleanSql(rawResponse);
    }

    /** Strips Markdown code fences and trailing semicolons/whitespace the model may add. */
    private String cleanSql(String rawResponse) {
        if (rawResponse == null) {
            return "";
        }
        String cleaned = rawResponse.trim();
        cleaned = cleaned.replaceAll("(?i)^```sql", "");
        cleaned = cleaned.replaceAll("^```", "");
        cleaned = cleaned.replaceAll("```$", "");
        cleaned = cleaned.trim();
        if (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned.trim();
    }

    /**
     * Parses the SQL into an AST and rejects anything that isn't a genuine
     * single SELECT statement.
     */
    private String validateReadOnly(String sql) {
        if (sql == null || sql.isBlank()) {
            return "generated SQL was empty.";
        }

        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            return "generated SQL failed to parse: " + e.getMessage();
        }

        if (!(statement instanceof Select)) {
            return "only SELECT statements are allowed, got: "
                    + statement.getClass().getSimpleName();
        }

        return validateTableAccess((Select) statement);
    }

    // Helper methods to validate the table access:
    /**
     * Extracts every table the SELECT touches — including joins and
     * subqueries — via JSQLParser's {@link TablesNamesFinder}, and checks
     * them against the configured allowlist/denylist.
     */

    private String validateTableAccess(Select select) {
        Set<String> referencedTables = new TablesNamesFinder().getTableList((Statement) select).stream()
                .map(this::normalizeTableName)
                .collect(Collectors.toSet());

        if (!allowedTables.isEmpty()) {
            Set<String> notAllowed = referencedTables.stream()
                    .filter(t -> !allowedTables.contains(t))
                    .collect(Collectors.toSet());
            if (!notAllowed.isEmpty()) {
                return "query references tables outside the allowlist: " + notAllowed;
            }
            return null;
        }

        Set<String> denied = referencedTables.stream()
                .filter(deniedTables::contains)
                .collect(Collectors.toSet());
        if (!denied.isEmpty()) {
            return "query references restricted tables: " + denied;
        }

        return null;
    }

    /** Strips schema/catalog qualification and quoting, lower-cases for comparison. */
    private String normalizeTableName(String rawName) {
        String name = rawName;
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(dot + 1);
        }
        name = name.replaceAll("[\"`\\[\\]]", "");
        return name.toLowerCase(Locale.ROOT);
    }

    private static Set<String> parseTableSet(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    /** Introspects the connected database's tables and columns via JDBC metadata. */
    /** Returns the cached schema description, computing it on first use. */
    private String describeSchema() {
        String schema = cachedSchema;
        if (schema == null) {
            synchronized (this) {
                schema = cachedSchema;
                if (schema == null) {
                    schema = introspectSchema();
                    cachedSchema = schema;
                }
            }
        }
        return schema;
    }

    // Recompute the schema on next request.
    public void refreshSchema() {
        synchronized (this) {
            cachedSchema = null;
        }
    }

    /** Introspects the connected database's tables and columns via JDBC metadata. */
    private String introspectSchema() {
        return jdbcTemplate.execute((java.sql.Connection connection) -> {
            StringBuilder sb = new StringBuilder();
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    sb.append("Table: ").append(tableName).append("\n");
                    try (ResultSet columns = metaData.getColumns(null, null, tableName, "%")) {
                        while (columns.next()) {
                            sb.append("  - ")
                                    .append(columns.getString("COLUMN_NAME"))
                                    .append(" (")
                                    .append(columns.getString("TYPE_NAME"))
                                    .append(")\n");
                        }
                    }
                }
            }
            return sb.toString();
        });
    }

    /** Renders a ResultSet as a Markdown table, capped at MAX_ROWS for context safety. */
    private String formatAsMarkdownTable(ResultSet rs) throws java.sql.SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        StringBuilder header = new StringBuilder("|");
        StringBuilder separator = new StringBuilder("|");
        for (int i = 1; i <= columnCount; i++) {
            header.append(" ").append(meta.getColumnLabel(i)).append(" |");
            separator.append(" --- |");
        }

        StringBuilder rows = new StringBuilder();
        int rowCount = 0;
        while (rs.next() && rowCount < MAX_ROWS) {
            rows.append("|");
            for (int i = 1; i <= columnCount; i++) {
                Object value = rs.getObject(i);
                rows.append(" ").append(value == null ? "NULL" : value.toString()).append(" |");
            }
            rows.append("\n");
            rowCount++;
        }

        if (rowCount == 0) {
            return "No rows returned.";
        }

        String truncatedNote = rowCount == MAX_ROWS
                ? "\n_Results truncated at " + MAX_ROWS + " rows._"
                : "";

        return header + "\n" + separator + "\n" + rows + truncatedNote;
    }
}