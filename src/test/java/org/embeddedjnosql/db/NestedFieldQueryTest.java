package org.embeddedjnosql.db;

import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.Query;
import org.embeddedjnosql.db.nosql.document.QueryParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Nested-field (dotted-path) query support. Filters like
 * {@code {"address.city": {"$eq": "Nairobi"}}} used to silently match nothing
 * because field resolution was strictly top-level ({@code has/getRaw}) — the
 * workspace gap surfaced by the NoSQL workspace audit. These tests lock in the
 * fix: {@link Document#resolvePath} walks nested maps and array indices, and
 * every filter operator plus sort and {@code $exists} honor dotted paths.
 */
@DisplayName("Nested (dotted-path) document queries match nested values")
class NestedFieldQueryTest {

    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.inMemory();
    }

    @AfterEach
    void tearDown() {
        if (db != null) db.close();
    }

    /** Three docs: two with nested address (Ada/Nairobi -1.2, Bob/Mombasa -4.0), one without (Cara). */
    private void seed() {
        var col = db.documentCollection("nested_q");
        col.insert(Document.of("name", "Ada")
                .add("address", Map.of("city", "Nairobi", "geo", Map.of("lat", -1.2)))
                .add("tags", List.of("a", "b")));
        col.insert(Document.of("name", "Bob")
                .add("address", Map.of("city", "Mombasa", "geo", Map.of("lat", -4.0))));
        col.insert(Document.of("name", "Cara"));
    }

    private String nameOf(Document d) {
        return (String) d.get("name");
    }

    @Test
    @DisplayName("$eq on a nested map path matches the stored value")
    void eqOnNestedPath() {
        var col = db.documentCollection("nested_q");
        seed();
        var hits = col.find(QueryParser.parse(Map.of("address.city", Map.of("$eq", "Nairobi"))));
        assertEquals(1, hits.size());
        assertEquals("Ada", nameOf(hits.get(0)));

        // simple-equality form (field: value, no operator map)
        var hits2 = col.find(QueryParser.parse(Map.of("address.city", "Mombasa")));
        assertEquals(1, hits2.size());
        assertEquals("Bob", nameOf(hits2.get(0)));
    }

    @Test
    @DisplayName("numeric comparison and $regex traverse nested paths")
    void numericAndRegexOnNestedPath() {
        var col = db.documentCollection("nested_q");
        seed();
        assertEquals(1, col.find(QueryParser.parse(
                Map.of("address.geo.lat", Map.of("$lt", -3.0)))).size(),
                "address.geo.lat < -3 must match only Mombasa (-4.0)");
        assertEquals(1, col.find(QueryParser.parse(
                Map.of("address.city", Map.of("$regex", "^Nair")))).size());
    }

    @Test
    @DisplayName("$exists distinguishes nested presence from absence, and null stays null")
    void existsAndNullOnNestedPath() {
        var col = db.documentCollection("nested_q");
        seed();
        assertEquals(2, col.find(QueryParser.parse(
                Map.of("address.city", Map.of("$exists", true)))).size(),
                "Ada and Bob have address.city; Cara must not match");
        assertEquals(1, col.find(QueryParser.parse(
                Map.of("address.city", Map.of("$exists", false)))).size(),
                "only Cara lacks address.city");
    }

    @Test
    @DisplayName("resolvePath distinguishes a stored null leaf from an absent path")
    void resolvePathNullVersusMissing() {
        var d = Document.of("name", "x")
                .add("address", Collections.singletonMap("city", null));
        assertNull(d.resolvePath("address.city"),
                "a stored null leaf resolves to null, not missing");
        assertTrue(d.hasPath("address.city"));
        assertSame(Document.PATH_MISSING, d.resolvePath("address.zip"));
        assertFalse(d.hasPath("address.zip"));
        // The engine's JSON round-trip drops null leaves, so through storage a
        // null city becomes absent — $eq null must then behave like the old
        // missing-field rule (match only when querying for null).
        var col = db.documentCollection("nested_null");
        col.insert(d);
        assertEquals(1, col.find(QueryParser.parse(
                Collections.singletonMap("address.city",
                        Collections.singletonMap("$eq", null)))).size());
    }

    @Test
    @DisplayName("numeric segments index into arrays")
    void arrayIndexSegment() {
        var col = db.documentCollection("nested_q");
        seed();
        assertEquals(1, col.find(QueryParser.parse(
                Map.of("tags.0", Map.of("$eq", "a")))).size());
        assertEquals(0, col.find(QueryParser.parse(
                Map.of("tags.5", Map.of("$exists", true)))).size(),
                "out-of-range index must be missing, not throw");
    }

    @Test
    @DisplayName("fluent Query factories honor dotted paths and skip index hints for them")
    void fluentFactoriesWithNestedPaths() {
        var col = db.documentCollection("nested_q");
        seed();
        assertEquals(1, col.find(Query.eq("address.city", "Nairobi")).size());
        assertEquals(1, col.find(Query.lt("address.geo.lat", -3.0)).size());
        assertEquals(2, col.find(Query.exists("address.geo.lat")).size(),
                "Ada and Bob both carry address.geo.lat");
        // Indexable hints must not be set for dotted fields (they are not
        // secondary-indexable); the query must still return correct results.
        assertNull(Query.eq("address.city", "x").getIndexedField());
        assertEquals("name", Query.eq("name", "x").getIndexedField(),
                "plain fields keep their index hint");
    }

    @Test
    @DisplayName("sorting by a dotted path orders on nested values")
    void sortOnNestedPath() {
        var col = db.documentCollection("nested_q");
        seed();
        var sorted = col.find(Query.all().sortByAsc("address.geo.lat"));
        assertEquals(3, sorted.size());
        assertEquals("Cara", nameOf(sorted.get(0)),
                "docs without the path sort first (null)");
        assertEquals("Bob", nameOf(sorted.get(1)), "-4.0 sorts before -1.2");
        assertEquals("Ada", nameOf(sorted.get(2)));
    }
}
