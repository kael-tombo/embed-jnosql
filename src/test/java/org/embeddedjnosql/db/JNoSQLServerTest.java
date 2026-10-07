package org.embeddedjnosql.db;

import org.embeddedjnosql.db.nosql.document.Document;
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

import static org.junit.jupiter.api.Assertions.*;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class EmbedJNoSQLServerTest {

    private EmbedJNoSQL db;
    private EmbedJNoSQLServer server;

    @BeforeEach
    void setUp() throws Exception {
        db = EmbedJNoSQL.embed()
            .build();
        server = db.startServer(0);
        server.setApiKey(null);  // Disable auth for tests
        // Give server time to start
        Thread.sleep(100);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                // Ignore cleanup errors
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
    void healthEndpoint() throws Exception {
        var response = get("/api/health");
        assertEquals(200, response.code, "Health check should return 200");
        assertTrue(response.body.contains("ok"), "Health check should contain 'ok'");
    }

    @Test
    void insertDocument() throws Exception {
        var response = post("/api/collections/users", "{\"fields\":{\"name\":\"Alice\"},\"id\":null}");
        assertEquals(201, response.code, "Insert should return 201");
        assertTrue(response.body.contains("name"), "Response should contain 'name' field");
    }

    @Test
    void findAllDocuments() throws Exception {
        db.documentCollection("users").insert(Document.of("name", "Alice"));
        db.documentCollection("users").insert(Document.of("name", "Bob"));

        var response = get("/api/collections/users");
        assertEquals(200, response.code, "Get all should return 200");
        assertTrue(response.body.contains("Alice"), "Response should contain 'Alice'");
        assertTrue(response.body.contains("Bob"), "Response should contain 'Bob'");
    }

    @Test
    void findDocumentById() throws Exception {
        var doc = db.documentCollection("users").insert(Document.of("name", "Alice"));

        var response = get("/api/collections/users/" + doc.id());
        assertEquals(200, response.code, "Get by ID should return 200");
        assertTrue(response.body.contains("Alice"), "Response should contain 'Alice'");
    }

    @Test
    void deleteDocumentById() throws Exception {
        var doc = db.documentCollection("users").insert(Document.of("name", "Alice"));

        var response = delete("/api/collections/users/" + doc.id());
        assertEquals(204, response.code, "Delete should return 204");

        assertNull(db.documentCollection("users").findById(doc.id()), "Document should be deleted");
    }

    @Test
    void kvPutAndGet() throws Exception {
        var put = put("/api/kv/cache/session1", "{\"value\":\"user-data\"}");
        assertEquals(201, put.code, "KV put should return 201");
        assertTrue(put.body.contains("user-data"), "Put response should contain data");

        var get = get("/api/kv/cache/session1");
        assertEquals(200, get.code, "KV get should return 200");
        assertTrue(get.body.contains("user-data"), "Get response should contain data");
    }

    @Test
    void kvDelete() throws Exception {
        db.keyValueBucket("cache").put("key1", "value1");

        var response = delete("/api/kv/cache/key1");
        assertEquals(204, response.code, "KV delete should return 204");

        assertNull(db.keyValueBucket("cache").get("key1"), "Key should be deleted");
    }

    @Test
    void listCollections() throws Exception {
        db.documentCollection("products").insert(Document.of("name", "Laptop"));
        db.documentCollection("orders").insert(Document.of("total", 100));

        var response = get("/api/collections");
        assertEquals(200, response.code, "List collections should return 200");
        assertTrue(response.body.contains("products"), "Response should contain products collection");
        assertTrue(response.body.contains("orders"), "Response should contain orders collection");
    }

    @Test
    void authLoginAndLogout() throws Exception {
        var loginResp = post("/api/auth/login", "{\"username\":\"admin\",\"password\":\"secret\"}");
        assertEquals(200, loginResp.code, "Login should return 200");
        assertTrue(loginResp.body.contains("authenticated"), "Should contain authenticated status");
        assertTrue(loginResp.body.contains("session"), "Should return session ID");

        var logoutResp = post("/api/auth/logout", "");
        assertEquals(200, logoutResp.code, "Logout should return 200");
        assertTrue(logoutResp.body.contains("logged_out"), "Should return logged_out status");
    }

    private record Response(int code, String body) {}

    private Response get(String path) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("http://localhost:" + port() + path).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            return readResponse(conn);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private Response post(String path, String body) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("http://localhost:" + port() + path).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            try (var os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            return readResponse(conn);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private Response put(String path, String body) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("http://localhost:" + port() + path).openConnection();
            conn.setRequestMethod("PUT");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            try (var os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            return readResponse(conn);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private Response delete(String path) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("http://localhost:" + port() + path).openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            return readResponse(conn);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private Response readResponse(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        var stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (stream == null) return new Response(code, "");
        try (var reader = new BufferedReader(new InputStreamReader(stream))) {
            var body = reader.lines().reduce("", (a, b) -> a + b);
            return new Response(code, body);
        }
    }
}
