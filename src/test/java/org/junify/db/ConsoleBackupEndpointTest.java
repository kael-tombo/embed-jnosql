package org.junify.db;

import org.junify.db.config.ConsoleConfig;
import org.junify.db.config.JunifyDBConfig;
import org.junify.db.config.SecurityConfig;
import org.junify.db.nosql.document.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HTTP-level coverage for the console Backup panel.
 *
 * <p>Before the fix these endpoints reported success while doing nothing useful:
 * {@code POST /api/backup} snapshotted a brand-new engine over an empty temp directory
 * (so every snapshot was the 22-byte gzip of {@code {}}), the response never said how
 * many documents were captured, and {@code POST /api/backup/restore} — the exact call the
 * console UI makes — was rejected with "Usage: GET /api/backup or POST /api/backup".
 */
@DisplayName("Console backups capture and restore real data")
class ConsoleBackupEndpointTest {

    private static final String API_KEY = "backup-endpoint-test-key";

    private JunifyDB db;
    private String baseUrl;
    private Path dataDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        dataDir = tempDir;
        db = JunifyDB.embed()
                .storageEngine(JunifyDBConfig.StorageEngineType.FILE)
                .persistTo(tempDir.toString())
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
    @DisplayName("GET /api/backup reports the live engine and durable backup directory")
    void getReportsEngineAndDirectory() throws Exception {
        var resp = execute("GET", "/api/backup", null);

        assertEquals(200, resp.code, resp.body);
        assertTrue(resp.body.contains("\"engine\":\"FILE\""), "engine must be the live engine, got: " + resp.body);
        assertTrue(resp.body.contains("\"directory\""), "UI needs the backup directory to show the user: " + resp.body);
        assertTrue(resp.body.contains("\"backups\""), resp.body);
    }

    @Test
    @DisplayName("POST /api/backup reports the document count it actually captured")
    void createReportsRealCounts() throws Exception {
        var products = db.documentCollection("products");
        products.insert(Document.of("name", "Keyboard").add("price", 75));
        products.insert(Document.of("name", "Desk Lamp").add("price", 40));
        db.keyValueBucket("sessions").put("s1", "token-1");

        var resp = execute("POST", "/api/backup", "{}");

        assertEquals(200, resp.code, resp.body);
        assertTrue(resp.body.contains("\"documents\":3"), "expected 3 captured entries, got: " + resp.body);
        assertTrue(resp.body.contains("\"products\":2"), resp.body);
        assertTrue(resp.body.contains("\"sessions\":1"), resp.body);
    }

    @Test
    @DisplayName("Snapshot lands in <dataDir>/backups and contains the seeded documents")
    void snapshotIsDurableAndPopulated() throws Exception {
        db.documentCollection("users").insert(Document.of("name", "Alice"));

        var resp = execute("POST", "/api/backup", "{}");
        assertEquals(200, resp.code, resp.body);

        var backupsDir = dataDir.resolve("backups");
        assertTrue(Files.isDirectory(backupsDir), "backups must not be written to a temp directory");

        var files = Files.list(backupsDir).filter(p -> p.toString().endsWith(".json.gz")).toList();
        assertEquals(1, files.size(), "expected exactly one snapshot");
        assertTrue(Files.size(files.get(0)) > 2, "an empty gzip of {} would be 2 bytes");

        try (var in = new GZIPInputStream(Files.newInputStream(files.get(0)))) {
            var content = new String(in.readAllBytes());
            assertTrue(content.contains("Alice"), "snapshot must hold the document, got: " + content);
        }
    }

    @Test
    @DisplayName("POST /api/backup/restore (the console's own call) succeeds instead of 400")
    void restoreRouteIsWired() throws Exception {
        db.documentCollection("users").insert(Document.of("name", "Bob"));
        var created = execute("POST", "/api/backup", "{}");
        assertEquals(200, created.code, created.body);
        var file = Files.list(dataDir.resolve("backups")).findFirst().orElseThrow().toString().replace("\\", "/");

        // Remove the document, then restore it from the snapshot.
        db.storageEngine().delete("users", db.storageEngine().keys("users").iterator().next());

        var restored = execute("POST", "/api/backup/restore", "{\"backupFile\":\"" + file + "\"}");

        assertEquals(200, restored.code, restored.body);
        assertTrue(restored.body.contains("\"restored\""), restored.body);
        assertEquals(1, db.storageEngine().keys("users").size(), "the document must be back in the live engine");
    }

    @Test
    @DisplayName("Restore of a missing file is a 404, not a usage error")
    void restoreMissingFileIsNotAUsageError() throws Exception {
        var resp = execute("POST", "/api/backup/restore",
                "{\"backupFile\":\"" + dataDir.resolve("nope.json.gz").toString().replace("\\", "/") + "\"}");

        assertEquals(404, resp.code, resp.body);
        assertTrue(resp.body.contains("not found"), resp.body);
    }

    @Test
    @DisplayName("Restore without a backupFile explains what is required")
    void restoreWithoutFileIsRejected() throws Exception {
        var resp = execute("POST", "/api/backup/restore", "{}");

        assertEquals(400, resp.code, resp.body);
        assertTrue(resp.body.contains("backupFile is required"), resp.body);
    }

    @Test
    @DisplayName("A body-less POST still answers (it used to drop the connection)")
    void emptyBodyCreatesBackupWithDefaults() throws Exception {
        db.documentCollection("users").insert(Document.of("name", "Carol"));

        var resp = execute("POST", "/api/backup", "");

        assertEquals(200, resp.code, "an empty body must not kill the connection: " + resp.body);
        assertTrue(resp.body.contains("\"documents\":1"), resp.body);
    }

    @Test
    @DisplayName("Malformed JSON is reported as 400 rather than dropped")
    void malformedBodyIsRejectedClearly() throws Exception {
        var resp = execute("POST", "/api/backup", "{not json");

        assertEquals(400, resp.code, resp.body);
        assertTrue(resp.body.contains("Invalid JSON body"), resp.body);
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
