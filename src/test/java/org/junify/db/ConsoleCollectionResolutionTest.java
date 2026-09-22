package org.junify.db;

import org.junify.db.config.ConsoleConfig;
import org.junify.db.config.JunifyDBConfig;
import org.junify.db.config.SecurityConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HTTP-level regression coverage for <b>R-55</b>: the collections handler resolved
 * its target through the auto-creating {@code JunifyDB.documentCollection(name)}
 * <em>before</em> dispatching on the method, so every request touching a typo'd
 * collection created it:
 *
 * <ul>
 *   <li>{@code GET /api/collections/typo} answered {@code 200 []} — a read that
 *       wrote to the catalog and left the typo visible in the console forever;</li>
 *   <li>{@code DELETE /api/collections/typo} answered {@code 405} while creating
 *       the resource it had been asked to remove;</li>
 *   <li>the typed sub-resources ({@code stats}, {@code set-ttl}, {@code cleanup},
 *       {@code query}) did the same.</li>
 * </ul>
 *
 * Only document-write requests (POST/PUT) may auto-create — the documented
 * schemaless workflow. Everything else on an unknown collection is now 404.
 */
@DisplayName("Collection resolution never creates (R-55)")
class ConsoleCollectionResolutionTest {

    private static final String API_KEY = "coll-resolution-key";

    private JunifyDB db;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        db = JunifyDB.embed()
                .storageEngine(JunifyDBConfig.StorageEngineType.IN_MEMORY)
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
        if (db != null && db.isOpen()) db.close();
    }

    @Test
    @DisplayName("R-55: GET on an unknown collection is 404 and creates nothing")
    void getUnknownCollectionIs404AndCreatesNothing() throws Exception {
        Response r = send("GET", "/api/collections/ghost_coll", null);
        assertEquals(404, r.code, "a read must not resolve a collection into existence: " + r.body);
        assertTrue(r.body.contains("Collection not found"), r.body);

        assertFalse(catalogContains("ghost_coll"),
                "the typo'd collection must not appear in the catalog after a failed read");

        // And the second read is still 404 — nothing materialized on the first attempt.
        assertEquals(404, send("GET", "/api/collections/ghost_coll", null).code);
    }

    @Test
    @DisplayName("R-55: DELETE on an unknown collection is 404 and does not create it")
    void deleteUnknownCollectionIs404AndCreatesNothing() throws Exception {
        Response r = send("DELETE", "/api/collections/ghost_delete", null);
        assertEquals(404, r.code,
                "deleting a non-existent resource must not create it (was 405 + created): " + r.body);
        assertFalse(catalogContains("ghost_delete"),
                "the failed DELETE must not leave the collection behind");
    }

    @Test
    @DisplayName("R-55: typed sub-resources on an unknown collection are 404, not creators")
    void subResourcesOnUnknownCollectionAre404() throws Exception {
        assertEquals(404, send("GET", "/api/collections/ghost_sub/stats", null).code);
        assertEquals(404, send("POST", "/api/collections/ghost_sub/cleanup", null).code);
        assertEquals(404, send("POST", "/api/collections/ghost_sub/query", "{}").code);
        assertEquals(404, send("POST", "/api/collections/ghost_sub/set-ttl",
                "{\"documentId\":\"d1\",\"ttlSeconds\":60}").code);

        assertFalse(catalogContains("ghost_sub"),
                "no sub-resource request may create the collection it addresses");
    }

    @Test
    @DisplayName("R-55: POST still auto-creates — the documented schemaless workflow is intact")
    void postStillCreatesCollection() throws Exception {
        Response created = send("POST", "/api/collections/fresh_coll",
                "{\"name\":\"widget\",\"qty\":3}");
        assertEquals(201, created.code, created.body);

        assertTrue(catalogContains("fresh_coll"),
                "an INSERT is still allowed to create its collection (R-48/ADR-004 contract)");

        Response list = send("GET", "/api/collections/fresh_coll", null);
        assertEquals(200, list.code, list.body);
        assertTrue(list.body.contains("widget"), list.body);
    }

    @Test
    @DisplayName("R-55: deleting a whole collection is 405, says why, and keeps the documents")
    void deleteExistingCollectionIs405AndKeepsDocuments() throws Exception {
        assertEquals(201, send("POST", "/api/collections/keep_me",
                "{\"name\":\"precious\"}").code);

        Response deleted = send("DELETE", "/api/collections/keep_me", null);
        assertEquals(405, deleted.code, deleted.body);
        assertTrue(deleted.body.contains("not supported"),
                "the 405 must name the limitation: " + deleted.body);

        Response list = send("GET", "/api/collections/keep_me", null);
        assertEquals(200, list.code);
        assertTrue(list.body.contains("precious"),
                "a rejected collection delete must not touch the documents: " + list.body);
    }

    @Test
    @DisplayName("R-55: reads of an existing collection still work (guard is not over-broad)")
    void existingCollectionReadStillWorks() throws Exception {
        assertEquals(201, send("POST", "/api/collections/real_coll", "{\"id\":\"d1\",\"v\":1}").code);
        Response got = send("GET", "/api/collections/real_coll/d1", null);
        assertEquals(200, got.code, got.body);
        assertTrue(got.body.contains("\"d1\""), got.body);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private boolean catalogContains(String collectionName) {
        Response r = send("GET", "/api/collections", null);
        assertEquals(200, r.code, r.body);
        return r.body.contains("\"" + collectionName + "\"");
    }

    private Response send(String method, String path, String body) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl + path).toURL().openConnection();
            conn.setRequestMethod(method);
            conn.setRequestProperty("X-API-Key", API_KEY);
            if (body != null) {
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            var is = code < 400 ? conn.getInputStream() : conn.getErrorStream();
            String text = new String(is != null ? is.readAllBytes() : new byte[0], StandardCharsets.UTF_8);
            return new Response(code, text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private record Response(int code, String body) {}
}
