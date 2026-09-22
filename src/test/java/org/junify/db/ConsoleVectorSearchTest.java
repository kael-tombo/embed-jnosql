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
 * HTTP-level regression coverage for <b>R-50</b>: the vector endpoint's search
 * path used to (a) silently create an empty index for a typo'd name and answer
 * {@code 200 {"results":[]}}, (b) answer a missing {@code vector} field with an
 * NPE 500, (c) answer {@code k=0} with a fake empty success and {@code k<0}
 * with a bare "{@code -5}" 500. Search now 404s on unknown indexes (only add
 * creates on first use) and 400s on malformed input.
 */
@DisplayName("Vector search refuses unknown indexes and malformed input (R-50)")
class ConsoleVectorSearchTest {

    private static final String API_KEY = "vector-test-key";

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
    @DisplayName("R-50: search on an unknown index is 404 and creates nothing")
    void searchUnknownIndexIs404() throws Exception {
        Response r = send("POST", "/api/vectors/typo_idx/search", "{\"vector\":[1,2,3],\"k\":3}");
        assertEquals(404, r.code, "a search must not create the index it names: " + r.body);
        assertTrue(r.body.contains("Vector index not found"), r.body);

        // And the typo'd index must not now exist: an info GET still 404s.
        Response info = send("GET", "/api/vectors/typo_idx/_", null);
        assertEquals(404, info.code, "failed search must not leave an empty index behind");
    }

    @Test
    @DisplayName("R-50: missing vector field is 400, not an NPE 500")
    void missingVectorIs400() throws Exception {
        Response created = send("POST", "/api/vectors/vec_r50/v1", "{\"vector\":[1,2,3]}");
        assertEquals(201, created.code, created.body);

        Response r = send("POST", "/api/vectors/vec_r50/search", "{\"k\":3}");
        assertEquals(400, r.code, r.body);
    }

    @Test
    @DisplayName("R-50: k=0 and negative k are 400, not fake-empty or bare-number 500")
    void badKIs400() throws Exception {
        send("POST", "/api/vectors/vec_r50k/v1", "{\"vector\":[1,2,3]}");

        Response zero = send("POST", "/api/vectors/vec_r50k/search", "{\"vector\":[1,2,3],\"k\":0}");
        assertEquals(400, zero.code, zero.body);

        Response negative = send("POST", "/api/vectors/vec_r50k/search", "{\"vector\":[1,2,3],\"k\":-5}");
        assertEquals(400, negative.code, negative.body);
    }

    @Test
    @DisplayName("R-50: dimension mismatch is 400 (was a 500), and a real search still works")
    void dimensionMismatchIs400AndRealSearchWorks() throws Exception {
        send("POST", "/api/vectors/vec_r50d/v1", "{\"vector\":[1,2,3]}");

        Response wrong = send("POST", "/api/vectors/vec_r50d/search", "{\"vector\":[1,2],\"k\":3}");
        assertEquals(400, wrong.code, wrong.body);

        Response right = send("POST", "/api/vectors/vec_r50d/search", "{\"vector\":[1,2,3],\"k\":3}");
        assertEquals(200, right.code, right.body);
        assertTrue(right.body.contains("results"), right.body);
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
