package org.junify.db;

import org.junify.db.config.ConsoleConfig;
import org.junify.db.config.JunifyDBConfig;
import org.junify.db.config.SecurityConfig;
import org.junify.db.core.cdc.CDCEvent;
import org.junify.db.core.cdc.CDCManager;
import org.junify.db.core.cdc.CDCProcessor;
import org.junify.db.nosql.document.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for console routing that either destroyed data or claimed
 * success while doing nothing.
 *
 * <ul>
 *   <li><b>Index drop</b> — {@code DELETE /api/indexes/{collection}} called
 *       {@code DocumentCollection.clear()}, so it deleted every document and answered
 *       {"status":"indexes cleared"}.</li>
 *   <li><b>CDC connectors</b> — a configured file connector was never subscribed to the
 *       event stream, so it was listed as connected and received nothing.</li>
 *   <li><b>CDC connector delete</b> — answered {"status":"disconnected"} for any name,
 *       including one that never existed.</li>
 *   <li><b>CDC connector create</b> — a body without {@code type} threw out of the handler
 *       and dropped the connection instead of returning 400.</li>
 * </ul>
 */
@DisplayName("Console index and CDC endpoints behave honestly")
class ConsoleIndexAndCdcEndpointTest {

    private static final String API_KEY = "index-cdc-endpoint-test-key";

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
    @DisplayName("DELETE /api/indexes/{collection}?field=x drops the index and keeps documents")
    void droppingAnIndexNeverDeletesDocuments() throws Exception {
        var products = db.documentCollection("products");
        products.insert(Document.of("name", "Keyboard").add("sku", "K1"));
        products.insert(Document.of("name", "Monitor").add("sku", "M1"));
        products.createIndex("sku");

        var resp = execute("DELETE", "/api/indexes/products?field=sku", null);

        assertEquals(200, resp.code, resp.body);
        assertTrue(resp.body.contains("index dropped"), resp.body);
        assertEquals(2, products.findAll().size(), "documents must survive an index drop");
        assertTrue(products.getIndexes().isEmpty(), "the sku index must be gone, found: " + products.getIndexes().keySet());
    }

    @Test
    @DisplayName("Index drop without a field explains itself instead of wiping the collection")
    void indexDropWithoutFieldIsRejected() throws Exception {
        var products = db.documentCollection("products");
        products.insert(Document.of("name", "Keyboard"));
        products.createIndex("name");

        var resp = execute("DELETE", "/api/indexes/products", null);

        assertEquals(400, resp.code, resp.body);
        assertTrue(resp.body.contains("field=<field>"), resp.body);
        assertEquals(1, products.findAll().size(), "no request may delete documents implicitly");
        assertEquals(1, products.getIndexes().size());
    }

    @Test
    @DisplayName("Dropping an index that does not exist reports 404")
    void droppingAMissingIndexIsNotFound() throws Exception {
        db.documentCollection("products").insert(Document.of("name", "Keyboard"));

        var resp = execute("DELETE", "/api/indexes/products?field=nope", null);

        assertEquals(404, resp.code, resp.body);
        assertTrue(resp.body.contains("No index on field"), resp.body);
    }

    @Test
    @DisplayName("A configured file connector receives real events and writes them to disk")
    void fileConnectorActuallyReceivesEvents() throws Exception {
        var manager = new CDCManager();
        var processor = manager.processor();

        assertEquals(0, processor.subscriberCount(), "no subscribers before a connector is added");

        var connectorDir = java.nio.file.Files.createTempDirectory("cdc-delivery");
        manager.addFileConnector("delivery", connectorDir);

        assertEquals(1, processor.subscriberCount(), "adding a connector must subscribe it");

        processor.onEvent(CDCEvent.insert("products", "p1", "{\"id\":\"p1\"}"));

        var file = connectorDir.resolve("delivery.jsonl");
        var content = awaitFileContaining(file, "p1");
        assertTrue(content.contains("\"p1\""), "the connector must have written the event, got: " + content);

        manager.removeFileConnector("delivery");
        assertEquals(0, processor.subscriberCount(), "removing a connector must unsubscribe it");
    }

    @Test
    @DisplayName("Deleting an unknown connector is a 404, not a fake disconnect")
    void deletingUnknownConnectorIs404() throws Exception {
        var resp = execute("DELETE", "/api/cdc/connectors/never-existed", null);

        assertEquals(404, resp.code, resp.body);
        assertTrue(resp.body.contains("No connector named"), resp.body);
    }

    @Test
    @DisplayName("Creating a connector without 'type' explains the contract instead of dropping the request")
    void connectorWithoutTypeIsRejected() throws Exception {
        var resp = execute("POST", "/api/cdc/connectors/broken", "{}");

        assertEquals(400, resp.code, "must not drop the connection: " + resp.body);
        assertTrue(resp.body.contains("'type' is required"), resp.body);
    }

    @Test
    @DisplayName("A file connector without outputDir is rejected clearly")
    void fileConnectorWithoutOutputDirIsRejected() throws Exception {
        var resp = execute("POST", "/api/cdc/connectors/nodir", "{\"type\":\"file\"}");

        assertEquals(400, resp.code, resp.body);
        assertTrue(resp.body.contains("outputDir"), resp.body);
    }

    @Test
    @DisplayName("A connector created over HTTP is listed and then removable")
    void connectorLifecycleOverHttp() throws Exception {
        var dir = java.nio.file.Files.createTempDirectory("cdc-http");

        var created = execute("POST", "/api/cdc/connectors/httpfile",
                "{\"type\":\"file\",\"outputDir\":\"" + dir.toString().replace("\\", "/") + "\"}");
        assertEquals(201, created.code, created.body);

        var status = execute("GET", "/api/cdc", null);
        assertTrue(status.body.contains("httpfile"), "the connector must appear in the status: " + status.body);
        // The connector is a real subscriber to the event stream, not just a listed name.
        assertTrue(status.body.contains("\"subscribers\":1"), "a wired connector is a real subscriber: " + status.body);

        var removed = execute("DELETE", "/api/cdc/connectors/httpfile", null);
        assertEquals(200, removed.code, removed.body);

        var after = execute("GET", "/api/cdc", null);
        assertTrue(after.body.contains("\"subscribers\":0"), after.body);
    }

    /** Waits for the connector's writer thread to flush the event to disk. */
    private String awaitFileContaining(Path file, String needle) throws Exception {
        for (int i = 0; i < 50; i++) {
            if (Files.exists(file)) {
                var content = Files.readString(file);
                if (content.contains(needle)) {
                    return content;
                }
            }
            Thread.sleep(100);
        }
        return Files.exists(file) ? Files.readString(file) : "<connector wrote no file>";
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
