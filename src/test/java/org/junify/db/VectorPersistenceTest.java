package org.junify.db;

import org.junify.db.config.ConsoleConfig;
import org.junify.db.config.JunifyDBConfig;
import org.junify.db.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for <b>R-52</b>: vector indexes lived only in the
 * console server's memory — every restart silently lost all vectors and
 * dimensions while documents, KV entries, and column families all survived.
 * Indexes are now persisted under {@code <dataDir>/vectors/*.json} on every
 * mutation (best-effort, like the audit writer) and restored at server start.
 *
 * <p>Verified across a full server close/reopen cycle over the same data
 * directory, on the FILE engine. IN_MEMORY keeps its no-durability promise:
 * it neither persists nor restores.</p>
 */
@DisplayName("Vector indexes survive a server restart like every other stored object (R-52)")
class VectorPersistenceTest {

    @Test
    void vectorsSurviveRestartOnFileEngine(@TempDir Path tempDir) throws Exception {
        try (var db = newFileDb(tempDir)) {
            post(db, "POST", "/api/vectors/keep_idx/v1", "{\"vector\":[1,2,3]}");
            post(db, "POST", "/api/vectors/keep_idx/v2", "{\"vector\":[3,2,1]}");
        }

        try (var db = newFileDb(tempDir)) {
            Response info = send(db, "GET", "/api/vectors/keep_idx/_", null);
            assertEquals(200, info.code, "index must exist after restart: " + info.body);
            assertTrue(info.body.contains("\"size\":2"), "both vectors restored: " + info.body);
            assertTrue(info.body.contains("\"dimensions\":3"), info.body);

            Response search = send(db, "POST", "/api/vectors/keep_idx/search", "{\"vector\":[1,2,3],\"k\":2}");
            assertEquals(200, search.code, search.body);
            assertTrue(search.body.contains("v1"), "nearest neighbour v1 found after restart: " + search.body);

            // Deletion persists too: remove v2, restart again, size must stay 1.
            send(db, "DELETE", "/api/vectors/keep_idx/v2", null);
        }

        try (var db = newFileDb(tempDir)) {
            Response info = send(db, "GET", "/api/vectors/keep_idx/_", null);
            assertEquals(200, info.code, info.body);
            assertTrue(info.body.contains("\"size\":1"), "deletion persisted across restart: " + info.body);
        }
    }

    @Test
    void inMemoryEngineDoesNotPersist(@TempDir Path tempDir) throws Exception {
        var db = JunifyDB.embed()
                .storageEngine(JunifyDBConfig.StorageEngineType.IN_MEMORY)
                .console(ConsoleConfig.builder().enabled(true).port(0).host("127.0.0.1").intelligentPort(true).build())
                .security(SecurityConfig.builder()
                        .authEnabled(true).apiKey("vp-key").csrfEnabled(false)
                        .rateLimitEnabled(false).bruteForceProtectionEnabled(false).build())
                .build();
        try {
            post(db, "POST", "/api/vectors/mem_idx/m1", "{\"vector\":[1,2,3]}");
            assertEquals(0, countVectorFiles(tempDir), "IN_MEMORY must not write vector files");
        } finally {
            db.close();
        }
    }

    @Test
    void corruptVectorFileDoesNotPreventStartup(@TempDir Path tempDir) throws Exception {
        try (var db = newFileDb(tempDir)) {
            post(db, "POST", "/api/vectors/good_idx/g1", "{\"vector\":[1,2,3]}");
        }
        java.nio.file.Files.writeString(
                tempDir.resolve("vectors/bad_idx.json"), "{not json at all");

        // Startup must succeed and the good index must still restore.
        try (var db = newFileDb(tempDir)) {
            Response info = send(db, "GET", "/api/vectors/good_idx/_", null);
            assertEquals(200, info.code, "healthy index must restore beside a corrupt one");
            Response bad = send(db, "GET", "/api/vectors/bad_idx/_", null);
            assertEquals(404, bad.code, "corrupt index is quarantined, not served");
            try (var leftovers = java.nio.file.Files.list(tempDir.resolve("vectors"))) {
                assertTrue(leftovers.anyMatch(p -> p.getFileName().toString().startsWith("bad_idx.json.corrupt-")),
                        "corrupt file must be renamed aside for inspection, not deleted");
            }
        }
    }

    // ---- helpers --------------------------------------------------------------

    private JunifyDB newFileDb(Path tempDir) {
        return JunifyDB.create(org.junify.db.config.JunifyDBConfig.builder()
                .storageEngine(JunifyDBConfig.StorageEngineType.FILE)
                .dataDir(tempDir)
                .console(ConsoleConfig.builder().enabled(true).port(0).host("127.0.0.1").intelligentPort(true).build())
                .security(SecurityConfig.builder()
                        .authEnabled(true).apiKey("vp-key").csrfEnabled(false)
                        .rateLimitEnabled(false).bruteForceProtectionEnabled(false).build())
                .buildConfig());
    }

    private long countVectorFiles(Path tempDir) throws Exception {
        Path dir = tempDir.resolve("vectors");
        return java.nio.file.Files.isDirectory(dir) ? java.nio.file.Files.list(dir).count() : 0;
    }

    private void post(JunifyDB db, String method, String path, String body) throws Exception {
        send(db, method, path, body);
    }

    private Response send(JunifyDB db, String method, String path, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(
                "http://127.0.0.1:" + db.consolePort() + path).toURL().openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("X-API-Key", "vp-key");
        if (body != null) {
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        var is = code < 400 ? conn.getInputStream() : conn.getErrorStream();
        String text = new String(is != null ? is.readAllBytes() : new byte[0], StandardCharsets.UTF_8);
        return new Response(code, text);
    }

    private record Response(int code, String body) {}
}
