package org.junify.db;

import org.junify.db.config.ConsoleConfig;
import org.junify.db.config.JunifyDBConfig;
import org.junify.db.config.SecurityConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for two query-path defects found by probing the live
 * console, both of which silently returned wrong results instead of failing:
 *
 * <ul>
 *   <li><b>R-45</b> — {@code $regex} used {@link String#matches}, which anchors
 *       the whole string, so the substring pattern {@code {"$regex":"Key"}} that
 *       the Javadoc example style implies never matched "Keyboard". Mixed with
 *       other operators it also made every {@code $and}-joined query empty.</li>
 *   <li><b>R-46</b> — top-level {@code {"$and":[...]}} / {@code {"$or":[...]}}
 *       were unreachable: {@code parseField} saw a List (not a Map), treated it
 *       as simple equality on a field literally named "$and", and every document
 *       failed. Confirmed live: {@code {"$and":[{"name":{"$eq":"Keyboard"}}]}}
 *       returned 0 results with a matching document present.</li>
 *   <li><b>R-47</b> — unknown operators were silently ignored
 *       ({@code default -> doc -> true}), so a typo like {@code {"$gteX":5}}
 *       returned every document as if no filter had been sent. Now 400.</li>
 * </ul>
 */
@DisplayName("NoSQL query operators match, combine, and refuse what they cannot parse")
class ConsoleQueryEndpointTest {

    private static final String API_KEY = "query-test-key";

    private JunifyDB db;
    private String baseUrl;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
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

        post("/api/collections/qa", "{\"id\":\"a1\",\"name\":\"Keyboard\",\"stock\":41,\"tags\":[\"input\",\"wireless\"]}");
        post("/api/collections/qa", "{\"id\":\"a2\",\"name\":\"Mouse Pad\",\"stock\":7,\"tags\":[\"input\"]}");
        post("/api/collections/qa", "{\"id\":\"a3\",\"name\":\"Monitor\",\"stock\":12,\"tags\":[]}");
    }

    @AfterEach
    void tearDown() {
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    // ---- R-45: $regex must be substring, not whole-string anchored ----------

    @Test
    @DisplayName("R-45: $regex \"Key\" matches Keyboard (substring, not String.matches)")
    void regexIsSubstring() {
        List<Map<String, Object>> rows = query("{\"name\":{\"$regex\":\"Key\"}}");
        assertEquals(1, rows.size(), "substring pattern must match Keyboard");
        assertEquals("a1", rows.get(0).get("id"));
    }

    @Test
    @DisplayName("R-45: $regex \"^Key\" still anchors when the user asks for it")
    void regexRespectsExplicitAnchor() {
        assertEquals(1, query("{\"name\":{\"$regex\":\"^Key\"}}").size());
        assertEquals(0, query("{\"name\":{\"$regex\":\"^ouse\"}}").size(),
                "explicit anchor must not match 'Mouse Pad'");
    }

    @Test
    @DisplayName("R-45: $regex on numbers stringifies the value")
    void regexStringifiesNumbers() {
        List<Map<String, Object>> rows = query("{\"stock\":{\"$regex\":\"^1\"}}");
        assertEquals(1, rows.size(), "stocks 41, 7, 12 -> only 12 starts with 1");
    }

    // ---- R-46: top-level $and / $or must be reachable ------------------------

    @Test
    @DisplayName("R-46: top-level $and with a matching branch returns the document")
    void topLevelAnd() {
        List<Map<String, Object>> rows = query(
                "{\"$and\":[{\"name\":{\"$eq\":\"Keyboard\"}},{\"stock\":{\"$gt\":10}}]}");
        assertEquals(1, rows.size());
        assertEquals("a1", rows.get(0).get("id"));
    }

    @Test
    @DisplayName("R-46: top-level $and with a failing branch returns nothing")
    void topLevelAndFailingBranch() {
        assertEquals(0, query(
                "{\"$and\":[{\"name\":{\"$eq\":\"Keyboard\"}},{\"stock\":{\"$lt\":10}}]}").size());
    }

    @Test
    @DisplayName("R-46: top-level $or returns the union of branches")
    void topLevelOr() {
        List<Map<String, Object>> rows = query(
                "{\"$or\":[{\"name\":{\"$eq\":\"Monitor\"}},{\"stock\":{\"$lt\":10}}]}");
        assertEquals(2, rows.size(), "Monitor and Mouse Pad");
    }

    @Test
    @DisplayName("R-46: simple-equality branches inside top-level $or work")
    void topLevelOrSimpleEquality() {
        List<Map<String, Object>> rows = query(
                "{\"$or\":[{\"name\":\"Keyboard\"},{\"name\":\"Monitor\"}]}");
        assertEquals(2, rows.size(), "Keyboard and Monitor");
    }

    @Test
    @DisplayName("R-46: field-level multiple operators still combine")
    void fieldLevelOperators() {
        List<Map<String, Object>> rows = query(
                "{\"stock\":{\"$gt\":5,\"$lt\":20}}");
        assertEquals(2, rows.size(), "stocks 7 and 12 both satisfy 5 < stock < 20");
    }

    // ---- R-47: unknown operators are refused, not ignored --------------------

    @Test
    @DisplayName("R-47: typo'd operator is a 400 with the offending name, not 'all documents'")
    void unknownOperatorIsRefused() {
        int status = post("/api/collections/qa/query", "{\"name\":{\"$gteX\":\"Keyboard\"}}");
        assertEquals(400, status, "unknown operator must fail loudly");
    }

    @Test
    @DisplayName("R-47: non-object operator value is a 400, not a ClassCastException 500")
    void malformedOperatorValueIs400() {
        int status = post("/api/collections/qa/query", "{\"name\":{\"$in\":\"notalist\"}}");
        assertEquals(400, status);
    }

    @Test
    @DisplayName("R-47: $regex with a syntactically invalid pattern is a 400")
    void invalidRegexPatternIs400() {
        int status = post("/api/collections/qa/query", "{\"name\":{\"$regex\":\"[unclosed\"}}");
        assertEquals(400, status);
    }

    // ---- helpers --------------------------------------------------------------

    private List<Map<String, Object>> lastJson;

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> query(String json) {
        int status = post("/api/collections/qa/query", json);
        assertEquals(200, status, "query " + json + " must succeed");
        return lastJson;
    }

    @SuppressWarnings("unchecked")
    private int post(String path, String body) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl + path).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-API-Key", API_KEY);
            conn.setDoOutput(true);
            conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            int status = conn.getResponseCode();
            try (var is = status < 400 ? conn.getInputStream() : conn.getErrorStream()) {
                String text = new String(is != null ? is.readAllBytes() : new byte[0], StandardCharsets.UTF_8);
                if (status < 400 && text.startsWith("[")) {
                    lastJson = (List<Map<String, Object>>) (List<?>) org.junify.db.core.util.JsonSerde.fromJson(text, List.class);
                }
            }
            return status;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
