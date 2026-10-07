package org.embeddedjnosql.db;

import org.embeddedjnosql.db.config.ConsoleConfig;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.config.SecurityConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for two more "success that cannot fail" defects found by
 * probing the live console:
 *
 * <ul>
 *   <li><b>R-42</b> — committing or rolling back an unknown (or already finished)
 *       transactionId returned {@code 200 {"status":"committed"}} without doing
 *       anything, and the rollback string was literally "rollbackted".</li>
 *   <li><b>R-44</b> — {@code POST /api/bulk/{collection}} always generated UUID ids,
 *       silently demoting the caller's "id" to an ordinary field: a client that
 *       bulk-inserted {@code {"id":"b1",...}} and then GETs {@code /b1} got a 404.
 *       The single-document POST honours the id; bulk now matches.</li>
 * </ul>
 */
@DisplayName("Console transactions and bulk inserts report what actually happened")
class ConsoleTransactionAndBulkTest {

    private static final String API_KEY = "tx-bulk-test-key";

    private EmbedJNoSQL db;
    private String baseUrl;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        db = EmbedJNoSQL.embed()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY)
                .console(ConsoleConfig.builder()
                        .enabled(true)
                        .port(0)
                        .host("127.0.0.1")
                        .intelligentPort(true)
                        .build())
                .security(SecurityConfig.builder()
                        .authEnabled(true)
                        .apiKey(API_KEY)
                        .csrfEnabled(false)
                        .rateLimitEnabled(false)
                        .bruteForceProtectionEnabled(false)
                        .build())
                .build();
        baseUrl = "http://127.0.0.1:" + db.consolePort();
    }

    @AfterEach
    void tearDown() {
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    @Test
    @DisplayName("Committing an unknown transactionId is a 404, not a fake 200 committed")
    void commitUnknownTransactionIs404() throws Exception {
        var resp = execute("POST", "/api/transactions", "{\"action\":\"commit\",\"transactionId\":999999}");

        assertEquals(404, resp.code, "a commit that did nothing must not say committed: " + resp.body);
        assertTrue(resp.body.contains("No active transaction"), resp.body);
    }

    @Test
    @DisplayName("Rolling back an unknown transactionId is a 404 too")
    void rollbackUnknownTransactionIs404() throws Exception {
        var resp = execute("POST", "/api/transactions", "{\"action\":\"rollback\",\"transactionId\":1}");

        assertEquals(404, resp.code, resp.body);
    }

    @Test
    @DisplayName("A real begin/commit round trip reports committed")
    void realCommitRoundTrip() throws Exception {
        var begun = execute("POST", "/api/transactions", "{}");
        assertEquals(200, begun.code, begun.body);
        assertTrue(begun.body.contains("\"started\""), begun.body);
        var txId = extractNumber(begun.body, "transactionId");

        var committed = execute("POST", "/api/transactions",
                "{\"action\":\"commit\",\"transactionId\":" + txId + "}");

        assertEquals(200, committed.code, committed.body);
        assertTrue(committed.body.contains("\"committed\""), committed.body);
        assertFalse(committed.body.contains("rollbackted"), "the old typo must be gone");
        assertEquals(0, db.consoleServer() != null ? countActive() : -1);
    }

    @Test
    @DisplayName("Double commit is a conflict (409), not a second success")
    void doubleCommitIs409() throws Exception {
        var begun = execute("POST", "/api/transactions", "{}");
        var txId = extractNumber(begun.body, "transactionId");

        var first = execute("POST", "/api/transactions", "{\"action\":\"commit\",\"transactionId\":" + txId + "}");
        assertEquals(200, first.code, first.body);

        var second = execute("POST", "/api/transactions", "{\"action\":\"commit\",\"transactionId\":" + txId + "}");
        assertEquals(404, second.code, "the tx is gone from the active map after commit; " +
                "a second commit must not claim success again: " + second.body);
    }

    @Test
    @DisplayName("A real rollback reports rolled_back")
    void realRollbackRoundTrip() throws Exception {
        var begun = execute("POST", "/api/transactions", "{}");
        var txId = extractNumber(begun.body, "transactionId");

        var rolled = execute("POST", "/api/transactions", "{\"action\":\"rollback\",\"transactionId\":" + txId + "}");

        assertEquals(200, rolled.code, rolled.body);
        assertTrue(rolled.body.contains("rolled_back"), rolled.body);
        assertFalse(rolled.body.contains("rollbackted"), "the old typo must be gone");
    }

    @Test
    @DisplayName("Bulk insert honours client-supplied ids, like the single-doc POST")
    void bulkInsertHonoursClientIds() throws Exception {
        var resp = execute("POST", "/api/bulk/gateitems",
                "[{\"id\":\"b1\",\"name\":\"One\"},{\"id\":\"b2\",\"name\":\"Two\"}]");

        assertEquals(201, resp.code, resp.body);
        assertTrue(resp.body.contains("\"inserted\":2"), resp.body);

        // The whole point: an id the client sent must be addressable afterwards.
        var b1 = execute("GET", "/api/collections/gateitems/b1", null);
        assertEquals(200, b1.code, "GET by the client-supplied id must find the document: " + b1.body);
        assertTrue(b1.body.contains("One"), b1.body);

        var b2 = execute("GET", "/api/collections/gateitems/b2", null);
        assertEquals(200, b2.code, b2.body);
    }

    @Test
    @DisplayName("Bulk insert without ids still generates them, and reports skipped entries")
    void bulkInsertWithoutIdsGeneratesAndSkips() throws Exception {
        var resp = execute("POST", "/api/bulk/gateitems",
                "[{\"name\":\"NoId\"},\"not-a-document\"]");

        assertEquals(201, resp.code, resp.body);
        assertTrue(resp.body.contains("\"inserted\":1"), resp.body);
        assertTrue(resp.body.contains("\"skipped\":1"), "non-object entries must be counted, not vanish: " + resp.body);

        var all = execute("GET", "/api/collections/gateitems", null);
        assertEquals(200, all.code);
        assertTrue(all.body.contains("NoId"), all.body);
    }

    @Test
    @DisplayName("Bulk insert of a non-array body is a 400, not a silent 0")
    void bulkInsertNonArrayIs400() throws Exception {
        var resp = execute("POST", "/api/bulk/gateitems", "{\"id\":\"x\"}");

        assertEquals(400, resp.code, resp.body);
        assertTrue(resp.body.contains("expected an array"), resp.body);
    }

    private int countActive() throws Exception {
        var resp = execute("GET", "/api/transactions", null);
        return Integer.parseInt(extractNumber(resp.body, "activeTransactions"));
    }

    private static String extractNumber(String body, String field) {
        var marker = "\"" + field + "\":";
        int start = body.indexOf(marker);
        if (start < 0) throw new AssertionError("field " + field + " not in: " + body);
        start += marker.length();
        int end = start;
        while (end < body.length() && (Character.isDigit(body.charAt(end)))) end++;
        return body.substring(start, end);
    }

    private Response execute(String method, String path, String body) throws Exception {
        var conn = (HttpURLConnection) URI.create(baseUrl + path).toURL().openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("X-API-Key", API_KEY);
        if (body != null) {
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        var stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String text = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        return new Response(code, text);
    }

    private record Response(int code, String body) {
    }
}
