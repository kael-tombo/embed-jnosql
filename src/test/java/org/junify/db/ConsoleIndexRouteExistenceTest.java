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

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for R-64: the index routes resolved the collection through
 * {@code JunifyDB.documentCollection(name)}, which auto-creates — so
 * {@code GET /api/indexes/typo} answered {@code 200 {"indexes":{}}} and left {@code typo}
 * in the catalog permanently. The console's Indexes panel takes a typed collection name,
 * so a single mistyped letter added junk collections to an operator's database; once a
 * created collection's existence became durable (R-62) that junk also survived restarts.
 *
 * <p>Adding an index (POST) is a write and keeps the documented schemaless auto-create.</p>
 */
@DisplayName("Console index routes do not create collections they are only asked about")
class ConsoleIndexRouteExistenceTest {

    private static final String API_KEY = "index-route-existence-test-key";

    private JunifyDB db;
    private String baseUrl;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
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
    @DisplayName("GET /api/indexes/{unknown} is a 404 and creates nothing")
    void listingIndexesOnAnUnknownCollectionCreatesNothing() throws Exception {
        var before = db.getCollectionNames();

        var resp = execute("GET", "/api/indexes/typo_collection", null);

        assertEquals(404, resp.code, resp.body);
        assertTrue(resp.body.contains("Collection not found"), resp.body);
        assertEquals(before, db.getCollectionNames(),
                "a read must not add the collection it was asked about");
    }

    @Test
    @DisplayName("DELETE /api/indexes/{unknown} is a 404 and creates nothing")
    void droppingAnIndexOnAnUnknownCollectionCreatesNothing() throws Exception {
        var before = db.getCollectionNames();

        var resp = execute("DELETE", "/api/indexes/also_typo?field=x", null);

        assertEquals(404, resp.code, resp.body);
        assertEquals(before, db.getCollectionNames(),
                "a delete on a missing collection must not bring it into existence");
    }

    @Test
    @DisplayName("the index routes still work on a collection that exists")
    void indexRoutesStillWorkOnAnExistingCollection() throws Exception {
        db.documentCollection("products").insert(Document.of("name", "Keyboard").add("sku", "K1"));

        var create = execute("POST", "/api/indexes/products", "{\"field\":\"sku\"}");
        assertEquals(201, create.code, create.body);

        var list = execute("GET", "/api/indexes/products", null);
        assertEquals(200, list.code, list.body);
        assertTrue(list.body.contains("sku"), list.body);

        var drop = execute("DELETE", "/api/indexes/products?field=sku", null);
        assertEquals(200, drop.code, drop.body);
        assertEquals(1, db.documentCollection("products").count(),
                "dropping an index must never touch documents");
    }

    @Test
    @DisplayName("adding an index is a write and still auto-creates, as documented")
    void addingAnIndexKeepsTheSchemalessAutoCreate() throws Exception {
        var resp = execute("POST", "/api/indexes/schemaless_col", "{\"field\":\"name\"}");

        assertEquals(201, resp.code, resp.body);
        assertTrue(db.getCollectionNames().contains("schemaless_col"),
                "a write may create the collection it writes to, per the schemaless workflow");
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private record Resp(int code, String body) { }

    private Resp execute(String method, String path, String body) throws Exception {
        var connection = (HttpURLConnection) URI.create(baseUrl + path).toURL().openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Authorization", "Bearer " + API_KEY);
        connection.setRequestProperty("Content-Type", "application/json");
        if (body != null) {
            connection.setDoOutput(true);
            try (var out = connection.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = connection.getResponseCode();
        var stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String responseBody = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        connection.disconnect();
        return new Resp(code, responseBody);
    }
}
