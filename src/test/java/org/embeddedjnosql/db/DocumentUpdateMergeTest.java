package org.embeddedjnosql.db;

import org.embeddedjnosql.db.console.http.EmbedJNoSQLServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for R-31: {@code POST/PUT /api/collections/{name}/{id}} must
 * merge onto the stored document.
 *
 * <p>Before the fix the handler built the row from {@code Document.of("name","temp")},
 * so a partial update replaced the document — silently dropping every field the body
 * omitted — and injected a synthetic {@code name=temp} field into the row.</p>
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class DocumentUpdateMergeTest {

    private EmbedJNoSQL db;
    private EmbedJNoSQLServer server;

    @BeforeEach
    void setUp() throws Exception {
        db = EmbedJNoSQL.embed().build();
        server = db.startServer(0);
        server.setApiKey(null); // auth disabled, as on the preview server
        Thread.sleep(100);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                // ignore cleanup errors
            }
        }
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    private int port() {
        return server.port();
    }

    @Test
    void partialUpdatePreservesStoredFieldsAndAddsNoSyntheticField() throws Exception {
        var created = send("POST", "/api/collections/merge-demo",
                "{\"id\":\"d1\",\"name\":\"Keyboard\",\"price\":75.0,\"stock\":42}");
        assertTrue(created.code == 200 || created.code == 201,
                "seed write should succeed, got " + created.code + " " + created.body);

        var updated = send("PUT", "/api/collections/merge-demo/d1", "{\"stock\":41}");
        assertTrue(updated.code == 200 || updated.code == 201,
                "update should succeed, got " + updated.code + " " + updated.body);

        var fetched = get("/api/collections/merge-demo/d1");
        assertEquals(200, fetched.code, "document should still exist: " + fetched.body);
        assertTrue(fetched.body.contains("Keyboard"), "name must survive a partial update: " + fetched.body);
        assertTrue(fetched.body.contains("75.0"), "price must survive a partial update: " + fetched.body);
        assertTrue(fetched.body.contains("41"), "updated field must be applied: " + fetched.body);
        assertFalse(fetched.body.contains("temp"),
                "no synthetic name=temp field may be written: " + fetched.body);
    }

    @Test
    void updateByIdOnUnknownIdCreatesTheDocument() throws Exception {
        var created = send("PUT", "/api/collections/merge-demo/d9", "{\"name\":\"Hub\",\"price\":49.5}");
        assertTrue(created.code == 200 || created.code == 201,
                "creating by id should succeed, got " + created.code + " " + created.body);

        var fetched = get("/api/collections/merge-demo/d9");
        assertEquals(200, fetched.code);
        assertTrue(fetched.body.contains("Hub"), fetched.body);
        assertFalse(fetched.body.contains("temp"), fetched.body);
    }

    private Response get(String path) throws Exception {
        return exchange("GET", path, null);
    }

    private Response send(String method, String path, String body) throws Exception {
        return exchange(method, path, body);
    }

    private Response exchange(String method, String path, String body) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("http://localhost:" + port() + path).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = conn.getResponseCode();
            var stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            var sb = new StringBuilder();
            if (stream != null) {
                try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
            }
            return new Response(code, sb.toString());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private record Response(int code, String body) {
    }
}
