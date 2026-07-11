// Unit test for the methods in SqlQueryTool.java

package com.spectrayan.spector.synapse.agent.tools;
import com.spectrayan.spector.synapse.agent.AgentTool;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.spectrayan.spector.synapse.bridge.LlmBridge;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.SQLTimeoutException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SqlQueryTool} covering:
 * <ul>
 *   <li>rejection of write operations, unparsable SQL, and disallowed statement types</li>
 *   <li>exception handling for execution failures (including timeout-shaped exceptions)</li>
 *   <li>Markdown table formatting of query results</li>
 *   <li>table-level allowlist/denylist enforcement</li>
 *   <li>schema caching and {@code refreshSchema()} invalidation</li>
 * </ul>
 */
class SqlQueryToolTest {

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:sqlquerytool-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE users (id INT, name VARCHAR(50))");
        jdbcTemplate.execute("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");
        jdbcTemplate.execute("CREATE TABLE credentials (user_id INT, password_hash VARCHAR(100))");
        jdbcTemplate.execute("INSERT INTO credentials VALUES (1, 'hash-abc')");
    }

    // ── helpers ──

    /** Builds a SqlQueryTool whose generated SQL is always {@code sqlToReturn}. */
    private SqlQueryTool toolReturning(String sqlToReturn, JdbcTemplate jdbc,
                                        String allowedTables, String deniedTables) {
        LlmBridge llmBridge = mock(LlmBridge.class);
        when(llmBridge.generate(anyString(), anyString())).thenReturn(sqlToReturn);

        return new SqlQueryTool(llmBridge, jdbc, allowedTables, deniedTables);
    }

    private SqlQueryTool toolReturning(String sqlToReturn) {
        return toolReturning(sqlToReturn, jdbcTemplate, "", "");
    }

    // ── 1. rejection of write operations ──

    @Test
    void rejectsGeneratedDelete() {
        SqlQueryTool tool = toolReturning("DELETE FROM users WHERE id = 1");

        String result = tool.execute(Map.of("question", "delete all users"));

        assertThat(result).startsWith("Error:").contains("only SELECT statements are allowed");
    }

    @Test
    void rejectsGeneratedDrop() {
        SqlQueryTool tool = toolReturning("DROP TABLE users");

        String result = tool.execute(Map.of("question", "drop the users table"));

        assertThat(result).startsWith("Error:");
    }

    @Test
    void rejectsGeneratedInsert() {
        SqlQueryTool tool = toolReturning("INSERT INTO users VALUES (3, 'Eve')");

        String result = tool.execute(Map.of("question", "add a user"));

        assertThat(result).startsWith("Error:");
    }

    @Test
    void rejectsStackedStatements() {
        SqlQueryTool tool = toolReturning("SELECT * FROM users; DROP TABLE users;");

        String result = tool.execute(Map.of("question", "list users"));

        assertThat(result).startsWith("Error:");
    }

    @Test
    void allowsSelectContainingWriteKeywordAsLiteral() {
        // proves this isn't a naive keyword-regex check
        SqlQueryTool tool = toolReturning(
                "SELECT * FROM users WHERE name = 'DELETE attempt logged'");

        String result = tool.execute(Map.of("question", "find suspicious log entries"));

        assertThat(result).doesNotStartWith("Error:");
    }

    // ── 2. parse errors and execution/timeout exceptions ──

    @Test
    void rejectsUnparsableGeneratedSql() {
        SqlQueryTool tool = toolReturning("this is not sql at all");

        String result = tool.execute(Map.of("question", "something nonsensical"));

        assertThat(result).startsWith("Error:").contains("failed to parse");
    }

    @Test
    void executeRequiresQuestion() {
        SqlQueryTool tool = toolReturning("SELECT * FROM users");

        assertThat(tool.execute(Map.of())).startsWith("Error:");
    }

    @Test
    void handlesTimeoutExceptionDuringExecution() {
        JdbcTemplate spyJdbc = spy(jdbcTemplate);
        doThrow(new QueryTimeoutException("query timed out", new SQLTimeoutException("timeout")))
                .when(spyJdbc).query(anyString(), any(ResultSetExtractor.class));

        SqlQueryTool tool = toolReturning("SELECT * FROM users", spyJdbc, "", "");

        String result = tool.execute(Map.of("question", "list all users"));

        assertThat(result).startsWith("Error:").contains("query execution failed");
    }

    @Test
    void handlesGenericSqlExceptionDuringExecution() {
        // real SQLException path: querying a column that doesn't exist
        SqlQueryTool tool = toolReturning("SELECT nonexistent_column FROM users");

        String result = tool.execute(Map.of("question", "bad query"));

        assertThat(result).startsWith("Error:").contains("query execution failed");
    }

    // ── 3. Markdown table formatting ──

    @Test
    void formatsResultsAsMarkdownTable() {
        SqlQueryTool tool = toolReturning("SELECT id, name FROM users ORDER BY id");

        String result = tool.execute(Map.of("question", "list all users"));

        assertThat(result)
                .contains("| ID | NAME |")
                .contains("| --- | --- |")
                .contains("| 1 | Alice |")
                .contains("| 2 | Bob |");
    }

    @Test
    void formatsEmptyResultSetAsNoRowsMessage() {
        SqlQueryTool tool = toolReturning("SELECT * FROM users WHERE id = 999");

        String result = tool.execute(Map.of("question", "find user 999"));

        assertThat(result).isEqualTo("No rows returned.");
    }

    // ── table allowlist / denylist ──

    @Test
    void rejectsQueryAgainstDefaultDeniedTable() {
        SqlQueryTool tool = toolReturning(
                "SELECT password_hash FROM credentials",
                jdbcTemplate, "", "credentials,api_keys,tokens");

        String result = tool.execute(Map.of("question", "show me a password hash"));

        assertThat(result).startsWith("Error:").contains("restricted tables");
    }

    @Test
    void rejectsQueryTouchingDeniedTableViaJoin() {
        SqlQueryTool tool = toolReturning(
                "SELECT u.name, c.password_hash FROM users u JOIN credentials c ON u.id = c.user_id",
                jdbcTemplate, "", "credentials");

        String result = tool.execute(Map.of("question", "join users and credentials"));

        assertThat(result).startsWith("Error:").contains("restricted tables");
    }

    @Test
    void allowsQueryAgainstNonDeniedTable() {
        SqlQueryTool tool = toolReturning(
                "SELECT * FROM users", jdbcTemplate, "", "credentials");

        String result = tool.execute(Map.of("question", "list users"));

        assertThat(result).doesNotStartWith("Error:");
    }

    @Test
    void allowlistTakesPriorityOverDenylistAndBlocksUnlistedTables() {
        SqlQueryTool tool = toolReturning(
                "SELECT * FROM credentials", jdbcTemplate, "users", "");

        String result = tool.execute(Map.of("question", "show credentials"));

        assertThat(result).startsWith("Error:").contains("outside the allowlist");
    }

    @Test
    void allowlistPermitsExplicitlyListedTable() {
        SqlQueryTool tool = toolReturning(
                "SELECT * FROM users", jdbcTemplate, "users", "");

        String result = tool.execute(Map.of("question", "show users"));

        assertThat(result).doesNotStartWith("Error:");
    }

    // ── schema caching ──

    @Test
    void schemaIsCachedAcrossMultipleExecuteCalls() {
        JdbcTemplate spyJdbc = spy(jdbcTemplate);
        SqlQueryTool tool = toolReturning("SELECT * FROM users", spyJdbc, "", "");

        tool.execute(Map.of("question", "list users"));
        tool.execute(Map.of("question", "list users again"));

        // schema introspection (a ConnectionCallback execute) should only
        // happen once across both calls, not once per call
        verify(spyJdbc, times(1)).execute(any(ConnectionCallback.class));
    }

    @Test
    void refreshSchemaForcesRecomputationOnNextCall() {
        JdbcTemplate spyJdbc = spy(jdbcTemplate);
        SqlQueryTool tool = toolReturning("SELECT * FROM users", spyJdbc, "", "");

        tool.execute(Map.of("question", "list users"));
        tool.refreshSchema();
        tool.execute(Map.of("question", "list users again"));

        verify(spyJdbc, times(2)).execute(any(ConnectionCallback.class));
    }

    // ── metadata ──

    @Test
    void exposesExpectedMetadata() {
        SqlQueryTool tool = toolReturning("SELECT * FROM users");

        assertThat(tool.name()).isEqualTo("sql_query");
        assertThat(tool.category()).isEqualTo(AgentTool.ToolCategory.DATA);
        assertThat(tool.isWriteTool()).isFalse();
        assertThat(tool.parameterSchema()).containsEntry("type", "object");
    }
}