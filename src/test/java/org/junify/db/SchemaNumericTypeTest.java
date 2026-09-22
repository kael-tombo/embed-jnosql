package org.junify.db;

import org.junify.db.console.http.JunifyDBServer;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for R-32: a field registered as {@code number} must accept any
 * JSON number.
 *
 * <p>The registration endpoint maps {@code number} to {@code Double}, and the validator
 * used strict {@code isInstance}, so an integral value such as {@code 99} was rejected
 * with "must be of type Double, got Integer" while {@code 99.0} passed.</p>
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class SchemaNumericTypeTest {

    private JunifyDB db;
    private JunifyDBServer server;

    @BeforeEach
    void setUp() throws Exception {
        db = JunifyDB.embed().build();
        server = db.startServer(0);
        server.setApiKey(null);
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
    void integralNumberSatisfiesNumberFieldButStringsStillFail() throws Exception {
        var schema = exchange("POST", "/api/schema/numeric-demo",
                "{\"strict\":false,\"fields\":[{\"name\":\"price\",\"type\":\"number\",\"required\":true}]}");
        assertTrue(schema.code == 200 || schema.code == 201,
                "schema registration should succeed, got " + schema.code + " " + schema.body);

        var integral = exchange("POST", "/api/collections/numeric-demo",
                "{\"id\":\"n1\",\"price\":75}");
        assertTrue(integral.code == 200 || integral.code == 201,
                "integral JSON number must satisfy a 'number' field, got " + integral.code + " " + integral.body);

        var decimal = exchange("POST", "/api/collections/numeric-demo",
                "{\"id\":\"n2\",\"price\":75.5}");
        assertTrue(decimal.code == 200 || decimal.code == 201,
                "decimal JSON number must satisfy a 'number' field, got " + decimal.code + " " + decimal.body);

        var notANumber = exchange("POST", "/api/collections/numeric-demo",
                "{\"id\":\"n3\",\"price\":\"expensive\"}");
        assertEquals(400, notANumber.code,
                "a string must still fail a numeric field: " + notANumber.body);

        var missingRequired = exchange("POST", "/api/collections/numeric-demo", "{\"id\":\"n4\"}");
        assertEquals(400, missingRequired.code,
                "required-field validation must still apply: " + missingRequired.body);
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
