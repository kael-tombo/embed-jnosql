package org.junify.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junify.db.config.ConsoleConfig;
import org.junify.db.config.JunifyDBConfig;
import org.junify.db.config.SecurityConfig;
import org.junify.db.core.util.JsonSerde;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Console TASK-SUCCESS contract, exercised over real HTTP against a real server.
 *
 * <p>These assertions encode the Console quality requirements that are checkable from the
 * outside: the orientation context the UI must always be able to display, the explicit
 * state vocabulary behind every action, and the correlation id that makes an error
 * reportable. They deliberately test the shipped static assets as well as the API,
 * because an affordance that exists only in the JavaScript is not a delivered feature.</p>
 */
public class ConsoleTaskSuccessTest {

    private static final String API_KEY = "task-success-key-123";
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "task-success-pass";

    private JunifyDB db;
    private int port;
    private String cookie;

    @BeforeEach
    void setUp() {
        db = JunifyDB.embed()
                .storageEngine(JunifyDBConfig.StorageEngineType.IN_MEMORY)
                .console(ConsoleConfig.builder()
                        .enabled(true)
                        .port(0)
                        .contextPath("/")
                        .intelligentPort(true)
                        .build())
                .security(SecurityConfig.builder()
                        .authEnabled(true)
                        .apiKey(API_KEY)
                        .adminUsername(ADMIN_USER)
                        .adminPassword(ADMIN_PASS)
                        .build())
                .build();
        port = db.consolePort();
        assertTrue(port > 0, "console should bind a real port");
    }

    @AfterEach
    void tearDown() {
        if (db != null && db.isOpen()) db.close();
    }

    // ---------------------------------------------------------------- context

    @Test
    @DisplayName("health exposes the orientation context the Console must always show")
    @SuppressWarnings("unchecked")
    void healthExposesOrientationContext() throws Exception {
        loginAdmin();
        Response health = get("/api/health");
        assertEquals(200, health.code);

        var body = JsonSerde.fromJson(health.body, Map.class);
        var context = (Map<String, Object>) body.get("context");
        assertNotNull(context, "health must carry a context block, not just raw counters");

        // Every field the requirements demand the Console be able to display.
        for (String key : new String[]{
                "engine", "storageMode", "durability", "database", "dataDir",
                "authEnabled", "user", "activeTransactions", "transactionalConsoleWrites",
                "transactionScope"}) {
            assertTrue(context.containsKey(key), "context is missing required field: " + key);
        }

        assertEquals("IN_MEMORY", context.get("engine"));
        assertEquals("in-memory", context.get("storageMode"));
        assertEquals("memory", context.get("database"));
        assertTrue(String.valueOf(context.get("durability")).contains("no durability"),
                "an in-memory database must say so, not imply persistence");
        assertEquals(Boolean.TRUE, context.get("authEnabled"));
        assertEquals(ADMIN_USER, context.get("user"), "identity must be the signed-in user");
        assertEquals(0, context.get("activeTransactions"));
        // The honest statement of transaction scope: Console writes are NOT transactional.
        assertEquals(Boolean.FALSE, context.get("transactionalConsoleWrites"));
        assertTrue(String.valueOf(context.get("transactionScope")).contains("bypass"),
                "transaction scope must state that Console writes bypass transactions");

        System.out.println("[TEST EVIDENCE] orientation context: " + context);
    }

    @Test
    @DisplayName("a file-backed database reports its durability window and data directory")
    @SuppressWarnings("unchecked")
    void fileEngineReportsDurabilityAndDataDir(@TempDir Path dir) throws Exception {
        try (JunifyDB fileDb = JunifyDB.embed()
                .storageEngine(JunifyDBConfig.StorageEngineType.FILE)
                .dataDir(dir)
                .autoFlush(true)
                .flushIntervalMs(1500)
                .console(ConsoleConfig.builder().enabled(true).port(0).intelligentPort(true).build())
                .build()) {
            var resp = new TestClient(fileDb.consolePort(), null)
                    .get("/api/health", null);
            assertEquals(200, resp.code);
            var context = (Map<String, Object>) JsonSerde.fromJson(resp.body, Map.class).get("context");

            assertEquals("FILE", context.get("engine"));
            assertEquals("sync", context.get("storageMode"));
            assertTrue(String.valueOf(context.get("durability")).contains("1500"),
                    "durability must name the actual flush interval, got: " + context.get("durability"));
            assertEquals(dir.toAbsolutePath().toString(), context.get("dataDir"));
            assertEquals(String.valueOf(dir), context.get("database"));
        }
    }

    @Test
    @DisplayName("active transactions are visible in the context block")
    @SuppressWarnings("unchecked")
    void activeTransactionsAppearInContext() throws Exception {
        loginAdmin();
        var before = (Map<String, Object>) JsonSerde.fromJson(
                get("/api/health").body, Map.class).get("context");
        assertEquals(0, before.get("activeTransactions"));

        Response begin = post("/api/transactions", "{\"action\":\"begin\"}");
        assertEquals(200, begin.code);

        var after = (Map<String, Object>) JsonSerde.fromJson(
                get("/api/health").body, Map.class).get("context");
        assertEquals(1, after.get("activeTransactions"),
                "a begun transaction must be reflected, so the Console can show tx state");
    }

    // ---------------------------------------------------------- correlation id

    @Test
    @DisplayName("every response carries a correlation id; errors repeat it in the body")
    void correlationIdOnSuccessAndFailure() throws Exception {
        loginAdmin();

        Response ok = get("/api/health");
        assertNotNull(ok.correlationId, "successful responses carry X-Correlation-Id");

        Response badSql = post("/api/sql", "{\"query\":\"SELECT * FROM no_such_table\"}");
        assertEquals(404, badSql.code, "unknown table is a client-state error");
        assertNotNull(badSql.correlationId, "error responses carry X-Correlation-Id");
        var errBody = JsonSerde.fromJson(badSql.body, Map.class);
        assertEquals(badSql.correlationId, errBody.get("correlationId"),
                "the id in the error body must match the header, so a user can quote one value");
        assertNotNull(errBody.get("error"));

        System.out.println("[TEST EVIDENCE] error correlation id: " + badSql.correlationId
                + " body: " + badSql.body);
    }

    @Test
    @DisplayName("a caller-supplied correlation id is echoed back for end-to-end tracing")
    void requestedCorrelationIdIsHonoured() throws Exception {
        loginAdmin();
        var resp = new TestClient(port, cookie).get("/api/health", "trace-console-42");
        assertEquals(200, resp.code);
        assertEquals("trace-console-42", resp.correlationId);
    }

    @Test
    @DisplayName("distinct failures are distinguishable by status so the UI can classify them")
    void failuresAreClassifiable() throws Exception {
        loginAdmin();

        // 401 permission: no credentials at all
        assertEquals(401, new TestClient(port, null).get("/api/collections", null).code);

        // 400 validation: malformed SQL is a syntax problem
        Response syntax = post("/api/sql", "{\"query\":\"SELECT FROM WHERE\"}");
        assertEquals(400, syntax.code);

        // 400 validation: empty statement is refused before execution
        assertEquals(400, post("/api/sql", "{\"query\":\"  \"}").code);

        // constraint violation from the earlier slice must surface as a client error
        post("/api/sql", "{\"query\":\"CREATE TABLE items (id VARCHAR PRIMARY KEY, qty INT NOT NULL)\"}");
        assertEquals(200, post("/api/sql", "{\"query\":\"INSERT INTO items (id, qty) VALUES ('i1', 5)\"}").code);
        Response dup = post("/api/sql", "{\"query\":\"INSERT INTO items (id, qty) VALUES ('i1', 9)\"}");
        assertEquals(400, dup.code, "duplicate primary key is a client error, not a 500");
        assertNotNull(dup.correlationId);

        // the rejected write must not have changed anything
        assertEquals(1, db.sql("SELECT * FROM items").size(),
                "a rejected statement must leave the table unchanged");
    }

    // --------------------------------------------------------- shipped assets

    @Test
    @DisplayName("shipped Console assets carry the task-success affordances")
    void staticAssetsExposeRequiredAffordances() throws Exception {
        String html = new String(Files.readAllBytes(
                Path.of("src/main/resources/static/index.html")), StandardCharsets.UTF_8);
        String js = new String(Files.readAllBytes(
                Path.of("src/main/resources/static/js/console.js")), StandardCharsets.UTF_8);

        // The status bar must be able to show every required piece of orientation context.
        for (String id : new String[]{"sbEngine", "sbStorage", "sbDatabase", "sbConnection",
                "sbTx", "sbUser", "sbContext", "statusbar"}) {
            assertTrue(html.contains("id=\"" + id + "\""), "index.html is missing status-bar item: " + id);
        }
        assertTrue(html.contains("id=\"statusbar\""), "status bar must be present in the layout");

        // SQL workflow affordances added for task success.
        for (String id : new String[]{"sqlCancel", "sqlRunSel", "sqlExportCsv", "sqlExportJson",
                "sqlDestructive"}) {
            assertTrue(html.contains("id=\"" + id + "\""), "SQL Studio is missing control: " + id);
        }

        // The named confirmation dialog replaces the browser prompt for destructive actions,
        // because confirm() can state neither the target nor the impact.
        assertTrue(html.contains("id=\"confirmDialog\""), "a confirmation dialog must exist");
        assertTrue(html.contains("role=\"dialog\""), "the dialog must be exposed as a dialog to assistive tech");
        assertFalse(js.contains("window.confirm"),
                "destructive actions must not fall back to window.confirm");
        assertFalse(java.util.regex.Pattern.compile("confirm\\(\\s*[`'\"]").matcher(js).find(),
                "destructive actions must use the named dialog, not a bare confirm(...) call");
        assertTrue(js.contains("confirmAction("), "destructive-action dialog helper must exist");

        // The explicit state vocabulary required by the Console contract.
        for (String state : new String[]{"loading", "success", "empty", "validation", "backend",
                "timeout", "permission", "conflict", "recovery_required"}) {
            assertTrue(js.contains("'" + state + "'"), "state vocabulary is missing: " + state);
        }

        // Errors must answer "did data change?" and expose the correlation id.
        assertTrue(js.contains("Did data change?"), "error surfaces must state whether data changed");
        assertTrue(js.contains("Correlation ID"), "error surfaces must show the correlation id");

        // Destructive SQL must be detected before it runs.
        assertTrue(js.contains("destructiveReason"), "destructive-statement detection must exist");

        System.out.println("[TEST EVIDENCE] shipped assets verified: status bar, destructive SQL guard, "
                + "export, cancel, named confirmation dialog, explicit state vocabulary");
    }

    // ---------------------------------------------------------------- helpers

    private void loginAdmin() throws Exception {
        var resp = new TestClient(port, null).post("/api/auth/login",
                "{\"username\":\"" + ADMIN_USER + "\",\"password\":\"" + ADMIN_PASS + "\"}", null);
        assertEquals(200, resp.code);
        var setCookie = resp.setCookie;
        assertNotNull(setCookie, "login must set a session cookie");
        for (String part : setCookie.split(";")) {
            String trimmed = part.trim();
            if (trimmed.regionMatches(true, 0, "JUNIFY_SESSION=", 0, 15)) {
                this.cookie = trimmed;
                return;
            }
        }
        fail("session cookie not found in Set-Cookie: " + setCookie);
    }

    private Response get(String path) throws Exception {
        return new TestClient(port, cookie).get(path, null);
    }

    private Response post(String path, String body) throws Exception {
        return new TestClient(port, cookie).post(path, body, null);
    }

    private static class Response {
        final int code;
        final String body;
        final String setCookie;
        final String correlationId;

        Response(int code, String body, String setCookie, String correlationId) {
            this.code = code;
            this.body = body;
            this.setCookie = setCookie;
            this.correlationId = correlationId;
        }
    }

    /** Minimal HTTP client so the assertions exercise the real wire format. */
    private static class TestClient {
        private final int port;
        private final String cookie;

        TestClient(int port, String cookie) {
            this.port = port;
            this.cookie = cookie;
        }

        Response get(String path, String correlationId) throws Exception {
            return call("GET", path, null, correlationId);
        }

        Response post(String path, String body, String correlationId) throws Exception {
            return call("POST", path, body, correlationId);
        }

        private Response call(String method, String path, String body, String correlationId) throws Exception {
            HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + path).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            if (cookie != null) conn.setRequestProperty("Cookie", cookie);
            if (correlationId != null) conn.setRequestProperty("X-Correlation-Id", correlationId);
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                // CSRF: session-authenticated writes require the token issued at login.
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = conn.getResponseCode();
            String setCookie = conn.getHeaderField("Set-Cookie");
            String corrId = conn.getHeaderField("X-Correlation-Id");
            var stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            StringBuilder sb = new StringBuilder();
            if (stream != null) {
                try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                }
            }
            conn.disconnect();
            return new Response(code, sb.toString(), setCookie, corrId);
        }
    }
}
